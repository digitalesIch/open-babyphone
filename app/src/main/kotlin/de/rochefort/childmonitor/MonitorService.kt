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
package de.rochefort.childmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.rochefort.childmonitor.audio.FrameCodec
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class MonitorService : Service() {
    private val binder: IBinder = MonitorBinder()
    private lateinit var nsdManager: NsdManager
    private var registrationListener: RegistrationListener? = null
    private var currentSocket: ServerSocket? = null
    private var connectionToken: Any? = null
    private var currentPort = 0
    private lateinit var notificationManager: NotificationManager
    private var monitorThread: Thread? = null
    private var audioProducerThread: Thread? = null
    private val clientManager = ClientManager()
    @Volatile private var isStreaming = false
    var monitorActivity: MonitorActivity? = null

    private var pairingCodeSnapshot: String = ""
    private var streamSessionId: ByteArray? = null
    private var streamKey: ByteArray? = null

    private val pairingCode: String
        get() = pairingCodeSnapshot

    fun updateMonitorActivity() {
        val ma = this.monitorActivity ?: return
        ma.runOnUiThread {
            val pairingCodeText = ma.findViewById<TextView>(R.id.pairingCodeField)
            pairingCodeText.text = pairingCode
            val clientCountText = ma.findViewById<TextView>(R.id.clientCount)
            clientCountText.text = ma.getString(R.string.connected_clients, clientManager.getClientCount())
        }
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
        
        updateMonitorActivity()
        
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
        val ma = this.monitorActivity
        ma?.runOnUiThread {
            val statusText = ma.findViewById<TextView>(R.id.textStatus)
            statusText.setText(R.string.streaming)
        }
        
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
                AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        frequency,
                        channelConfiguration,
                        audioEncoding,
                        bufferSize
                )
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
                    val encoded: Int = AudioCodecDefines.CODEC.encode(pcmBuffer, read, ulawBuffer, 0)
                    
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
        Log.i(TAG, "ChildMonitor start")
        super.onCreate()
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        this.nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
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
        ensureMonitorThread()
        return START_NOT_STICKY
    }

    private fun ensureMonitorThread() {
        var mt = this.monitorThread
        if (mt != null && mt.isAlive) {
            return
        }
        streamSessionId = CryptoHelper.generateSessionId()
        streamKey = if (pairingCodeSnapshot.isNotEmpty()) {
            CryptoHelper.deriveKey(pairingCodeSnapshot)
        } else {
            null
        }
        val currentToken = Any()
        this.connectionToken = currentToken
        mt = Thread {
            while (this.connectionToken == currentToken) {
                try {
                    ServerSocket(this.currentPort).use { serverSocket ->
                        this.currentSocket = serverSocket
                        // Store the chosen port.
                        val localPort = serverSocket.localPort

                        // Register the service so that parent devices can
                        // locate the child device
                        registerService(localPort)
                        
                        // Start audio producer once we have a server socket
                        startAudioProducer()
                        
                        // Accept multiple clients until max reached or connection token changes
                        while (clientManager.canAcceptMoreClients() &&
                                this.connectionToken == currentToken &&
                                !Thread.currentThread().isInterrupted) {
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
                        
                        if (!clientManager.canAcceptMoreClients()) {
                            Log.i(TAG, "Max clients reached, waiting for disconnect")
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
        serviceInfo.serviceName = "ChildMonitor on " + Build.MODEL
        serviceInfo.serviceType = "_childmonitor._tcp."
        serviceInfo.port = port
        this.registrationListener = object : RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                nsdServiceInfo.serviceName.let { serviceName ->
                    Log.i(TAG, "Service name: $serviceName")
                    monitorActivity?.let { ma ->
                        ma.runOnUiThread {
                            val statusText = ma.findViewById<TextView>(R.id.textStatus)
                            statusText.setText(R.string.waitingForParent)
                            val serviceText = ma.findViewById<TextView>(R.id.textService)
                            serviceText.text = serviceName
                            val portText = ma.findViewById<TextView>(R.id.port)
                            portText.text = port.toString()
                            val pairingCodeText = ma.findViewById<TextView>(R.id.pairingCodeField)
                            pairingCodeText.text = pairingCode
                        }
                    }
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Registration failed!  Put debugging code here to determine why.
                Log.e(TAG, "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.i(TAG, "Unregistering service")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Unregistration failed.  Put debugging code here to determine why.
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }
        nsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
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
        // Set the info for the views that show in the notification panel.
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
        b.setSmallIcon(R.drawable.listening_notification) // the status icon
                .setOngoing(true)
                .setTicker(text) // the status text
                .setContentTitle(text) // the label of the entry
        return b.build()
    }

    override fun onDestroy() {
        // Stop audio producer
        isStreaming = false
        this.audioProducerThread?.let {
            it.interrupt()
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Interrupted while waiting for audio producer to stop")
            }
        }
        this.audioProducerThread = null
        
        // Disconnect all clients
        clientManager.removeAllClients()
        
        // Stop monitor thread
        this.monitorThread?.let {
            this.monitorThread = null
            it.interrupt()
        }
        unregisterService()
        this.connectionToken = null
        this.currentSocket?.let {
            try {
                it.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to close active socket on port $currentPort")
            }
        }
        this.currentSocket = null

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Tell the user we stopped.
        Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

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
