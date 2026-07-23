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
package org.openbabyphone

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A discovered Open Babyphone child advertised via Wi-Fi Direct service
 * discovery.
 */
data class WifiDirectPeer(
    val deviceAddress: String,
    val deviceName: String,
    val port: Int,
    val displayName: String
)

/**
 * Wi-Fi Direct connection endpoint information delivered to the parent after a
 * successful P2P connection.
 */
data class WifiDirectEndpoint(
    val host: String,
    val port: Int,
    val name: String
)

internal fun upsertWifiDirectPeer(
    peers: List<WifiDirectPeer>,
    peer: WifiDirectPeer
): List<WifiDirectPeer> {
    val index = peers.indexOfFirst { it.deviceAddress == peer.deviceAddress }
    if (index < 0) return peers + peer
    return peers.toMutableList().also { it[index] = peer }
}

/**
 * State machine for the Wi-Fi Direct optional connection mode.
 */
sealed interface WifiDirectState {
    /**
     * Wi-Fi Direct is idle.
     */
    data object Idle : WifiDirectState

    /**
     * Wi-Fi Direct is starting (creating a group on the child, or starting
     * discovery on the parent).
     */
    data object Starting : WifiDirectState

    /**
     * The child is advertising and waiting for a parent to connect.
     */
    data object Advertising : WifiDirectState

    /**
     * The parent is discovering nearby Open Babyphone children.
     */
    data class Discovering(val peers: List<WifiDirectPeer>) : WifiDirectState

    /**
     * The parent is connecting to a selected child.
     */
    data class Connecting(val peer: WifiDirectPeer) : WifiDirectState

    /**
     * The parent has a usable endpoint and can hand off to the audio stream.
     */
    data class Connected(val endpoint: WifiDirectEndpoint) : WifiDirectState

    /**
     * An error occurred. The caller may retry or fall back to other modes.
     */
    data class Error(val message: String) : WifiDirectState
}

interface WifiDirectSession {
    val state: StateFlow<WifiDirectState>
    fun isSupported(): Boolean
    fun startChildAdvertising(port: Int, name: String)
    fun startParentDiscovery()
    fun connectToPeer(peer: WifiDirectPeer)
    fun handoffToListen()
    fun stop()
}

internal class WifiDirectCallbackGate {
    private var generation = 0L
    private var connectedClaimed = false

    fun begin(): Long {
        generation++
        connectedClaimed = false
        return generation
    }

    fun cancel() {
        generation++
        connectedClaimed = false
    }

    fun currentToken(): Long = generation

    fun isCurrent(token: Long): Boolean = token == generation

    fun claimConnected(token: Long): Boolean {
        if (!isCurrent(token) || connectedClaimed) return false
        connectedClaimed = true
        return true
    }
}

/**
 * Maps a [WifiP2pManager] error code to a human-readable reason.
 */
object WifiDirectErrors {
    fun describe(reasonCode: Int): String = when (reasonCode) {
        P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device"
        WifiP2pManager.BUSY -> "Wi-Fi Direct is busy, try again"
        WifiP2pManager.ERROR -> "Wi-Fi Direct operation failed"
        else -> "Wi-Fi Direct error ($reasonCode)"
    }
}

/**
 * Parses Wi-Fi Direct service TXT records into a [WifiDirectPeer].
 *
 * Extracted as a pure function so it can be unit tested without Android.
 */
object WifiDirectTxtRecordParser {

    /**
     * Parses a TXT record map into a [WifiDirectPeer], returning `null` if the
     * record does not advertise Open Babyphone or lacks a usable port.
     */
    fun parse(
        deviceAddress: String,
        deviceName: String,
        record: Map<String, String>
    ): WifiDirectPeer? {
        if (record[ConnectionConstants.WIFI_DIRECT_TXT_APP] !=
            ConnectionConstants.WIFI_DIRECT_TXT_APP_VALUE
        ) {
            return null
        }
        val port = record[ConnectionConstants.WIFI_DIRECT_TXT_PORT]?.toIntOrNull()
            ?: return null
        if (port !in 1..65535) return null
        val displayName = record[ConnectionConstants.WIFI_DIRECT_TXT_NAME]
            ?: deviceName
        return WifiDirectPeer(deviceAddress, deviceName, port, displayName)
    }
}

/**
 * Controller that wraps [WifiP2pManager] for the optional Wi-Fi Direct
 * connection mode.
 *
 * It exposes a [state] flow consumed by the ViewModels and handles the child
 * (group owner + local service) and parent (service discovery + connect) roles.
 */
class WifiDirectController(private val context: Context) : WifiDirectSession {

