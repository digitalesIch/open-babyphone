package org.openbabyphone.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.GlobalScope
import org.openbabyphone.ConnectionConstants

object MonitorServiceRepository {
    private val _serviceName = MutableStateFlow("")
    val serviceName: StateFlow<String> = _serviceName.asStateFlow()

    private val _port = MutableStateFlow(ConnectionConstants.DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _sessionState = MutableStateFlow<MonitorSessionState>(MonitorSessionState.Setup)
    val sessionState: StateFlow<MonitorSessionState> = _sessionState.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    fun updateServiceInfo(name: String, port: Int, addresses: List<String>) {
        _serviceName.value = name
        _port.value = port
        _addresses.value = addresses
    }

    fun updateSessionState(state: MonitorSessionState) {
        _sessionState.value = state
    }

    fun updateConnectedClients(count: Int) {
        _connectedClients.value = count
    }

    fun reset() {
        _serviceName.value = ""
        _port.value = ConnectionConstants.DEFAULT_PORT
        _addresses.value = emptyList()
        _sessionState.value = MonitorSessionState.Setup
        _connectedClients.value = 0
    }
}

object ListenServiceRepository {
    private val _childDeviceName = MutableStateFlow("")
    val childDeviceName: StateFlow<String> = _childDeviceName.asStateFlow()

    private val _sessionState = MutableStateFlow<ListenSessionState>(ListenSessionState.Connecting)
    val sessionState: StateFlow<ListenSessionState> = _sessionState.asStateFlow()

    val isConnected: StateFlow<Boolean> = _sessionState
        .map { it is ListenSessionState.Listening }
        .stateIn(GlobalScope, SharingStarted.Eagerly, false)

    val isError: StateFlow<Boolean> = _sessionState
        .map { it is ListenSessionState.Error || it is ListenSessionState.Lost }
        .stateIn(GlobalScope, SharingStarted.Eagerly, false)

    fun updateChildDeviceName(name: String) {
        _childDeviceName.value = name
    }

    fun startConnecting(name: String) {
        _childDeviceName.value = name
        _sessionState.value = ListenSessionState.Connecting
    }

    fun updateListening() {
        _sessionState.value = ListenSessionState.Listening
    }

    fun updateReconnecting(attempt: Int, maxAttempts: Int) {
        _sessionState.value = ListenSessionState.Reconnecting(attempt, maxAttempts)
    }

    fun updateDisrupted() {
        _sessionState.value = ListenSessionState.Disrupted
    }

    fun updateLost() {
        _sessionState.value = ListenSessionState.Lost
    }

    fun updateError(reason: String) {
        _sessionState.value = ListenSessionState.Error(reason)
    }

    fun updateStopped() {
        _sessionState.value = ListenSessionState.Stopped
    }

    fun reset() {
        _childDeviceName.value = ""
        _sessionState.value = ListenSessionState.Connecting
    }
}