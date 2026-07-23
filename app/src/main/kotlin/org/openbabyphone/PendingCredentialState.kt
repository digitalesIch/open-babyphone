package org.openbabyphone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

internal enum class PendingCredentialState {
    None,
    Available,
    Expired
}

@Composable
internal fun pendingCredentialState(
    requestId: String,
    store: PendingConnectionStore = PendingConnections.store
): PendingCredentialState {
    val revision by store.changes.collectAsState()
    return remember(requestId, revision, store) {
        when {
            requestId.isBlank() -> PendingCredentialState.None
            store.contains(requestId) -> PendingCredentialState.Available
            else -> PendingCredentialState.Expired
        }
    }
}
