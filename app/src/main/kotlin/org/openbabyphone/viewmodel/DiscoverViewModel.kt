package org.openbabyphone.viewmodel

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openbabyphone.ConnectionConstants
import org.openbabyphone.PairingCode
import org.openbabyphone.PairingQrCode
import org.openbabyphone.PendingConnection
import org.openbabyphone.PendingConnectionStore
import org.openbabyphone.PendingConnections
import org.openbabyphone.TrustedChild
import org.openbabyphone.TrustedChildStore
import org.openbabyphone.trustedChildStore
import java.util.concurrent.ConcurrentLinkedQueue

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val port: Int,
    val childId: String? = null,
    val pairingId: String? = null,
    val displayName: String? = null
) {
    val visibleName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: name

    val hasIdentity: Boolean
        get() = childId != null && pairingId != null
}

enum class KnownChildStatus {
    Available,
    NotFound,
    PairAgain
}

data class KnownChildRow(
    val child: TrustedChild,
    val status: KnownChildStatus,
    internal val device: DiscoveredDevice?
)

data class ListenRequest(
    val requestId: String,
    val childId: String,
    val pairingId: String
)

sealed interface PairingFlowState {
    data object Idle : PairingFlowState
    data object InvalidQr : PairingFlowState
    data class LookingForChild(
        val childName: String,
        internal val childId: String,
        internal val pairingId: String,
        internal val requestId: String
    ) : PairingFlowState
    data class Ready(
        val childName: String,
        val request: ListenRequest
    ) : PairingFlowState
    data class ChildNotFound(
        val childName: String,
        internal val childId: String,
        internal val pairingId: String,
        internal val requestId: String
    ) : PairingFlowState
}

sealed interface QrScanResult {
    data object Invalid : QrScanResult
    data class Structured(val childName: String) : QrScanResult
    data class Legacy(val pairingCode: String) : QrScanResult
}

sealed interface KnownConnectionResult {
    data class Ready(val request: ListenRequest) : KnownConnectionResult
    data object NotFound : KnownConnectionResult
    data object PairAgain : KnownConnectionResult
}

data class DiscoverUiState(
    val isDiscovering: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val error: String? = null,
    val trustedChildren: List<TrustedChild> = emptyList(),
    val knownChildren: List<KnownChildRow> = emptyList(),
    val pairingFlow: PairingFlowState = PairingFlowState.Idle
) {
    val fallbackDevices: List<DiscoveredDevice>
        get() = devices.filter { candidate ->
            knownChildren.none { it.status == KnownChildStatus.Available && it.device == candidate }
        }
}

