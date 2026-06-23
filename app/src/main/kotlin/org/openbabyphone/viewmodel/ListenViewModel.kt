package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.openbabyphone.R
import org.openbabyphone.service.ListenServiceRepository
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
    val isError: Boolean = false
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
            ListenServiceRepository.status,
            ListenServiceRepository.isConnected
        ) { name, status, connected ->
            Triple(name, status, connected)
        },
        ListenServiceRepository.isError
    ) { volumeHistory, volumeNorm, repoInfo, isError ->
        val connected = repoInfo.third
        val status = repoInfo.second.ifEmpty {
            when {
                isError -> disconnectedStatus
                connected -> listeningStatus
                else -> connectingStatus
            }
        }
        ListenUiState(
            childDeviceName = repoInfo.first,
            status = status,
            volumeHistory = volumeHistory,
            volumeNorm = volumeNorm,
            isConnected = connected,
            isError = isError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListenUiState(status = connectingStatus))

    fun connect(address: String, port: Int, name: String, pairingCode: String) {
        // ListenService will be started via Intent
        // Repository will be updated by service callbacks
    }

    fun updateVolumeHistory(history: FloatArray, norm: Float) {
        _volumeHistory.value = history
        _volumeNorm.value = norm
    }
}
