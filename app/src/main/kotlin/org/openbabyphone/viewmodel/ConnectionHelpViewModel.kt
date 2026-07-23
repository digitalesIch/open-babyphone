package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openbabyphone.PendingConnection
import org.openbabyphone.PendingConnections
import org.openbabyphone.TrustedChild
import org.openbabyphone.WifiDirectController
import org.openbabyphone.WifiDirectSession
import org.openbabyphone.WifiDirectState
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.trustedChildStore

data class ConnectionHelpUiState(
    val knownChildren: List<TrustedChild> = emptyList(),
    val childWifiDirectState: WifiDirectState = WifiDirectState.Idle,
    val wifiDirectSupported: Boolean = true
)

data class LastKnownConnection(
    val requestId: String,
    val childId: String,
    val pairingId: String
)

class ConnectionHelpViewModel @JvmOverloads constructor(
    application: Application,
    private val wifiDirectController: WifiDirectSession = WifiDirectController(application)
) : AndroidViewModel(application) {
    private val trustedChildren = MutableStateFlow(application.trustedChildStore().getAll())

    val uiState: StateFlow<ConnectionHelpUiState> = combine(
        trustedChildren,
        wifiDirectController.state
    ) { children, wifiState ->
        ConnectionHelpUiState(
            knownChildren = children,
            childWifiDirectState = wifiState,
            wifiDirectSupported = wifiDirectController.isSupported()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ConnectionHelpUiState(wifiDirectSupported = wifiDirectController.isSupported())
    )

    fun tryLastKnownConnection(childId: String): LastKnownConnection? {
        val child = trustedChildren.value.firstOrNull { it.childId == childId } ?: return null
        val address = child.lastKnownAddress?.takeIf { it.isNotBlank() } ?: return null
        val port = child.lastKnownPort?.takeIf { it in 1..65535 } ?: return null
        val requestId = PendingConnections.store.put(
            PendingConnection(
                address = address,
                port = port,
                name = child.displayName,
                pairingCode = null,
                expectedChildId = child.childId,
                expectedPairingId = child.pairingId
            )
        )
        return LastKnownConnection(requestId, child.childId, child.pairingId)
    }

    fun forget(childId: String) {
        getApplication<Application>().trustedChildStore().forget(childId)
        trustedChildren.value = getApplication<Application>().trustedChildStore().getAll()
    }

    fun startChildWifiDirect() {
        wifiDirectController.startChildAdvertising(
            MonitorServiceRepository.port.value,
            MonitorServiceRepository.serviceName.value
        )
    }

    fun stopChildWifiDirect() {
        wifiDirectController.stop()
    }

    override fun onCleared() {
        wifiDirectController.stop()
        super.onCleared()
    }
}
