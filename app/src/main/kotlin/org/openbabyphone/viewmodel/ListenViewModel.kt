package org.openbabyphone.viewmodel

import android.app.Application
import android.content.res.Resources
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openbabyphone.R
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState

enum class ListenPrimaryAction {
    Retry,
    PairAgain,
    ConnectionHelp
}

data class ListenPresentation(
    val message: String,
    val detail: String,
    val showProgress: Boolean = false,
    val primaryAction: ListenPrimaryAction? = null
)

data class ListenUiState(
    val childDeviceName: String = "",
    val sessionState: ListenSessionState = ListenSessionState.Idle,
    val presentation: ListenPresentation = ListenPresentation("", ""),
    val volumeHistory: FloatArray = floatArrayOf(),
    val volumeNorm: Float = 1.0f,
    val lastAudioUpdateAtMillis: Long = 0L
)

class ListenViewModel(application: Application) : AndroidViewModel(application) {
    private val _volumeHistory = MutableStateFlow(FloatArray(0))
    private val _volumeNorm = MutableStateFlow(1.0f)
    private val _lastAudioUpdateAtMillis = MutableStateFlow(0L)

    val uiState: StateFlow<ListenUiState> = combine(
        _volumeHistory,
        _volumeNorm,
        _lastAudioUpdateAtMillis,
        combine(
            ListenServiceRepository.childDeviceName,
            ListenServiceRepository.sessionState
        ) { name, state -> name to state }
    ) { volumeHistory, volumeNorm, lastAudioUpdateAtMillis, repoInfo ->
        val (name, sessionState) = repoInfo
        ListenUiState(
            childDeviceName = name,
            sessionState = sessionState,
            presentation = listenPresentation(application, sessionState),
            volumeHistory = volumeHistory,
            volumeNorm = volumeNorm,
            lastAudioUpdateAtMillis = lastAudioUpdateAtMillis
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ListenUiState(
            sessionState = ListenSessionState.Connecting,
            presentation = listenPresentation(application, ListenSessionState.Connecting)
        )
    )

    fun updateVolumeHistory(history: FloatArray, norm: Float) {
        _volumeHistory.value = history
        _volumeNorm.value = norm
        _lastAudioUpdateAtMillis.value = SystemClock.elapsedRealtime()
    }
}

internal fun listenPresentation(
    application: Application,
    state: ListenSessionState
): ListenPresentation = listenPresentation(application.resources, state)

internal fun listenPresentation(
    resources: Resources,
    state: ListenSessionState
): ListenPresentation = when (state) {
    ListenSessionState.Connecting -> ListenPresentation(
        resources.getString(R.string.listen_connecting_title),
        resources.getString(R.string.listen_connecting_detail),
        showProgress = true
    )
    ListenSessionState.Listening -> ListenPresentation(
        resources.getString(R.string.listen_listening_title),
        resources.getString(R.string.listen_listening_detail)
    )
    is ListenSessionState.Reconnecting -> ListenPresentation(
        resources.getString(R.string.listen_reconnecting_title),
        resources.getString(R.string.listen_reconnecting_detail, state.attempt, state.maxAttempts),
        showProgress = true
    )
    ListenSessionState.Disrupted -> ListenPresentation(
        resources.getString(R.string.audio_interrupted),
        resources.getString(R.string.audio_interrupted_recovery)
    )
    ListenSessionState.Lost -> ListenPresentation(
        resources.getString(R.string.connection_lost),
        resources.getString(R.string.connection_lost_detail),
        primaryAction = ListenPrimaryAction.Retry
    )
    is ListenSessionState.Error -> when (state.type) {
        ListenSessionError.Unreachable -> ListenPresentation(
            resources.getString(R.string.could_not_connect),
            resources.getString(R.string.could_not_connect_detail),
            primaryAction = ListenPrimaryAction.Retry
        )
        ListenSessionError.Authentication -> ListenPresentation(
            resources.getString(R.string.pair_again),
            resources.getString(R.string.authentication_failed_detail),
            primaryAction = ListenPrimaryAction.PairAgain
        )
        ListenSessionError.CredentialStorage -> ListenPresentation(
            resources.getString(R.string.could_not_save_pairing),
            resources.getString(R.string.could_not_save_pairing_detail),
            primaryAction = ListenPrimaryAction.Retry
        )
        ListenSessionError.Playback -> ListenPresentation(
            resources.getString(R.string.playback_failed),
            resources.getString(R.string.playback_failed_detail),
            primaryAction = ListenPrimaryAction.Retry
        )
        ListenSessionError.Decoding -> ListenPresentation(
            resources.getString(R.string.audio_stream_failed),
            resources.getString(R.string.audio_stream_failed_detail),
            primaryAction = ListenPrimaryAction.ConnectionHelp
        )
    }
    ListenSessionState.Idle,
    ListenSessionState.Stopped -> ListenPresentation(
        resources.getString(R.string.listening_stopped),
        resources.getString(R.string.listening_stopped_detail)
    )
}
