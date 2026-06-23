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
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
    private var listenThread: Thread? = null
    @Volatile private var currentSocket: Socket? = null
    @Volatile private var isRunning = false
    val volumeHistory = VolumeHistory(16384)
    var childDeviceName: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        createNotificationChannel()
        intent.extras?.let {
            val name = it.getString("name")
            childDeviceName = name
            ListenServiceRepository.updateChildDeviceName(name ?: "")
            val address = it.getString("address")
            val port = it.getInt("port")
            val pairingCode = it.getString("pairingCode")
            ListenServiceRepository.startConnecting(name ?: "")
            val n = buildNotification(name, address, port, pairingCode)
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, ID, n, foregroundServiceType)
            stopListenThread()
            doListen(address, port, pairingCode)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopListenThread()
        ListenServiceRepository.updateConnected(false)

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_service_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
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
            ListenServiceRepository.updateError()
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
                        ListenServiceRepository.updateConnected(true)
                        val streamResult = streamAudio(socket, sessionInfo.key, sessionInfo.sessionId)
                        shouldReconnect = !streamResult
                    }

                    if (shouldReconnect && isRunning) {
                        reconnectAttempts++
                        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                            val status = "Reconnecting ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)..."
                            Log.i(TAG, status)
                            postStatus(status)
                            Thread.sleep(RECONNECT_DELAY_MS)
                        } else {
                            Log.e(TAG, "Max reconnect attempts reached")
                            ListenServiceRepository.updateError()
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
                            val status = "Reconnecting ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)..."
                            Log.w(TAG, "Connection error, $status", e)
                            postStatus(status)
                            try {
                                Thread.sleep(RECONNECT_DELAY_MS)
                            } catch (ie: InterruptedException) {
                                Thread.currentThread().interrupt()
                                shouldReconnect = false
                            }
                        } else {
                            Log.e(TAG, "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts", e)
                            ListenServiceRepository.updateError()
                            playAlert()
                            onError?.invoke()
                            shouldReconnect = false
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid socket parameters", e)
                    ListenServiceRepository.updateError()
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
            if (handshake.authRequired) {
                val code = pairingCode?.trim() ?: ""
                if (code.isEmpty()) {
                    Log.e(TAG, "Child requires pairing code but none provided")
                    return null
                }
                val key = CryptoHelper.deriveKey(code)
                val encryptedChallenge = CryptoHelper.encryptChallenge(handshake.challenge!!, key, handshake.authNonce!!)
                Handshake.writeAuthResponse(socket.getOutputStream(), encryptedChallenge)
                socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                SessionInfo(handshake.sessionId, key)
            } else {
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
        val audioTrack = try {
            AudioTrack(AudioManager.STREAM_MUSIC, frequency, channelConfiguration, audioEncoding, bufferSize, AudioTrack.MODE_STREAM)
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
        var lastFrameTime = System.currentTimeMillis()
        var streamDisruptedTime: Long? = null

        val streamRunning = AtomicBoolean(true)
        val playbackFailed = AtomicBoolean(false)
        val playbackThread = Thread {
            val decodedBuffer = ShortArray(byteBufferSize * 2)
            Log.i(TAG, "Starting playback from jitter buffer")
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
                        postStatus("Connection disrupted...")
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

                if (header.seqNum != expectedSeqNum) {
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
        val mp = MediaPlayer.create(this, R.raw.upward_beep_chromatic_fifths)
        if (mp != null) {
            Log.i(TAG, "Playing alert")
            mp.setOnCompletionListener { obj: MediaPlayer -> obj.release() }
            mp.start()
        } else {
            Log.e(TAG, "Failed to play alert")
        }
    }

    companion object {
        private const val TAG = "ListenService"
        const val CHANNEL_ID = TAG
        const val ID = 902938409
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
