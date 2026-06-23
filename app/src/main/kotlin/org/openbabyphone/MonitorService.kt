/*
 * This file is part of Child Monitor.
 *
 * Child Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Child Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Child Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.openbabyphone.audio.FrameCodec
import org.openbabyphone.service.MonitorServiceRepository
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.locks.ReentrantLock

class MonitorService : Service() {
    private val binder: IBinder = MonitorBinder()
    private lateinit var nsdManager: NsdManager
    private lateinit var connectivityManager: ConnectivityManager
    private var registrationListener: RegistrationListener? = null
    private var currentSocket: ServerSocket? = null
    private var connectionToken: Any? = null
    private var currentPort = 0
    private lateinit var notificationManager: NotificationManager
    private var monitorThread: Thread? = null
    private var audioProducerThread: Thread? = null
    private val clientManager = ClientManager()
    @Volatile private var isStreaming = false
    private val capacityLock = ReentrantLock()
    private val capacityCondition = capacityLock.newCondition()

    private var pairingCodeSnapshot: String = ""
    private var streamSessionId: ByteArray? = null
    private var streamKey: ByteArray? = null

    private val pairingCode: String
        get() = pairingCodeSnapshot

    private val listenAddresses: List<String>
        get() {
            val addresses: MutableList<String> = ArrayList()
            try {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val networkInfo = connectivityManager.getNetworkInfo(network)
                    if (networkInfo?.isConnected == true) {
                        val linkProperties = connectivityManager.getLinkProperties(network)
                        linkProperties?.linkAddresses?.forEach { linkAddress ->
                            val address = linkAddress.address
                            val hostAddress = address.hostAddress
                            if (!address.isLinkLocalAddress && !address.isLoopbackAddress && hostAddress != null) {
                                addresses.add("$hostAddress (${networkInfo.typeName})")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get network addresses", e)
            }
            return addresses
        }

    private fun authenticateParent(socket: Socket): Boolean {
        val sessionId = streamSessionId ?: return false
        val key = streamKey
        val authRequired = key != null
        return try {
            socket.soTimeout = AUTH_TIMEOUT_MS
            val challenge = if (authRequired) CryptoHelper.generateChallenge() else null
            Handshake.writeHandshake(socket.getOutputStream(), sessionId, authRequired, challenge)
            if (!authRequired) {
                return true
            }
            val encryptedChallenge = Handshake.readAuthResponse(socket.getInputStream())
                ?: run {
                    Log.w(TAG, "Parent did not send auth response")
                    return false
                }
            val verified = CryptoHelper.verifyChallenge(encryptedChallenge, challenge!!, key!!, sessionId)
            if (!verified) {
                Log.w(TAG, "Rejected parent connection with invalid pairing code")
            }
            verified
        } catch (e: IOException) {
            Log.w(TAG, "Failed to authenticate parent connection", e)
            false
        } finally {
            try {
                socket.soTimeout = 0
            } catch (e: IOException) {
                Log.d(TAG, "Failed to reset socket timeout after authentication", e)
            }
        }
    }

    private fun handleClient(socket: Socket): Boolean {
        if (!authenticateParent(socket)) {
            Log.w(TAG, "Client authentication failed")
            return false
        }

        val client = clientManager.addClient(socket, pairingCode)
        if (client == null) {
            Log.w(TAG, "Rejected client - max clients reached")
            socket.close()
            return false
        }

        MonitorServiceRepository.updateStatus(getString(R.string.connected_clients, clientManager.getClientCount()))

        if (!clientManager.canAcceptMoreClients()) {
            unregisterService()
            Log.i(TAG, "Max clients reached, unregistering NSD")
        }

        return true
    }

    private fun startAudioProducer() {
        if (isStreaming) {
            Log.w(TAG, "Audio producer already running")
            return
        }

        val sessionId = streamSessionId ?: run {
            Log.e(TAG, "Cannot start audio producer without session ID")
            return
        }
        val key = streamKey

        isStreaming = true
        MonitorServiceRepository.updateStatus(getString(R.string.streaming))

        audioProducerThread = Thread {
            val frequency: Int = AudioCodecDefines.FREQUENCY
            val channelConfiguration: Int = AudioCodecDefines.CHANNEL_CONFIGURATION_IN
            val audioEncoding: Int = AudioCodecDefines.ENCODING
            val bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)

            if (bufferSize <= 0) {
                Log.e(TAG, "Invalid audio buffer size: $bufferSize")
                isStreaming = false
                return@Thread
            }

            val audioRecord: AudioRecord = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize)
            } catch (e: SecurityException) {
                Log.e(TAG, "AudioRecord permission denied", e)
                isStreaming = false
                return@Thread
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioRecord initialization failed", e)
                isStreaming = false
                return@Thread
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly")
                audioRecord.release()
                isStreaming = false
                return@Thread
            }

            val pcmBufferSize = bufferSize * 2
            val pcmBuffer = ShortArray(pcmBufferSize)
            val ulawBuffer = ByteArray(pcmBufferSize)
            var seqNum = 0
            val sessionStartTime = System.currentTimeMillis()
            var lastHeartbeatTime = 0L

            try {
                audioRecord.startRecording()
                while (isStreaming && !Thread.currentThread().isInterrupted) {
                    val read = audioRecord.read(pcmBuffer, 0, bufferSize)
                    if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                    val encoded = AudioCodecDefines.CODEC.encode(pcmBuffer, read, ulawBuffer, 0)
                    val timestampMs = (System.currentTimeMillis() - sessionStartTime).toInt()
                    val frame = FrameCodec.encodeFrame(ulawBuffer.copyOf(encoded), seqNum++, timestampMs, key, sessionId)
                    clientManager.broadcastFrame(frame)

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHeartbeatTime >= AudioCodecDefines.HEARTBEAT_INTERVAL_MS) {
                        val heartbeat = FrameCodec.encodeHeartbeat(seqNum++, timestampMs)
                        clientManager.broadcastFrame(heartbeat)
                        lastHeartbeatTime = currentTime
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio producer failed", e)
            } finally {
                try {
                    audioRecord.stop()
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "AudioRecord already stopped")
                }
                audioRecord.release()
                isStreaming = false
            }
        }
        audioProducerThread?.start()
        Log.i(TAG, "Audio producer started")
    }

    override fun onCreate() {
        Log.i(TAG, "Open Babyphone start")
        super.onCreate()
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        this.nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
        this.connectivityManager = this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        this.currentPort = 10000
        this.currentSocket = null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        pairingCodeSnapshot = getSharedPreferences(PAIRING_PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_KEY_PAIRING_CODE, "") ?: ""
        createNotificationChannel()
        val n = buildNotification()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, ID, n, foregroundServiceType)
        clientManager.setClientCountListener { count ->
            MonitorServiceRepository.updateStatus(getString(R.string.connected_clients, count))
            if (clientManager.canAcceptMoreClients() && registrationListener == null && connectionToken != null) {
                Log.i(TAG, "Capacity available again, re-registering NSD")
                currentSocket?.localPort?.let { registerService(it) }
            }
            capacityLock.lock()
            try {
                capacityCondition.signalAll()
            } finally {
                capacityLock.unlock()
            }
        }
        ensureMonitorThread()
        return START_NOT_STICKY
    }

    private fun ensureMonitorThread() {
        var mt = this.monitorThread
        if (mt != null && mt.isAlive) {
            return
        }
        streamSessionId = CryptoHelper.generateSessionId()
        streamKey = if (pairingCodeSnapshot.isNotEmpty()) CryptoHelper.deriveKey(pairingCodeSnapshot) else null
        val currentToken = Any()
        this.connectionToken = currentToken
        mt = Thread {
            while (this.connectionToken == currentToken) {
                try {
                    ServerSocket(this.currentPort).use { serverSocket ->
                        this.currentSocket = serverSocket
                        val localPort = serverSocket.localPort
                        registerService(localPort)
                        startAudioProducer()

                        while (this.connectionToken == currentToken && !Thread.currentThread().isInterrupted) {
                            if (!clientManager.canAcceptMoreClients()) {
                                Log.i(TAG, "Max clients reached, waiting for disconnect")
                                capacityLock.lock()
                                try {
                                    while (!clientManager.canAcceptMoreClients() && this.connectionToken == currentToken && !Thread.currentThread().isInterrupted) {
                                        capacityCondition.await()
                                    }
                                } finally {
                                    capacityLock.unlock()
                                }
                                if (this.connectionToken != currentToken || Thread.currentThread().isInterrupted) {
                                    break
                                }
                            }

                            val socket = serverSocket.accept()
                            Log.i(TAG, "Connection from parent device received")
                            if (!handleClient(socket)) {
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.d(TAG, "Failed to close rejected parent socket", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (this.connectionToken == currentToken) {
                        this.currentPort++
                        Log.e(TAG, "Failed to open server socket. Port increased to $currentPort", e)
                    }
                }
            }
        }
        this.monitorThread = mt
        mt.start()
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = "Open Babyphone"
        serviceInfo.serviceType = "_childmonitor._tcp."
        serviceInfo.port = port
        this.registrationListener = object : RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                nsdServiceInfo.serviceName.let { serviceName ->
                    Log.i(TAG, "Service name: $serviceName")
                    MonitorServiceRepository.updateServiceInfo(serviceName, port, listenAddresses)
                    MonitorServiceRepository.updateStatus(getString(R.string.waitingForParent))
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(TAG, "Unregistering service")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun unregisterService() {
        this.registrationListener?.let {
            this.registrationListener = null
            Log.i(TAG, "Unregistering monitoring service")
            this.nsdManager.unregisterService(it)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_service_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            this.notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        val text: CharSequence = getText(R.string.childDevice)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.listening_notification)
            .setOngoing(true)
            .setTicker(text)
            .setContentTitle(text)
            .build()
    }

    override fun onDestroy() {
        isStreaming = false
        clientManager.setClientCountListener(null)
        this.audioProducerThread?.let {
            it.interrupt()
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Interrupted while waiting for audio producer to stop")
                Thread.currentThread().interrupt()
            }
        }
        this.audioProducerThread = null
        clientManager.removeAllClients()

        this.connectionToken = null
        capacityLock.lock()
        try {
            capacityCondition.signalAll()
        } finally {
            capacityLock.unlock()
        }
        this.monitorThread?.let {
            this.monitorThread = null
            it.interrupt()
        }
        unregisterService()
        this.currentSocket?.let {
            try {
                it.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to close active socket on port $currentPort")
            }
        }
        this.currentSocket = null
        MonitorServiceRepository.updateStatus(getString(R.string.stopped))

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder

    inner class MonitorBinder : Binder() {
        val service: MonitorService
            get() = this@MonitorService
    }

    companion object {
        const val TAG = "MonitorService"
        const val CHANNEL_ID = TAG
        const val ID = 1338
        const val PAIRING_PREFS_NAME = "pairing"
        const val PREF_KEY_PAIRING_CODE = "pairingCode"
        private const val AUTH_TIMEOUT_MS = 10_000
    }
}
