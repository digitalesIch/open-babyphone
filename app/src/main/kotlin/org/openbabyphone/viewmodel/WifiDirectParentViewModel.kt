/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
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
import org.openbabyphone.WifiDirectController
import org.openbabyphone.WifiDirectState

data class WifiDirectParentUiState(
    val wifiDirectState: WifiDirectState = WifiDirectState.Idle,
    val wifiDirectSupported: Boolean = true,
    val pairingCode: String = ""
)

class WifiDirectParentViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = WifiDirectController(application)
    private val wifiDirectSupported = controller.isSupported()

    private val _pairingCode = MutableStateFlow("")

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
        _pairingCode.value = code
    }

    fun startDiscovery() {
        controller.startParentDiscovery()
    }

    fun connectToPeer(peer: org.openbabyphone.WifiDirectPeer) {
        controller.connectToPeer(peer)
    }

    fun stop() {
        controller.stop()
    }

    override fun onCleared() {
        super.onCleared()
        controller.stop()
    }
}