class DiscoverViewModel @JvmOverloads constructor(
    application: Application,
    private val trustedChildStore: TrustedChildStore = application.trustedChildStore(),
    private val pendingConnections: PendingConnectionStore = PendingConnections.store,
    private val pairingTimeoutMillis: Long = PAIRING_TIMEOUT_MS,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val discoveredDevices = mutableListOf<DiscoveredDevice>()
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile private var isResolving = false
    private var discoveryGeneration = 0
    private var pairingTimeoutJob: Job? = null
    private var scannedRequestId: String? = null

    companion object {
        private const val TAG = "DiscoverViewModel"
        const val PAIRING_TIMEOUT_MS = 12_000L
    }

    init {
        refreshTrustedChildren()
    }

    fun activate() {
        refreshTrustedChildren()
        val flow = _uiState.value.pairingFlow
        if (flow is PairingFlowState.Ready && !pendingConnections.contains(flow.request.requestId)) {
            _uiState.value = _uiState.value.copy(pairingFlow = PairingFlowState.Idle)
        }
        startDiscovery()
    }

    fun refreshTrustedChildren() {
        _uiState.value = _uiState.value.copy(trustedChildren = trustedChildStore.getAll())
        recomputeKnownChildren()
    }

    fun handleQrScan(content: String?): QrScanResult {
        val parsed = PairingQrCode.parse(content)
        if (parsed == null) {
            cancelPairingSearch(PairingFlowState.InvalidQr)
            return QrScanResult.Invalid
        }
        return when (parsed) {
            is PairingQrCode.ParsedQrCode.Legacy -> {
                cancelPairingSearch(PairingFlowState.Idle)
                if (!PairingCode.isValid(parsed.pairingCode)) {
                    _uiState.value = _uiState.value.copy(pairingFlow = PairingFlowState.InvalidQr)
                    QrScanResult.Invalid
                } else {
                    QrScanResult.Legacy(PairingCode.normalize(parsed.pairingCode))
                }
            }
            is PairingQrCode.ParsedQrCode.Structured -> {
                if (!PairingCode.isValid(parsed.pairingCode)) {
                    cancelPairingSearch(PairingFlowState.InvalidQr)
                    return QrScanResult.Invalid
                }
                cancelPairingSearch(PairingFlowState.Idle)
                val requestId = pendingConnections.put(
                    PendingConnection(
                        name = parsed.name,
                        pairingCode = PairingCode.normalize(parsed.pairingCode).toCharArray(),
                        expectedChildId = parsed.childId,
                        expectedPairingId = parsed.pairingId,
                        rememberAfterAuthentication = true
                    )
                )
                scannedRequestId = requestId
                _uiState.value = _uiState.value.copy(
                    pairingFlow = PairingFlowState.LookingForChild(
                        childName = parsed.name.ifBlank {
                            getApplication<Application>().getString(org.openbabyphone.R.string.default_child_name)
                        },
                        childId = parsed.childId,
                        pairingId = parsed.pairingId,
                        requestId = requestId
                    )
                )
                matchScannedIdentity()
                if (_uiState.value.pairingFlow is PairingFlowState.LookingForChild) {
                    startPairingTimeout(requestId)
                }
                QrScanResult.Structured(
                    parsed.name.ifBlank {
                        getApplication<Application>().getString(org.openbabyphone.R.string.default_child_name)
                    }
                )
            }
        }
    }

    fun retryPairingSearch() {
        val notFound = _uiState.value.pairingFlow as? PairingFlowState.ChildNotFound ?: return
        if (!pendingConnections.contains(notFound.requestId)) {
            cancelPairingSearch(PairingFlowState.InvalidQr)
            return
        }
        _uiState.value = _uiState.value.copy(
            pairingFlow = PairingFlowState.LookingForChild(
                notFound.childName,
                notFound.childId,
                notFound.pairingId,
                notFound.requestId
            )
        )
        matchScannedIdentity()
        if (_uiState.value.pairingFlow is PairingFlowState.LookingForChild) {
            startPairingTimeout(notFound.requestId)
        }
        if (!_uiState.value.isDiscovering) startDiscovery()
    }

    fun cancelPairingSearch() {
        cancelPairingSearch(PairingFlowState.Idle)
    }

    fun prepareKnownConnection(childId: String): KnownConnectionResult {
        val row = _uiState.value.knownChildren.firstOrNull { it.child.childId == childId }
            ?: return KnownConnectionResult.NotFound
        if (row.status == KnownChildStatus.PairAgain) return KnownConnectionResult.PairAgain
        val device = row.device ?: return KnownConnectionResult.NotFound
        val child = trustedChildStore.findById(childId)
            ?.takeIf { it.pairingId == device.pairingId }
            ?: return KnownConnectionResult.PairAgain
        val requestId = pendingConnections.put(
            PendingConnection(
                address = device.address,
                port = device.port,
                name = device.visibleName.ifBlank { child.displayName },
                pairingCode = null,
                expectedChildId = child.childId,
                expectedPairingId = child.pairingId
            )
        )
        return KnownConnectionResult.Ready(ListenRequest(requestId, child.childId, child.pairingId))
    }

    fun prepareCodePairing(device: DiscoveredDevice, pairingCode: String): Boolean {
        if (!PairingCode.isValid(pairingCode)) return false
        val requestId = pendingConnections.put(
            PendingConnection(
                address = device.address,
                port = device.port,
                name = device.visibleName,
                pairingCode = PairingCode.normalize(pairingCode).toCharArray(),
                expectedChildId = device.childId.takeIf { device.hasIdentity },
                expectedPairingId = device.pairingId.takeIf { device.hasIdentity },
                rememberAfterAuthentication = device.hasIdentity
            )
        )
        cancelPairingSearch(PairingFlowState.Idle)
        scannedRequestId = requestId
        _uiState.value = _uiState.value.copy(
            pairingFlow = PairingFlowState.Ready(
                device.visibleName,
                ListenRequest(requestId, device.childId.orEmpty(), device.pairingId.orEmpty())
            )
        )
        return true
    }

    /** Adds a resolved NSD record and applies identity-based deduplication and pairing matching. */
    fun recordResolvedDevice(device: DiscoveredDevice) {
        if (device.address.isBlank() || device.port !in 1..65535) return
        val duplicate = if (!device.childId.isNullOrBlank()) {
            discoveredDevices.indexOfFirst { it.childId == device.childId }
        } else {
            discoveredDevices.indexOfFirst { it.address == device.address && it.port == device.port }
        }
        if (duplicate >= 0) {
            discoveredDevices[duplicate] = device
            if (!device.childId.isNullOrBlank()) {
                discoveredDevices.removeAllIndexedAfter(duplicate) {
                    it.childId == device.childId
                }
            }
        } else {
            discoveredDevices.add(device)
        }
        publishDevices()
        matchScannedIdentity()
    }

    fun startDiscovery() {
        if (_uiState.value.isDiscovering) return
        val context = getApplication<Application>()
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("openBabyphoneDiscovery").apply {
            setReferenceCounted(false)
            acquire()
        }

        val generation = ++discoveryGeneration
        discoveredDevices.clear()
        resolveQueue.clear()
        isResolving = false
        _uiState.value = _uiState.value.copy(isDiscovering = true, devices = emptyList(), error = null)
        recomputeKnownChildren()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                if (generation == discoveryGeneration &&
                    service.serviceType == ConnectionConstants.SERVICE_TYPE &&
                    service.serviceName.startsWith(ConnectionConstants.SERVICE_NAME_PREFIX)
                ) {
                    resolveService(service, generation)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                if (generation != discoveryGeneration) return
                discoveredDevices.removeAll { it.name == service.serviceName }
                publishDevices()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                releaseMulticastLock()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (generation != discoveryGeneration) return
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    error = "Discovery failed: $errorCode"
                )
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                releaseMulticastLock()
            }
        }

        try {
            nsdManager?.discoverServices(
                ConnectionConstants.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (exception: RuntimeException) {
            Log.d(TAG, "Failed to start discovery", exception)
            _uiState.value = _uiState.value.copy(isDiscovering = false, error = "Discovery failed")
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        discoveryGeneration++
        resolveQueue.clear()
        isResolving = false
        val listener = discoveryListener
        discoveryListener = null
        if (listener != null) {
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (exception: RuntimeException) {
                Log.d(TAG, "Failed to stop discovery", exception)
            }
        }
        _uiState.value = _uiState.value.copy(isDiscovering = false)
        releaseMulticastLock()
    }

    private fun resolveService(service: NsdServiceInfo, generation: Int) {
        resolveQueue.add(service)
        processResolveQueue(generation)
    }

    private fun processResolveQueue(generation: Int) {
        if (isResolving || generation != discoveryGeneration) return
        val service = resolveQueue.poll() ?: return
        isResolving = true
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                finishResolve(generation)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (generation == discoveryGeneration) {
                    val hostAddress = serviceInfo.host?.hostAddress
                    if (hostAddress != null) {
                        val txtMap = serviceInfo.attributes
                        recordResolvedDevice(
                            DiscoveredDevice(
                                name = serviceInfo.serviceName
                                    .replace("\\\\032", " ")
                                    .replace("\\032", " "),
                                address = hostAddress,
                                port = serviceInfo.port,
                                childId = txtMap?.get(ConnectionConstants.NSD_TXT_CHILD_ID)
                                    ?.let { String(it, Charsets.UTF_8) },
                                pairingId = txtMap?.get(ConnectionConstants.NSD_TXT_PAIRING_ID)
                                    ?.let { String(it, Charsets.UTF_8) },
                                displayName = txtMap?.get(ConnectionConstants.NSD_TXT_NAME)
                                    ?.let { String(it, Charsets.UTF_8) }
                            )
                        )
                    }
                }
                finishResolve(generation)
            }
        }
        nsdManager?.resolveService(service, resolver)
    }

    private fun finishResolve(generation: Int) {
        if (generation != discoveryGeneration) return
        isResolving = false
        processResolveQueue(generation)
    }

    private fun publishDevices() {
        _uiState.value = _uiState.value.copy(devices = discoveredDevices.toList())
        recomputeKnownChildren()
    }

    private fun recomputeKnownChildren() {
        val state = _uiState.value
        _uiState.value = _uiState.value.copy(
            knownChildren = knownChildRows(state.trustedChildren, state.devices)
        )
    }

    private fun matchScannedIdentity() {
        val looking = _uiState.value.pairingFlow as? PairingFlowState.LookingForChild ?: return
        val device = discoveredDevices.firstOrNull {
            it.childId == looking.childId && it.pairingId == looking.pairingId
        } ?: return
        val completed = pendingConnections.complete(
            requestId = looking.requestId,
            address = device.address,
            port = device.port,
            name = device.visibleName,
            childId = device.childId,
            pairingId = device.pairingId
        ) ?: return
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        _uiState.value = _uiState.value.copy(
            pairingFlow = PairingFlowState.Ready(
                childName = looking.childName,
                request = ListenRequest(completed, looking.childId, looking.pairingId)
            )
        )
    }

    private fun startPairingTimeout(requestId: String) {
        pairingTimeoutJob?.cancel()
        val deadline = elapsedRealtime() + pairingTimeoutMillis
        pairingTimeoutJob = viewModelScope.launch {
            var remaining = deadline - elapsedRealtime()
            while (remaining > 0) {
                delay(remaining)
                remaining = deadline - elapsedRealtime()
            }
            val looking = _uiState.value.pairingFlow as? PairingFlowState.LookingForChild
            if (looking?.requestId == requestId) {
                _uiState.value = _uiState.value.copy(
                    pairingFlow = PairingFlowState.ChildNotFound(
                        looking.childName,
                        looking.childId,
                        looking.pairingId,
                        looking.requestId
                    )
                )
            }
            pairingTimeoutJob = null
        }
    }

    private fun cancelPairingSearch(replacement: PairingFlowState) {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        scannedRequestId?.let(pendingConnections::remove)
        scannedRequestId = null
        _uiState.value = _uiState.value.copy(pairingFlow = replacement)
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (exception: RuntimeException) {
            Log.d(TAG, "Failed to release multicast lock", exception)
        }
        multicastLock = null
    }

    override fun onCleared() {
        pairingTimeoutJob?.cancel()
        cancelPairingSearch(PairingFlowState.Idle)
        stopDiscovery()
        super.onCleared()
    }
}

internal fun knownChildRows(
    trustedChildren: List<TrustedChild>,
    devices: List<DiscoveredDevice>
): List<KnownChildRow> = trustedChildren.map { child ->
    val device = devices.firstOrNull { it.childId == child.childId }
    KnownChildRow(
        child = child,
        status = when {
            device == null -> KnownChildStatus.NotFound
            device.pairingId == child.pairingId -> KnownChildStatus.Available
            else -> KnownChildStatus.PairAgain
        },
        device = device?.takeIf { it.pairingId == child.pairingId }
    )
}

private inline fun <T> MutableList<T>.removeAllIndexedAfter(
    keptIndex: Int,
    predicate: (T) -> Boolean
) {
    for (index in lastIndex downTo keptIndex + 1) {
        if (predicate(this[index])) removeAt(index)
    }
}
