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
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.openbabyphone.BuildConfig
import org.openbabyphone.audio.FrameCodec
import org.openbabyphone.audio.FrameHeader
import org.openbabyphone.audio.JitterBuffer
import org.openbabyphone.service.ListenServiceRepository
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class ListenService : Service() {
    private val frequency: Int = AudioCodecDefines.FREQUENCY
    private val channelConfiguration: Int = AudioCodecDefines.CHANNEL_CONFIGURATION_OUT
    private val audioEncoding: Int = AudioCodecDefines.ENCODING
    private val bufferSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
    private val byteBufferSize = bufferSize * 2
    private val binder: IBinder = ListenBinder()
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private var listenThread: Thread? = null
    @Volatile private var currentSocket: Socket? = null
    @Volatile private var isRunning = false
    @Volatile private var hasAudioFocus = false
    val volumeHistory = VolumeHistory(16384)
    var childDeviceName: String? = null
        private set
    private var lastAddress: String? = null
    private var lastPort: Int = 0
    private var lastPairingCode: String? = null

    private val monitoringAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioFocusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(monitoringAudioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                Log.i(TAG, "Audio focus changed: $change")
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        this.audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createAlertNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId")
        createNotificationChannel()
        intent.extras?.let {
            val name = it.getString("name")
            childDeviceName = name
            ListenServiceRepository.updateChildDeviceName(name ?: "")
            val address = it.getString("address")
            val port = it.getInt("port")
            val pairingCode = it.getString("pairingCode")
            lastAddress = address
            lastPort = port
            lastPairingCode = pairingCode
            ListenServiceRepository.startConnecting(name ?: "", getString(R.string.connecting))
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Connecting to $address:$port")
            }
            val n = buildNotification(name, address, port, pairingCode)
            ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            stopListenThread()
            doListen(address, port, pairingCode)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopListenThread()
        abandonAudioFocus()
        ListenServiceRepository.updateConnected(false)
        notificationManager.cancel(ALERT_NOTIFICATION_ID)

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }

    private fun stopListenThread() {
        listenThread?.let { lt ->
            lt.interrupt()
            currentSocket?.let { socket ->
                try {
                    socket.close()
                } catch (e: IOException) {
                    Log.d(TAG, "Failed to close socket during stop", e)
                }
            }
            try {
                lt.join(1000)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Interrupted while waiting for listen thread to stop")
                Thread.currentThread().interrupt()
            }
        }
        listenThread = null
        currentSocket = null
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun buildNotification(name: String?, address: String?, port: Int, pairingCode: String?): Notification {
        val text = getText(R.string.listening)
        val listenUri = Uri.Builder()
            .scheme("quiet-engine")
            .authority("listen")
            .appendQueryParameter("address", address ?: "")
            .appendQueryParameter("port", port.toString())
            .appendQueryParameter("name", name ?: "")
            .appendQueryParameter("pairingCode", pairingCode ?: "")
            .appendQueryParameter("resumeOnly", "true")
            .build()
        val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(this@ListenService, "org.openbabyphone.MainActivity")
            data = listenUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            deepLinkIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.listening_notification)
            .setOngoing(true)
            .setTicker(text)
            .setContentTitle(text)
            .setContentText(name)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_service_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(serviceChannel)
    }

    private fun createAlertNotificationChannel() {
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.connection_lost_alert_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            enableLights(true)
            lightColor = android.graphics.Color.RED
        }
        notificationManager.createNotificationChannel(alertChannel)
    }

    private fun sendConnectionLostAlert() {
        val alertIntent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(this@ListenService, "org.openbabyphone.MainActivity")
            data = Uri.Builder()
                .scheme("quiet-engine")
                .authority("listen")
                .appendQueryParameter("address", lastAddress ?: "")
                .appendQueryParameter("port", lastPort.toString())
                .appendQueryParameter("name", childDeviceName ?: "")
                .appendQueryParameter("pairingCode", lastPairingCode ?: "")
                .appendQueryParameter("resumeOnly", "true")
                .build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            ALERT_REQUEST_CODE,
            alertIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.listening_notification)
            .setContentTitle(getString(R.string.connection_lost_alert_title))
            .setContentText(getString(R.string.connection_lost_alert_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    inner class ListenBinder : Binder() {
        val service: ListenService
            get() = this@ListenService
    }

    var onError: (() -> Unit)? = null
    var onUpdate: (() -> Unit)? = null
    var onStatusChange: ((String) -> Unit)? = null
    private var reconnectAttempts = 0
    private val jitterBuffer = JitterBuffer()

    private fun doListen(address: String?, port: Int, pairingCode: String?) {
        if (port !in VALID_PORT_RANGE) {
            Log.e(TAG, "Invalid socket port")
            ListenServiceRepository.updateError(getString(R.string.disconnected))
            playAlert()
            onError?.invoke()
            return
        }
        isRunning = true
        reconnectAttempts = 0
        val lt = Thread {
            var shouldReconnect: Boolean
            do {
                if (!isRunning) break
                try {
                    val socket = Socket()
                    currentSocket = socket
                    socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = SOCKET_READ_TIMEOUT_MS

                    val sessionInfo = performHandshake(socket, pairingCode)
                    if (sessionInfo == null) {
                        Log.e(TAG, "Handshake failed")
                        socket.close()
                        shouldReconnect = false
                    } else {
                        reconnectAttempts = 0
                        notificationManager.cancel(ALERT_NOTIFICATION_ID)
                        ListenServiceRepository.updateConnected(true, getString(R.string.listening))
                        val streamResult = streamAudio(socket, sessionInfo.key, sessionInfo.sessionId)
                        shouldReconnect = !streamResult
                    }

                    if (shouldReconnect && isRunning) {
                        reconnectAttempts++
                        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                            val status = getString(R.string.reconnecting_status, reconnectAttempts, MAX_RECONNECT_ATTEMPTS)
                            Log.i(TAG, status)
                            postStatus(status)
                            Thread.sleep(RECONNECT_DELAY_MS)
                        } else {
                            Log.e(TAG, "Max reconnect attempts reached")
                            ListenServiceRepository.updateError(getString(R.string.disconnected))
                            playAlert()
                            onError?.invoke()
                            shouldReconnect = false
                        }
                    }
                } catch (e: IOException) {
                    shouldReconnect = isRunning
                    if (shouldReconnect) {
                        reconnectAttempts++
                        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                            val status = getString(R.string.reconnecting_status, reconnectAttempts, MAX_RECONNECT_ATTEMPTS)
                            Log.w(TAG, "Connection error, $status", e)
                            postStatus(status)
                            try {
                                Thread.sleep(RECONNECT_DELAY_MS)
                            } catch (ie: InterruptedException) {
                                Thread.currentThread().interrupt()
                                shouldReconnect = false
                            }
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, "Error opening socket to $address on port $port", e)
                            } else {
                                Log.e(TAG, "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts")
                            }
                            ListenServiceRepository.updateError(getString(R.string.disconnected))
                            playAlert()
                            onError?.invoke()
                            shouldReconnect = false
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid socket parameters", e)
                    ListenServiceRepository.updateError(getString(R.string.disconnected))
                    playAlert()
                    onError?.invoke()
                    shouldReconnect = false
                }
            } while (shouldReconnect && isRunning)
        }
        this.listenThread = lt
        lt.start()
    }

    private data class SessionInfo(val sessionId: ByteArray, val key: ByteArray?)

    private fun performHandshake(socket: Socket, pairingCode: String?): SessionInfo? {
        return try {
            socket.soTimeout = AUTH_TIMEOUT_MS
            val handshake = Handshake.readHandshake(socket.getInputStream())
                ?: run {
                    Log.e(TAG, "Failed to read handshake from child device")
                    return null
                }
            if (!Handshake.isVersionSupported(handshake.protocolVersion)) {
                Log.e(TAG, "Child protocol version ${handshake.protocolVersion} not supported")
                return null
            }
            if ((handshake.capabilities and Handshake.CAP_G711_ULAW) == 0) {
                Log.e(TAG, "Child has no shared codec capability")
                return null
            }
            if (handshake.authRequired) {
                val code = pairingCode?.trim() ?: ""
                if (code.isEmpty()) {
                    Log.e(TAG, "Child requires pairing code but none provided")
                    return null
                }
                val key = CryptoHelper.deriveKey(code)
                val encryptedChallenge = CryptoHelper.encryptChallenge(handshake.challenge!!, key, handshake.authNonce!!)
                Handshake.writeAuthResponse(socket.getOutputStream(), encryptedChallenge)
                Handshake.writeCapabilityResponse(socket.getOutputStream())
                socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                SessionInfo(handshake.sessionId, key)
            } else {
                Handshake.writeCapabilityResponse(socket.getOutputStream())
                socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                SessionInfo(handshake.sessionId, null)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Handshake failed", e)
            null
        }
    }

    private fun postStatus(status: String) {
        ListenServiceRepository.updateStatus(status)
        onStatusChange?.let { callback ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(status)
            }
        }
    }

    private fun streamAudio(socket: Socket, key: ByteArray?, sessionId: ByteArray): Boolean {
        Log.i(TAG, "Setting up stream")
        requestAudioFocus()
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(monitoringAudioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(frequency)
                        .setChannelMask(channelConfiguration)
                        .setEncoding(audioEncoding)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "AudioTrack initialization failed - invalid parameters", e)
            return false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioTrack initialization failed", e)
            return false
        }

        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not initialized properly")
            audioTrack.release()
            return false
        }

        try {
            audioTrack.play()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start AudioTrack playback", e)
            audioTrack.release()
            return false
        }

        val inputStream = try {
            socket.getInputStream()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get input stream from socket", e)
            audioTrack.release()
            return false
        }

        var expectedSeqNum = 0
        var firstFrameReceived = false
        var lastFrameTime = System.currentTimeMillis()
        var streamDisruptedTime: Long? = null

        val streamRunning = AtomicBoolean(true)
        val playbackFailed = AtomicBoolean(false)
        val playbackThread = Thread {
            val decodedBuffer = ShortArray(byteBufferSize * 2)
            Log.i(TAG, "Starting playback from jitter buffer")
            try {
                while (streamRunning.get() && !Thread.currentThread().isInterrupted) {
                    val jitterFrame = jitterBuffer.getFrame(100) ?: continue
                    val decoded = AudioCodecDefines.CODEC.decode(decodedBuffer, jitterFrame.ulawData, jitterFrame.ulawData.size, 0)
                    if (decoded > 0) {
                        val written = audioTrack.write(decodedBuffer, 0, decoded)
                        if (written < 0) {
                            Log.e(TAG, "AudioTrack write error: $written")
                            playbackFailed.set(true)
                            streamRunning.set(false)
                            break
                        }
                        val decodedBytes = ShortArray(decoded)
                        System.arraycopy(decodedBuffer, 0, decodedBytes, 0, decoded)
                        volumeHistory.onAudioData(decodedBytes)
                        onUpdate?.invoke()
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Playback thread interrupted")
                Thread.currentThread().interrupt()
            }
        }

        try {
            playbackThread.start()
            var senderClockOffsetMs: Long? = null
            while (streamRunning.get() && !Thread.currentThread().isInterrupted) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastFrame = currentTime - lastFrameTime
                if (timeSinceLastFrame > STREAM_LOST_MS) {
                    Log.e(TAG, "Stream lost - no frames for ${timeSinceLastFrame}ms")
                    return false
                }
                if (timeSinceLastFrame > STREAM_DISRUPTED_MS) {
                    if (streamDisruptedTime == null) {
                        streamDisruptedTime = currentTime
                        Log.w(TAG, "Stream disrupted - no frames for ${timeSinceLastFrame}ms")
                        postStatus(getString(R.string.connection_disrupted))
                    }
                } else {
                    streamDisruptedTime = null
                }

                val header = try {
                    FrameHeader.readFrom(inputStream)
                } catch (e: SocketTimeoutException) {
                    continue
                } ?: run {
                    Log.e(TAG, "Failed to read frame header")
                    return false
                }
                lastFrameTime = System.currentTimeMillis()

                if (!firstFrameReceived) {
                    expectedSeqNum = header.seqNum + 1
                    firstFrameReceived = true
                } else if (header.seqNum != expectedSeqNum) {
                    Log.w(TAG, "Frame gap: expected $expectedSeqNum, got ${header.seqNum}")
                    expectedSeqNum = header.seqNum + 1
                } else {
                    expectedSeqNum++
                }

                if (header.flags == FrameCodec.FLAG_HEARTBEAT) {
                    Log.d(TAG, "Received heartbeat frame")
                    continue
                }

                val payload = ByteArray(header.payloadLength)
                var bytesRead = 0
                while (bytesRead < header.payloadLength) {
                    val chunk = inputStream.read(payload, bytesRead, header.payloadLength - bytesRead)
                    if (chunk < 0) {
                        Log.e(TAG, "Incomplete payload read")
                        return false
                    }
                    bytesRead += chunk
                }

                val frame = FrameCodec.decodeFrame(header, payload, key, sessionId)
                    ?: run {
                        Log.e(TAG, "Failed to decode frame")
                        return false
                    }

                val receiveTime = System.currentTimeMillis()
                val offset = senderClockOffsetMs ?: (receiveTime - frame.timestampMs).also {
                    senderClockOffsetMs = it
                }
                val frameAge = receiveTime - (offset + frame.timestampMs)
                if (frameAge > AudioCodecDefines.MAX_FRAME_AGE_MS) {
                    Log.d(TAG, "Dropping stale frame: ${frameAge}ms old")
                    continue
                }

                jitterBuffer.addFrame(JitterBuffer.DecodedFrame(frame.seqNum, frame.timestampMs, frame.ulawData))
            }

            return !playbackFailed.get()
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            return false
        } finally {
            streamRunning.set(false)
            playbackThread.interrupt()
            try {
                playbackThread.join(1000)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Interrupted while waiting for playback thread to stop")
                Thread.currentThread().interrupt()
            }
            jitterBuffer.clear()
            try {
                audioTrack.stop()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "AudioTrack already stopped")
            }
            audioTrack.release()
            try {
                socket.close()
            } catch (e: IOException) {
                Log.d(TAG, "Failed to close socket", e)
            }
        }
    }

    private fun playAlert() {
        sendConnectionLostAlert()
        requestAudioFocus()
        val mp = MediaPlayer.create(this, R.raw.upward_beep_chromatic_fifths, monitoringAudioAttributes, 0)
        if (mp != null) {
            Log.i(TAG, "Playing alert")
            mp.setOnCompletionListener { obj: MediaPlayer -> obj.release() }
            mp.start()
        } else {
            Log.e(TAG, "Failed to play alert")
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) {
            Log.w(TAG, "Audio focus request was not granted: $result")
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        hasAudioFocus = false
    }

    companion object {
        private const val TAG = "ListenService"
        const val CHANNEL_ID = TAG
        const val ALERT_CHANNEL_ID = "connection_lost_alert"
        const val ID = 902938409
        private const val ALERT_NOTIFICATION_ID = 902938410
        private const val ALERT_REQUEST_CODE = 1
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 2000L
        private const val SOCKET_READ_TIMEOUT_MS = 1000
        private const val AUTH_TIMEOUT_MS = 10_000
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val STREAM_DISRUPTED_MS = 5000L
        private const val STREAM_LOST_MS = 10000L
        private val VALID_PORT_RANGE = 1..65535
    }
}
