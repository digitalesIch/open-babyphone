package org.openbabyphone.viewmodel

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openbabyphone.ConnectionConstants
import org.openbabyphone.PairingCode
import org.openbabyphone.PairingQrCode
import org.openbabyphone.TrustedChild
import org.openbabyphone.TrustedChildStore
import java.util.concurrent.ConcurrentLinkedQueue

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val port: Int,
    val childId: String? = null,
    val pairingId: String? = null,
    val displayName: String? = null
) {
    /**
     * The name to show in the UI. Falls back to the NSD service name when no
     * display name TXT attribute is available.
     */
    val visibleName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: name
}

/**
 * Status of a discovered device relative to the parent's trusted child list.
 */
sealed interface DeviceTrustStatus {
    /**
     * The device is a known child with a matching pairing generation.
     */
    data object Trusted : DeviceTrustStatus

    /**
     * The device's childId is known but the pairingId differs, meaning the
     * child's pairing code was reset and re-pairing is required.
     */
    data object PairingReset : DeviceTrustStatus

    /**
     * The device is not in the trusted child list.
     */
    data object Unknown : DeviceTrustStatus
}

data class DiscoverUiState(
    val isDiscovering: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val pairingCode: String = "",
    val error: String? = null,
    val trustedChildren: List<TrustedChild> = emptyList(),
    val trustedChildStatuses: Map<String, DeviceTrustStatus> = emptyMap()
)

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val discoveredDevices: MutableList<DiscoveredDevice> = mutableListOf()
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile private var isResolving = false

    private val trustedChildStore = TrustedChildStore(application)

    companion object {
        private const val TAG = "DiscoverViewModel"
        private const val PREF_KEY_PAIRING_CODE = "pairingCode"
    }

    init {
        loadSavedPairingCode()
        refreshTrustedChildren()
    }

    private fun loadSavedPairingCode() {
        val prefs = getApplication<Application>().getSharedPreferences(
            "DiscoverPrefs", Context.MODE_PRIVATE
        )
        _uiState.value = _uiState.value.copy(
            pairingCode = prefs.getString(PREF_KEY_PAIRING_CODE, "") ?: ""
        )
    }

    private fun refreshTrustedChildren() {
        _uiState.value = _uiState.value.copy(trustedChildren = trustedChildStore.getAll())
    }

    internal fun recordLastKnownChildAddress(childId: String?, address: String, port: Int) {
        if (childId == null) return
        trustedChildStore.updateLastKnown(childId, address, port)
        refreshTrustedChildren()
    }

    fun updatePairingCode(code: String) {
        if (!PairingCode.isValid(code)) {
            return
        }
        val normalizedCode = PairingCode.normalize(code)
        _uiState.value = _uiState.value.copy(pairingCode = normalizedCode)
        getApplication<Application>().getSharedPreferences(
            "DiscoverPrefs", Context.MODE_PRIVATE
        ).edit().putString(PREF_KEY_PAIRING_CODE, normalizedCode).apply()
    }

    fun trustAndPair(device: DiscoveredDevice, pairingCode: String): String? {
        if (!PairingCode.isValid(pairingCode)) return null
        val normalizedCode = PairingCode.normalize(pairingCode)
        val childId = device.childId
        val pairingId = device.pairingId
        if (childId != null && pairingId != null) {
            trustedChildStore.upsert(
                TrustedChild(
                    childId = childId,
                    pairingId = pairingId,
                    displayName = device.visibleName,
                    pairingCode = normalizedCode,
                    lastKnownAddress = device.address,
                    lastKnownPort = device.port,
                    lastSeenAt = System.currentTimeMillis()
                )
            )
            refreshTrustedChildren()
            recomputeTrustStatuses()
        }
        return normalizedCode
    }

    /**
     * Processes a scanned QR code. If the QR contains a structured pairing
     * payload, the child is stored as a trusted device and the pairing code
     * is set automatically.
     *
     * @return `true` if the scan produced a valid result (legacy or structured),
     *         `false` if the QR content was invalid.
     */
    fun handleQrScan(content: String?): Boolean {
        val parsed = PairingQrCode.parse(content) ?: return false
        return when (parsed) {
            is PairingQrCode.ParsedQrCode.Legacy -> {
                if (PairingCode.isValid(parsed.pairingCode)) {
                    updatePairingCode(parsed.pairingCode)
                    true
                } else false
            }
            is PairingQrCode.ParsedQrCode.Structured -> {
                if (!PairingCode.isValid(parsed.pairingCode)) return false
                val trusted = TrustedChild(
                    childId = parsed.childId,
                    pairingId = parsed.pairingId,
                    displayName = parsed.name,
                    pairingCode = parsed.pairingCode
                )
                trustedChildStore.upsert(trusted)
                updatePairingCode(parsed.pairingCode)
                refreshTrustedChildren()
                recomputeTrustStatuses()
                true
            }
        }
    }

    /**
     * Forgets (removes) a trusted child profile.
     */
    fun forgetChild(childId: String) {
        trustedChildStore.forget(childId)
        refreshTrustedChildren()
        recomputeTrustStatuses()
    }

    /**
     * Returns the pairing code to use when connecting to the given discovered
     * device. If the device is a trusted child, the stored pairing code is used;
     * otherwise the manually entered pairing code is used.
     */
    fun pairingCodeFor(device: DiscoveredDevice): String {
        val childId = device.childId ?: return _uiState.value.pairingCode
        val trusted = trustedChildStore.findById(childId) ?: return _uiState.value.pairingCode
        return if (trusted.matchesPairing(device.pairingId ?: "")) {
            trusted.pairingCode
        } else {
            _uiState.value.pairingCode
        }
    }

    fun startDiscovery() {
        if (_uiState.value.isDiscovering) return

        val context = getApplication<Application>()
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("multicastLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        discoveredDevices.clear()
        _uiState.value = _uiState.value.copy(isDiscovering = true, devices = emptyList(), error = null)

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == ConnectionConstants.SERVICE_TYPE && service.serviceName.startsWith(ConnectionConstants.SERVICE_NAME_PREFIX)) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredDevices.removeAll { it.name == service.serviceName }
                _uiState.value = _uiState.value.copy(devices = discoveredDevices.toList())
                recomputeTrustStatuses()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                releaseMulticastLock()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    error = "Discovery failed: $errorCode"
                )
                nsdManager?.stopServiceDiscovery(this)
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
                releaseMulticastLock()
            }
        }

        nsdManager?.discoverServices(ConnectionConstants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveService(service: NsdServiceInfo) {
        resolveQueue.add(service)
        processResolveQueue()
    }

    private fun processResolveQueue() {
        if (isResolving) return
        val service = resolveQueue.poll() ?: return
        isResolving = true
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isResolving = false
                processResolveQueue()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val hostAddress = serviceInfo.host?.hostAddress ?: run {
                    isResolving = false
                    processResolveQueue()
                    return
                }
                val name = serviceInfo.serviceName
                    .replace("\\\\032", " ")
                    .replace("\\032", " ")

                val txtMap = serviceInfo.attributes
                val childId = txtMap?.get(ConnectionConstants.NSD_TXT_CHILD_ID)?.let { String(it, Charsets.UTF_8) }
                val pairingId = txtMap?.get(ConnectionConstants.NSD_TXT_PAIRING_ID)?.let { String(it, Charsets.UTF_8) }
                val displayName = txtMap?.get(ConnectionConstants.NSD_TXT_NAME)?.let { String(it, Charsets.UTF_8) }

                val device = DiscoveredDevice(
                    name = name,
                    address = hostAddress,
                    port = serviceInfo.port,
                    childId = childId,
                    pairingId = pairingId,
                    displayName = displayName
                )
                recordLastKnownChildAddress(childId, hostAddress, serviceInfo.port)
                if (discoveredDevices.none { it.address == device.address && it.port == device.port }) {
                    discoveredDevices.add(device)
                    _uiState.value = _uiState.value.copy(devices = discoveredDevices.toList())
                    recomputeTrustStatuses()
                }
                isResolving = false
                processResolveQueue()
            }
        }
        nsdManager?.resolveService(service, resolver)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            nsdManager?.stopServiceDiscovery(it)
        }
        discoveryListener = null
        _uiState.value = _uiState.value.copy(isDiscovering = false)
    }

    private fun recomputeTrustStatuses() {
        val devices = _uiState.value.devices
        val statuses = mutableMapOf<String, DeviceTrustStatus>()
        for (device in devices) {
            val childId = device.childId ?: continue
            val trusted = trustedChildStore.findById(childId)
            val key = "${device.address}:${device.port}"
            statuses[key] = when {
                trusted == null -> DeviceTrustStatus.Unknown
                trusted.matchesPairing(device.pairingId ?: "") -> DeviceTrustStatus.Trusted
                else -> DeviceTrustStatus.PairingReset
            }
        }
        _uiState.value = _uiState.value.copy(trustedChildStatuses = statuses)
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to release multicast lock", e)
        }
        multicastLock = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
        releaseMulticastLock()
    }
}
