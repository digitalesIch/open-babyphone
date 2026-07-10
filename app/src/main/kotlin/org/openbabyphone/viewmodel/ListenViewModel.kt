package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.openbabyphone.R
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ListenUiState(
    val childDeviceName: String = "",
    val status: String = "",
    val volumeHistory: FloatArray = floatArrayOf(),
    val volumeNorm: Float = 1.0f,
    val isConnected: Boolean = false,
    val isError: Boolean = false,
    val isReconnecting: Boolean = false
)

class ListenViewModel(application: Application) : AndroidViewModel(application) {
    private val _volumeHistory = MutableStateFlow(FloatArray(0))
    private val _volumeNorm = MutableStateFlow(1.0f)
    private val connectingStatus = application.getString(R.string.connecting)
    private val listeningStatus = application.getString(R.string.listening)
    private val disconnectedStatus = application.getString(R.string.disconnected)

    val uiState: StateFlow<ListenUiState> = combine(
        _volumeHistory,
        _volumeNorm,
        combine(
            ListenServiceRepository.childDeviceName,
            ListenServiceRepository.sessionState
        ) { name, state -> name to state },
        ListenServiceRepository.isError
    ) { volumeHistory, volumeNorm, repoInfo, isError ->
        val (name, sessionState) = repoInfo
        val connected = sessionState is ListenSessionState.Listening
        val status = when (sessionState) {
            is ListenSessionState.Listening -> listeningStatus
            is ListenSessionState.Reconnecting -> application.getString(
                R.string.reconnecting_status, sessionState.attempt, sessionState.maxAttempts
            )
            is ListenSessionState.Disrupted -> application.getString(R.string.connection_disrupted)
            is ListenSessionState.Error -> disconnectedStatus
            is ListenSessionState.Lost -> disconnectedStatus
            is ListenSessionState.Connecting -> connectingStatus
            is ListenSessionState.Stopped -> disconnectedStatus
        }
        ListenUiState(
            childDeviceName = name,
            status = status,
            volumeHistory = volumeHistory,
            volumeNorm = volumeNorm,
            isConnected = connected,
            isError = isError,
            isReconnecting = sessionState is ListenSessionState.Reconnecting
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListenUiState(status = connectingStatus))

    fun connect(address: String, port: Int, name: String, pairingCode: String) {
    }

    fun updateVolumeHistory(history: FloatArray, norm: Float) {
        _volumeHistory.value = history
        _volumeNorm.value = norm
    }
}