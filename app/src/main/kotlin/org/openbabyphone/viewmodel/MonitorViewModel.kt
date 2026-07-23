package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.openbabyphone.BatteryOptimization
import org.openbabyphone.ConnectionConstants
import org.openbabyphone.ChildDeviceIdentityStore
import org.openbabyphone.ChildDeviceNamePreferences
import org.openbabyphone.PairingCode
import org.openbabyphone.PairingSettings
import org.openbabyphone.PairingQrCode
import org.openbabyphone.R
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.service.isAuthoritativelyActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val terminalErrorReason: String? = null,
    val sessionState: org.openbabyphone.service.MonitorSessionState = org.openbabyphone.service.MonitorSessionState.Setup,
    val qrPayload: String = "",
    val batteryOptimizationIgnored: Boolean = false
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val _pairingCode = MutableStateFlow("")
    private val _deviceName = MutableStateFlow("")
    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    private val loadingLabel = application.getString(R.string.loading)
    private val waitingForParentStatus = application.getString(R.string.waiting_for_parent)
    private val defaultDeviceName = application.getString(R.string.default_child_name)

    private val childIdentityStore = ChildDeviceIdentityStore(application)

    private data class ServiceInfo(
        val name: String,
        val port: Int,
        val addresses: List<String>,
        val sessionState: MonitorSessionState
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
        combine(
            MonitorServiceRepository.serviceName,
            MonitorServiceRepository.port,
            MonitorServiceRepository.addresses,
            MonitorServiceRepository.sessionState
        ) { name, port, addresses, sessionState ->
            ServiceInfo(name, port, addresses, sessionState)
        }
    ) { setupInfo, info ->
        val identity = childIdentityStore.identity
        val qrPayload = if (setupInfo.pairingCode.isNotBlank() && PairingCode.isValid(setupInfo.pairingCode)) {
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
            pairingCodeValid = setupInfo.pairingCode.isNotBlank() && PairingCode.isValid(setupInfo.pairingCode),
            deviceName = setupInfo.deviceName,
            serviceName = info.name.ifEmpty { loadingLabel },
            port = info.port,
            addresses = info.addresses,
            status = status.ifEmpty { waitingForParentStatus },
            connectedClients = (info.sessionState as? MonitorSessionState.Connected)?.parentCount ?: 0,
            isLoading = info.name.isEmpty(),
            isMonitoring = info.sessionState.isAuthoritativelyActive(),
            terminalErrorReason = (info.sessionState as? MonitorSessionState.Error)
                ?.takeUnless { info.sessionState.isAuthoritativelyActive() }
                ?.reason,
            sessionState = info.sessionState,
            qrPayload = qrPayload,
            batteryOptimizationIgnored = setupInfo.batteryOptimizationIgnored
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MonitorUiState(serviceName = loadingLabel, status = waitingForParentStatus)
    )

    init {
        refreshChildConfiguration()
        refreshBatteryOptimizationStatus()
    }

    private fun refreshChildConfiguration() {
        val application = getApplication<Application>()
        _pairingCode.value = PairingSettings.load(application).pairingCode
        _deviceName.value = ChildDeviceNamePreferences.read(application, defaultDeviceName)
    }

    fun refreshBatteryOptimizationStatus() {
        _batteryOptimizationIgnored.value = BatteryOptimization.isIgnoringBatteryOptimizations(getApplication())
    }

    fun startMonitoring() {
        refreshChildConfiguration()
        refreshBatteryOptimizationStatus()
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Starting)
    }

    fun stopMonitoring() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Stopped)
    }

}
