package org.openbabyphone.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openbabyphone.ConnectionConstants

object MonitorServiceRepository {
    private val _serviceName = MutableStateFlow("")
    val serviceName: StateFlow<String> = _serviceName.asStateFlow()

    private val _port = MutableStateFlow(ConnectionConstants.DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    fun updateServiceInfo(name: String, port: Int, addresses: List<String>) {
        _serviceName.value = name
        _port.value = port
        _addresses.value = addresses
    }

    fun updateStatus(status: String) {
        _status.value = status
    }

    fun updateConnectedClients(count: Int) {
        _connectedClients.value = count
    }
}

object ListenServiceRepository {
    private val _childDeviceName = MutableStateFlow("")
    val childDeviceName: StateFlow<String> = _childDeviceName.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    fun updateChildDeviceName(name: String) {
        _childDeviceName.value = name
    }

    fun startConnecting(name: String, status: String = "") {
        _childDeviceName.value = name
        _status.value = status
        _isConnected.value = false
        _isError.value = false
    }

    fun updateStatus(status: String) {
        _status.value = status
    }

    fun updateConnected(connected: Boolean, status: String = "") {
        _isConnected.value = connected
        if (connected) {
            _isError.value = false
            _status.value = status
        }
    }

    fun updateError(status: String = "") {
        _isError.value = true
        _status.value = status
        _isConnected.value = false
    }
}
