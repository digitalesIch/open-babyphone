package de.rochefort.childmonitor.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MonitorServiceRepository {
    private val _serviceName = MutableStateFlow("")
    val serviceName: StateFlow<String> = _serviceName.asStateFlow()

    private val _port = MutableStateFlow(10000)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _status = MutableStateFlow("Waiting for Parent...")
    val status: StateFlow<String> = _status.asStateFlow()

    fun updateServiceInfo(name: String, port: Int, addresses: List<String>) {
        _serviceName.value = name
        _port.value = port
        _addresses.value = addresses
    }

    fun updateStatus(status: String) {
        _status.value = status
    }
}

object ListenServiceRepository {
    private val _childDeviceName = MutableStateFlow("")
    val childDeviceName: StateFlow<String> = _childDeviceName.asStateFlow()

    private val _status = MutableStateFlow("Connecting...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    fun updateChildDeviceName(name: String) {
        _childDeviceName.value = name
    }

    fun updateStatus(status: String) {
        _status.value = status
    }

    fun updateConnected(connected: Boolean) {
        _isConnected.value = connected
        if (connected) {
            _status.value = "Listening..."
        }
    }

    fun updateError() {
        _isError.value = true
        _status.value = "Disconnected"
        _isConnected.value = false
    }
}
