package org.openbabyphone.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    fun updateError(type: MonitorSessionError, reason: String) {
        _sessionState.value = MonitorSessionState.Error(type, reason)
    }

    fun updateStopped() {
        _sessionState.update { current ->
            if (current is MonitorSessionState.Error) current else MonitorSessionState.Stopped
        }
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

    private val _sessionState = MutableStateFlow<ListenSessionState>(ListenSessionState.Idle)
    val sessionState: StateFlow<ListenSessionState> = _sessionState.asStateFlow()

    fun updateChildDeviceName(name: String) {
        _childDeviceName.value = name
    }

    fun startConnecting(name: String) {
        _childDeviceName.value = name
        _sessionState.value = ListenSessionState.Connecting
    }

    fun updateListening() {
        _sessionState.update { current ->
            if (current is ListenSessionState.Error ||
                current is ListenSessionState.Lost ||
                current is ListenSessionState.Stopped
            ) {
                current
            } else {
                ListenSessionState.Listening
            }
        }
    }

    fun updateReconnecting(attempt: Int, maxAttempts: Int) {
        _sessionState.update { current ->
            if (current is ListenSessionState.Lost ||
                current is ListenSessionState.Error ||
                current is ListenSessionState.Stopped
            ) {
                current
            } else {
                ListenSessionState.Reconnecting(attempt, maxAttempts)
            }
        }
    }

    fun updateDisrupted() {
        _sessionState.update { current ->
            if (current is ListenSessionState.Error ||
                current is ListenSessionState.Stopped ||
                current is ListenSessionState.Lost
            ) {
                current
            } else {
                ListenSessionState.Disrupted
            }
        }
    }

    fun updateLost() {
        _sessionState.update { current ->
            if (current is ListenSessionState.Error || current is ListenSessionState.Stopped) {
                current
            } else {
                ListenSessionState.Lost
            }
        }
    }

    fun updateError(type: ListenSessionError, reason: String) {
        _sessionState.value = ListenSessionState.Error(type, reason)
    }

    fun updateStopped() {
        _sessionState.update { current ->
            if (current is ListenSessionState.Error || current is ListenSessionState.Lost) {
                current
            } else {
                ListenSessionState.Stopped
            }
        }
    }

    fun reset() {
        _childDeviceName.value = ""
        _sessionState.value = ListenSessionState.Idle
    }
}
