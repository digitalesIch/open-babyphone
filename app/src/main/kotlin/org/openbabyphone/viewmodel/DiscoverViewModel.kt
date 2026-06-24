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
import org.openbabyphone.PairingCode
import java.util.concurrent.ConcurrentLinkedQueue

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val port: Int
)

data class DiscoverUiState(
    val isDiscovering: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val pairingCode: String = "",
    val error: String? = null
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

    companion object {
        private const val TAG = "DiscoverViewModel"
        private const val SERVICE_TYPE = "_childmonitor._tcp."
        private const val PREF_KEY_PAIRING_CODE = "pairingCode"
    }

    init {
        loadSavedPairingCode()
    }

    private fun loadSavedPairingCode() {
        val prefs = getApplication<Application>().getSharedPreferences(
            "DiscoverPrefs", Context.MODE_PRIVATE
        )
        _uiState.value = _uiState.value.copy(
            pairingCode = prefs.getString(PREF_KEY_PAIRING_CODE, "") ?: ""
        )
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
                if (service.serviceType == SERVICE_TYPE && service.serviceName.startsWith("Open Babyphone")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredDevices.removeAll { it.name == service.serviceName }
                _uiState.value = _uiState.value.copy(devices = discoveredDevices.toList())
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

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
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

                val device = DiscoveredDevice(name, hostAddress, serviceInfo.port)
                if (discoveredDevices.none { it.address == device.address && it.port == device.port }) {
                    discoveredDevices.add(device)
                    _uiState.value = _uiState.value.copy(devices = discoveredDevices.toList())
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
