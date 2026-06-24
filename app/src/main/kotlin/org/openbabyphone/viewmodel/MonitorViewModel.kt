package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.openbabyphone.DeviceName
import org.openbabyphone.PairingCode
import org.openbabyphone.PairingCodeGenerator
import org.openbabyphone.MonitorService
import org.openbabyphone.R
import org.openbabyphone.service.MonitorServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MonitorUiState(
    val pairingCode: String = "",
    val deviceName: String = "",
    val serviceName: String = "",
    val port: Int = 10000,
    val addresses: List<String> = emptyList(),
    val status: String = "",
    val isLoading: Boolean = true
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val _pairingCode = MutableStateFlow("")
    private val _deviceName = MutableStateFlow("")
    private val loadingLabel = application.getString(R.string.loading)
    private val waitingForParentStatus = application.getString(R.string.waiting_for_parent)

    val uiState: StateFlow<MonitorUiState> = combine(
        _pairingCode,
        _deviceName,
        combine(
            MonitorServiceRepository.serviceName,
            MonitorServiceRepository.port,
            MonitorServiceRepository.addresses,
            MonitorServiceRepository.status
        ) { name, port, addresses, status ->
            ServiceInfo(name, port, addresses, status)
        }
    ) { pairingCode, deviceName, info ->
        MonitorUiState(
            pairingCode = pairingCode,
            deviceName = deviceName,
            serviceName = info.name.ifEmpty { loadingLabel },
            port = info.port,
            addresses = info.addresses,
            status = info.status.ifEmpty { waitingForParentStatus },
            isLoading = info.name.isEmpty()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MonitorUiState(serviceName = loadingLabel, status = waitingForParentStatus)
    )

    private data class ServiceInfo(
        val name: String,
        val port: Int,
        val addresses: List<String>,
        val status: String
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
    }

    fun updatePairingCode(code: String) {
        if (!PairingCode.isValid(code)) {
            return
        }
        val normalizedCode = PairingCode.normalize(code)
        _pairingCode.value = normalizedCode
        val prefs = getApplication<Application>().getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        )
        prefs.edit()
            .putString(MonitorService.PREF_KEY_PAIRING_CODE, normalizedCode)
            .apply()
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
}