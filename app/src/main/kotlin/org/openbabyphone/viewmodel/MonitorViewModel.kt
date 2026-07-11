package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.openbabyphone.BatteryOptimization
import org.openbabyphone.ConnectionConstants
import org.openbabyphone.ChildDeviceIdentityStore
import org.openbabyphone.DeviceName
import org.openbabyphone.MicrophoneSensitivity
import org.openbabyphone.PairingCode
import org.openbabyphone.PairingCodeGenerator
import org.openbabyphone.MonitorService
import org.openbabyphone.PairingQrCode
import org.openbabyphone.R
import org.openbabyphone.WifiDirectController
import org.openbabyphone.WifiDirectState
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MonitorUiState(
    val pairingCode: String = "",
    val pairingCodeValid: Boolean = true,
    val deviceName: String = "",
    val serviceName: String = "",
    val port: Int = ConnectionConstants.DEFAULT_PORT,
    val addresses: List<String> = emptyList(),
    val status: String = "",
    val connectedClients: Int = 0,
    val isLoading: Boolean = true,
    val isMonitoring: Boolean = false,
    val sessionState: org.openbabyphone.service.MonitorSessionState = org.openbabyphone.service.MonitorSessionState.Setup,
    val wifiDirectState: WifiDirectState = WifiDirectState.Idle,
    val wifiDirectSupported: Boolean = true,
    val qrPayload: String = "",
    val microphoneSensitivity: MicrophoneSensitivity = MicrophoneSensitivity.NORMAL,
    val batteryOptimizationIgnored: Boolean = false
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val _pairingCode = MutableStateFlow("")
    private val _deviceName = MutableStateFlow("")
    private val _microphoneSensitivity = MutableStateFlow(MicrophoneSensitivity.NORMAL)
    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    private val loadingLabel = application.getString(R.string.loading)
    private val waitingForParentStatus = application.getString(R.string.waiting_for_parent)

    private val childIdentityStore = ChildDeviceIdentityStore(application)
    private val wifiDirectController = WifiDirectController(application)
    private val wifiDirectSupported = wifiDirectController.isSupported()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private data class ServiceInfo(
        val name: String,
        val port: Int,
        val addresses: List<String>,
        val sessionState: MonitorSessionState,
        val isMonitoring: Boolean,
        val microphoneSensitivity: MicrophoneSensitivity
    )

    private data class SetupInfo(
        val pairingCode: String,
        val deviceName: String,
        val batteryOptimizationIgnored: Boolean
    )

    val uiState: StateFlow<MonitorUiState> = combine(
        combine(_pairingCode, _deviceName, _batteryOptimizationIgnored) { pairingCode, deviceName, batteryOptimizationIgnored ->
            SetupInfo(pairingCode, deviceName, batteryOptimizationIgnored)
        },
        MonitorServiceRepository.connectedClients,
        combine(
            MonitorServiceRepository.serviceName,
            MonitorServiceRepository.port,
            MonitorServiceRepository.addresses,
            combine(
                MonitorServiceRepository.sessionState,
                _microphoneSensitivity,
                _isMonitoring
            ) { sessionState, micSensitivity, isMonitoring ->
                Triple(sessionState, micSensitivity, isMonitoring)
            }
        ) { name, port, addresses, stateInfo ->
            val (sessionState, micSensitivity, isMonitoring) = stateInfo
            ServiceInfo(name, port, addresses, sessionState, isMonitoring, micSensitivity)
        },
        wifiDirectController.state
    ) { setupInfo, clients, info, wifiDirect ->
        val identity = childIdentityStore.identity
        val qrPayload = if (setupInfo.pairingCode.isNotEmpty() && PairingCode.isValid(setupInfo.pairingCode)) {
            PairingQrCode.buildPayload(
                childId = identity.childId,
                pairingId = identity.pairingId,
                name = setupInfo.deviceName,
                pairingCode = setupInfo.pairingCode
            )
        } else {
            ""
        }
        val status = when (info.sessionState) {
            is MonitorSessionState.Setup -> ""
            is MonitorSessionState.Starting -> getApplication<Application>().getString(R.string.streaming)
            is MonitorSessionState.WaitingForParent -> waitingForParentStatus
            is MonitorSessionState.Connected -> getApplication<Application>().resources.getQuantityString(
                R.plurals.connected_clients, info.sessionState.parentCount, info.sessionState.parentCount
            )
            is MonitorSessionState.NoNetwork -> getApplication<Application>().getString(R.string.not_connected)
            is MonitorSessionState.Error -> info.sessionState.reason
            is MonitorSessionState.Stopped -> getApplication<Application>().getString(R.string.stopped)
        }
        MonitorUiState(
            pairingCode = setupInfo.pairingCode,
            pairingCodeValid = PairingCode.isValid(setupInfo.pairingCode),
            deviceName = setupInfo.deviceName,
            serviceName = info.name.ifEmpty { loadingLabel },
            port = info.port,
            addresses = info.addresses,
            status = status.ifEmpty { waitingForParentStatus },
            connectedClients = clients,
            isLoading = info.name.isEmpty(),
            isMonitoring = info.isMonitoring,
            sessionState = info.sessionState,
            wifiDirectState = wifiDirect,
            wifiDirectSupported = wifiDirectSupported,
            qrPayload = qrPayload,
            microphoneSensitivity = info.microphoneSensitivity,
            batteryOptimizationIgnored = setupInfo.batteryOptimizationIgnored
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MonitorUiState(serviceName = loadingLabel, status = waitingForParentStatus)
    )

    init {
        val prefs = application.getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        )
        val savedCode = prefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, "") ?: ""
        if (savedCode.isEmpty()) {
            val generatedCode = PairingCodeGenerator.generate()
            _pairingCode.value = generatedCode
            prefs.edit()
                .putString(MonitorService.PREF_KEY_PAIRING_CODE, generatedCode)
                .apply()
        } else {
            _pairingCode.value = savedCode
        }
        val savedDeviceName = prefs.getString(MonitorService.PREF_KEY_DEVICE_NAME, "") ?: ""
        _deviceName.value = savedDeviceName
        val savedSensitivity = MicrophoneSensitivity.fromPreferenceValue(
            prefs.getString(MonitorService.PREF_KEY_MICROPHONE_SENSITIVITY, null)
        )
        _microphoneSensitivity.value = savedSensitivity
        refreshBatteryOptimizationStatus()
    }

    fun refreshBatteryOptimizationStatus() {
        _batteryOptimizationIgnored.value = BatteryOptimization.isIgnoringBatteryOptimizations(getApplication())
    }

    fun updatePairingCode(code: String) {
        val normalizedCode = PairingCode.normalize(code)
        _pairingCode.value = normalizedCode
        if (!PairingCode.isValid(normalizedCode)) {
            return
        }
        val prefs = getApplication<Application>().getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        )
        prefs.edit()
            .putString(MonitorService.PREF_KEY_PAIRING_CODE, normalizedCode)
            .apply()
        childIdentityStore.rotatePairingId()
    }

    fun updateDeviceName(name: String) {
        val trimmed = DeviceName.normalize(name)
        if (!DeviceName.isValid(trimmed) && trimmed.isNotEmpty()) {
            return
        }
        _deviceName.value = trimmed
        val prefs = getApplication<Application>().getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        )
        prefs.edit()
            .putString(MonitorService.PREF_KEY_DEVICE_NAME, trimmed)
            .apply()
    }

    fun startMonitoring() {
        refreshBatteryOptimizationStatus()
        _isMonitoring.value = true
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
    }

    fun updateMicrophoneSensitivity(sensitivity: MicrophoneSensitivity) {
        _microphoneSensitivity.value = sensitivity
        val prefs = getApplication<Application>().getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        )
        prefs.edit()
            .putString(MonitorService.PREF_KEY_MICROPHONE_SENSITIVITY, sensitivity.preferenceValue)
            .apply()
    }

    /**
     * Resets the pairing code to a new generated value and rotates the pairing
     * generation id. Parents that previously paired with this child must
     * re-scan the QR code to reconnect.
     */
    fun resetPairing() {
        val newCode = PairingCodeGenerator.generate()
        _pairingCode.value = newCode
        val prefs = getApplication<Application>().getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        )
        prefs.edit()
            .putString(MonitorService.PREF_KEY_PAIRING_CODE, newCode)
            .apply()
        childIdentityStore.rotatePairingId()
    }

    fun startWifiDirect() {
        wifiDirectController.startChildAdvertising(uiState.value.port, uiState.value.serviceName)
    }

    fun stopWifiDirect() {
        wifiDirectController.stop()
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirectController.stop()
    }
}
