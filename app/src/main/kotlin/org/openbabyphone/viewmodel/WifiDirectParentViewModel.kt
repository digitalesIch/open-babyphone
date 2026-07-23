/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openbabyphone.PairingCode
import org.openbabyphone.WifiDirectController
import org.openbabyphone.WifiDirectCleanupCoordinator
import org.openbabyphone.WifiDirectEndpoint
import org.openbabyphone.WifiDirectPeer
import org.openbabyphone.WifiDirectSession
import org.openbabyphone.WifiDirectState

data class WifiDirectParentUiState(
    val wifiDirectState: WifiDirectState = WifiDirectState.Idle,
    val wifiDirectSupported: Boolean = true,
    val pairingCode: String = ""
)

class WifiDirectParentViewModel @JvmOverloads constructor(
    application: Application,
    private val controller: WifiDirectSession = WifiDirectController(application),
    private val cleanupCoordinator: WifiDirectCleanupCoordinator =
        (application as org.openbabyphone.OpenBabyphoneApplication).wifiDirectCleanupCoordinator
) : AndroidViewModel(application) {
    private val wifiDirectSupported = controller.isSupported()
    private val _pairingCode = MutableStateFlow("")
    private var handoffConsumed = false

    val uiState: StateFlow<WifiDirectParentUiState> = combine(
        controller.state,
        _pairingCode
    ) { state, pairingCode ->
        WifiDirectParentUiState(
            wifiDirectState = state,
            wifiDirectSupported = wifiDirectSupported,
            pairingCode = pairingCode
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WifiDirectParentUiState(wifiDirectSupported = wifiDirectSupported)
    )

    fun updatePairingCode(code: String) {
        _pairingCode.value = code.take(PairingCode.MAX_LENGTH)
    }

    fun clearPairingCode() {
        _pairingCode.value = ""
    }

    fun startDiscovery() {
        if (handoffConsumed) cleanupCoordinator.cleanup()
        handoffConsumed = false
        controller.startParentDiscovery()
    }

    fun connectToPeer(peer: WifiDirectPeer, hasPendingCredential: Boolean) {
        if (!hasPendingCredential && !PairingCode.isValid(_pairingCode.value)) return
        controller.connectToPeer(peer)
    }

    @Synchronized
    fun consumeConnectedEndpoint(): WifiDirectEndpoint? {
        if (handoffConsumed) return null
        val connected = controller.state.value as? WifiDirectState.Connected ?: return null
        handoffConsumed = true
        controller.handoffToListen()
        cleanupCoordinator.handoff(controller)
        return connected.endpoint
    }

    fun cancel() {
        if (handoffConsumed) cleanupCoordinator.cleanup() else controller.stop()
        handoffConsumed = false
        clearPairingCode()
    }

    fun leaveFlow() {
        clearPairingCode()
        if (!handoffConsumed) controller.stop()
    }

    override fun onCleared() {
        clearPairingCode()
        if (!handoffConsumed) controller.stop()
        super.onCleared()
    }
}