    private val _state = MutableStateFlow<WifiDirectState>(WifiDirectState.Idle)
    override val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private var manager: WifiP2pManager? = null
    private var channel: Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    private var pendingEndpointPort: Int = ConnectionConstants.DEFAULT_PORT
    private var pendingEndpointName: String = ""
    private val callbackGate = WifiDirectCallbackGate()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    /**
     * Returns `true` if the device declares Wi-Fi Direct support and the
     * framework service is available.
     */
    override fun isSupported(): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.wifi.direct") &&
            context.getSystemService(Context.WIFI_P2P_SERVICE) is WifiP2pManager
    }

    private fun ensureManager() {
        if (manager != null && channel != null) return
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context.mainLooper, null)
    }

    private fun registerReceiver(token: Long) {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isCurrent(token)) return
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION ->
                        handleConnectionChanged(intent, token)
                }
            }
        }
        context.registerReceiver(receiver, intentFilter)
    }

    private fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Log.d(TAG, "Receiver was not registered", e)
            }
        }
        receiver = null
    }

    /**
     * Child role: create a Wi-Fi Direct group and advertise the Open Babyphone
     * service so parents can discover and connect.
     */
    @SuppressLint("MissingPermission")
    override fun startChildAdvertising(port: Int, name: String) {
        val token = beginOperation()
        if (!isSupported()) {
            emitIfCurrent(token, WifiDirectState.Error(WifiDirectErrors.describe(P2P_UNSUPPORTED)))
            return
        }
        ensureManager()
        registerReceiver(token)
        _state.value = WifiDirectState.Starting
        pendingEndpointPort = port
        pendingEndpointName = name

        val m = manager ?: run {
            emitIfCurrent(token, WifiDirectState.Error("Wi-Fi Direct is not available"))
            return
        }
        val c = channel ?: run {
            emitIfCurrent(token, WifiDirectState.Error("Wi-Fi Direct is not available"))
            return
        }

        val record = mapOf(
            ConnectionConstants.WIFI_DIRECT_TXT_APP to ConnectionConstants.WIFI_DIRECT_TXT_APP_VALUE,
            ConnectionConstants.WIFI_DIRECT_TXT_PORT to port.toString(),
            ConnectionConstants.WIFI_DIRECT_TXT_NAME to name
        )
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            "openbabyphone",
            ConnectionConstants.WIFI_DIRECT_SERVICE_TYPE,
            record
        )
        m.addLocalService(c, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (!isCurrent(token)) return
                m.createGroup(c, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        emitIfCurrent(token, WifiDirectState.Advertising)
                    }

                    override fun onFailure(reason: Int) {
                        emitIfCurrent(token, WifiDirectState.Error(WifiDirectErrors.describe(reason)))
                    }
                })
            }

            override fun onFailure(reason: Int) {
                emitIfCurrent(token, WifiDirectState.Error(WifiDirectErrors.describe(reason)))
            }
        })
    }

    /**
     * Parent role: start Wi-Fi Direct service discovery for Open Babyphone
     * children.
     */
    @SuppressLint("MissingPermission")
    override fun startParentDiscovery() {
        val token = beginOperation()
        if (!isSupported()) {
            emitIfCurrent(token, WifiDirectState.Error(WifiDirectErrors.describe(P2P_UNSUPPORTED)))
            return
        }
        ensureManager()
        registerReceiver(token)
        _state.value = WifiDirectState.Starting

        val m = manager ?: run {
            emitIfCurrent(token, WifiDirectState.Error("Wi-Fi Direct is not available"))
            return
        }
        val c = channel ?: run {
            emitIfCurrent(token, WifiDirectState.Error("Wi-Fi Direct is not available"))
            return
        }

        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, device ->
            if (!isCurrent(token)) return@DnsSdTxtRecordListener
            val peer = WifiDirectTxtRecordParser.parse(
                device.deviceAddress,
                device.deviceName,
                record
            )
            if (peer != null) {
                addPeer(peer, token)
            }
        }
        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { _, _, device ->
            if (!isCurrent(token)) return@DnsSdServiceResponseListener
            val current = (_state.value as? WifiDirectState.Discovering)?.peers.orEmpty()
            val peer = current.firstOrNull { it.deviceAddress == device.deviceAddress }
            if (peer != null) {
                _state.value = WifiDirectState.Discovering(current)
            }
        }
        m.setDnsSdResponseListeners(c, serviceListener, txtListener)

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        m.addServiceRequest(c, serviceRequest!!, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (!isCurrent(token)) return
                m.discoverServices(c, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (isDiscoveryState(token)) {
                            _state.value = WifiDirectState.Discovering(discoveredPeers.toList())
                        }
                    }

                    override fun onFailure(reason: Int) {
                        emitDiscoveryError(token, reason)
                    }
                })
            }

            override fun onFailure(reason: Int) {
                emitDiscoveryError(token, reason)
            }
        })
    }

    private val discoveredPeers: MutableList<WifiDirectPeer> = mutableListOf()

    @Synchronized
    private fun addPeer(peer: WifiDirectPeer, token: Long) {
        if (!isCurrent(token)) return
        if (_state.value != WifiDirectState.Starting &&
            _state.value !is WifiDirectState.Discovering
        ) {
            return
        }
        val updated = upsertWifiDirectPeer(discoveredPeers, peer)
        discoveredPeers.clear()
        discoveredPeers.addAll(updated)
        emitIfCurrent(token, WifiDirectState.Discovering(discoveredPeers.toList()))
    }

    /**
     * Parent role: connect to the given peer. After the connection is
     * established, [state] transitions to [WifiDirectState.Connected] with the
     * group owner address and advertised port.
     */
    @SuppressLint("MissingPermission")
    override fun connectToPeer(peer: WifiDirectPeer) {
        val token = callbackGate.currentToken()
        if (!isCurrent(token) || _state.value !is WifiDirectState.Discovering) return
        val m = manager ?: return
        val c = channel ?: return
        pendingEndpointPort = peer.port
        pendingEndpointName = peer.displayName
        _state.value = WifiDirectState.Connecting(peer)
        val config = android.net.wifi.p2p.WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
        }
        m.connect(c, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (!isCurrent(token)) return
            }

            override fun onFailure(reason: Int) {
                if (isCurrent(token) && _state.value is WifiDirectState.Connecting) {
                    _state.value = WifiDirectState.Error(WifiDirectErrors.describe(reason))
                }
            }
        })
    }

    private fun handleConnectionChanged(intent: Intent, token: Long) {
        if (!isCurrent(token)) return
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                WifiP2pInfo::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO) as? WifiP2pInfo
        }
        if (info != null && info.groupFormed) {
            if (info.isGroupOwner) {
                // Child side: group created, we are the group owner and are
                // advertising. Stay in Advertising so the UI shows the stop
                // button and waiting status.
                if (_state.value !is WifiDirectState.Advertising) {
                    emitIfCurrent(token, WifiDirectState.Advertising)
                }
            } else {
                // Parent side: connected to the child's group, the group
                // owner address is the child's address.
                val host = info.groupOwnerAddress?.hostAddress ?: return
                val endpoint = WifiDirectEndpoint(host, pendingEndpointPort, pendingEndpointName)
                if (callbackGate.claimConnected(token)) {
                    emitIfCurrent(token, WifiDirectState.Connected(endpoint))
                }
            }
        } else if (_state.value is WifiDirectState.Connecting ||
            _state.value is WifiDirectState.Connected ||
            _state.value == WifiDirectState.Advertising
        ) {
            emitIfCurrent(token, WifiDirectState.Error("Wi-Fi Direct connection was lost"))
        }
    }

    /**
     * Stop any active Wi-Fi Direct operation and clean up.
     */
    override fun handoffToListen() {
        removeDiscoveryResources()
        unregisterReceiver()
    }

    override fun stop() {
        callbackGate.cancel()
        cleanupFrameworkState()
        discoveredPeers.clear()
        _state.value = WifiDirectState.Idle
    }

    private fun beginOperation(): Long {
        val token = callbackGate.begin()
        cleanupFrameworkState()
        discoveredPeers.clear()
        return token
    }

    private fun isCurrent(token: Long): Boolean = callbackGate.isCurrent(token)

    private fun emitIfCurrent(token: Long, value: WifiDirectState) {
        if (isCurrent(token)) _state.value = value
    }

    private fun isDiscoveryState(token: Long): Boolean = isCurrent(token) &&
        (_state.value == WifiDirectState.Starting || _state.value is WifiDirectState.Discovering)

    private fun emitDiscoveryError(token: Long, reason: Int) {
        if (isDiscoveryState(token)) {
            _state.value = WifiDirectState.Error(WifiDirectErrors.describe(reason))
        }
    }

    private fun removeDiscoveryResources() {
        val m = manager
        val c = channel
        if (m != null && c != null) {
            serviceRequest?.let { req ->
                try {
                    m.removeServiceRequest(c, req, null)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to remove service request", e)
                }
            }
            serviceRequest = null
            try {
                m.stopPeerDiscovery(c, null)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to stop peer discovery", e)
            }
        }
    }

    private fun cleanupFrameworkState() {
        removeDiscoveryResources()
        val m = manager
        val c = channel
        if (m != null && c != null) {
            try {
                m.cancelConnect(c, null)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to cancel connection", e)
            }
            try {
                m.clearLocalServices(c, null)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to clear local services", e)
            }
            try {
                m.removeGroup(c, null)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to remove group", e)
            }
        }
        serviceRequest = null
        unregisterReceiver()
    }

    companion object {
        private const val TAG = "WifiDirectController"
    }
}
