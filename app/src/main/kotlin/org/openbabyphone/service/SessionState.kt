package org.openbabyphone.service

sealed interface MonitorSessionState {
    data object Setup : MonitorSessionState
    data object Starting : MonitorSessionState
    data object WaitingForParent : MonitorSessionState
    data class Connected(val parentCount: Int) : MonitorSessionState
    data object NoNetwork : MonitorSessionState
    data class Error(val type: MonitorSessionError, val reason: String) : MonitorSessionState
    data object Stopped : MonitorSessionState
}

enum class MonitorSessionError {
    Advertising,
    Startup,
    Authentication,
    AudioCapture,
    AudioEncoding
}

sealed interface ListenSessionState {
    data object Idle : ListenSessionState
    data object Connecting : ListenSessionState
    data object Listening : ListenSessionState
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ListenSessionState
    data object Disrupted : ListenSessionState
    data object Lost : ListenSessionState
    data class Error(val type: ListenSessionError, val reason: String) : ListenSessionState
    data object Stopped : ListenSessionState
}

enum class ListenSessionError {
    Unreachable,
    Authentication,
    CredentialStorage,
    Playback,
    Decoding
}
