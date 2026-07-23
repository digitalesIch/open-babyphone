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
import android.content.SharedPreferences
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.openbabyphone.BuildConfig
import org.openbabyphone.audio.AudioFrameTiming
import org.openbabyphone.audio.FrameCodec
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.service.allowsHeartbeatRecovery
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal class MonitorFrameSequence {
    private var nextSequence = 0

    fun current(): Int = nextSequence

    fun take(clientCount: Int): Int? {
        require(clientCount >= 0)
        if (clientCount == 0) return null
        check(nextSequence < Int.MAX_VALUE) { "Stream sequence space exhausted" }
        return nextSequence++
    }

    fun reset() {
        nextSequence = 0
    }
}

class MonitorService : Service() {
    private val binder: IBinder = MonitorBinder()
    private lateinit var nsdManager: NsdManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var childIdentityStore: ChildDeviceIdentityStore
    private var registrationListener: RegistrationListener? = null
    private var currentSocket: ServerSocket? = null
    @Volatile private var currentAuthenticatingSocket: Socket? = null
    private val authenticatingSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val handshakeExecutor = BoundedHandshakeExecutor()
    @Volatile private var connectionToken: Any? = null
    private var currentPort = 0
    private lateinit var notificationManager: NotificationManager
    private var monitorThread: Thread? = null
    private var audioProducerThread: Thread? = null
    private val clientManager = ClientManager()
    @Volatile private var isStreaming = false
    private val capacityLock = ReentrantLock()
    private val capacityCondition = capacityLock.newCondition()
    private val streamFrameLock = ReentrantLock()
    private val sessionStateLock = Any()
    private val workerGeneration = WorkerGeneration()
    @Volatile private var activeWorkerClaim: WorkerClaim? = null
    @Volatile private var currentAudioRecord: AudioRecord? = null

    private var pairingCodeSnapshot: String = ""
    private var streamSessionId: ByteArray? = null
    private var streamBaseKey: ByteArray? = null
    private var streamKey: ByteArray? = null
    private var streamKdfSalt: ByteArray? = null
    private val frameSequence = MonitorFrameSequence()
    @Volatile private var microphoneGain: Float = 1.0f
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val pairingCode: String
        get() = pairingCodeSnapshot

    private val listenAddresses: List<String>
        get() {
            val addresses: MutableList<String> = ArrayList()
            try {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        val linkProperties = connectivityManager.getLinkProperties(network)
                        linkProperties?.linkAddresses?.forEach { linkAddress ->
                            val address = linkAddress.address
                            val hostAddress = address.hostAddress
                            if (!address.isLinkLocalAddress && !address.isLoopbackAddress && hostAddress != null) {
                                addresses.add(hostAddress)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get network addresses", e)
            }
            return addresses
        }

    private data class PendingParentAuthentication(
        val hello: Handshake.ChildHello,
        val response: Handshake.ParentResponse,
        val authKey: ByteArray,
        val deadline: HandshakeDeadline
    )

    private fun authenticateParent(socket: Socket, claim: WorkerClaim): PendingParentAuthentication? {
        if (!isWorkerActive(claim)) return null
        val sessionId = streamSessionId ?: return null
        val baseKey = streamBaseKey ?: return null
        val kdfSalt = streamKdfSalt ?: return null
        return try {
            val deadline = HandshakeDeadline(AUTH_TIMEOUT_MS, SystemClock::elapsedRealtime)
            val input = deadline.input(socket.getInputStream()) { socket.soTimeout = it }
            val hello = Handshake.createChildHello(childIdentityStore.identity, sessionId, kdfSalt)
            Handshake.writeChildHello(socket.getOutputStream(), hello)
            deadline.check()
            val response = Handshake.readParentResponse(input)
                ?: run {
                    Log.w(TAG, "Parent did not send an authentication response")
                    return null
                }
            val authKey = CryptoHelper.deriveAuthKey(baseKey)
            if (!Handshake.verifyParentResponse(hello, response, authKey)) {
                Log.w(TAG, "Rejected parent connection with invalid pairing code")
                authKey.fill(0)
                return null
            }
            deadline.check()
            PendingParentAuthentication(hello, response, authKey, deadline)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to authenticate parent connection", e)
            null
        }
    }

    private fun handleClient(socket: Socket, claim: WorkerClaim): Boolean {
        val pending = authenticateParent(socket, claim)
        if (pending == null) {
            Log.w(TAG, "Client authentication failed")
            return false
        }
        val client = streamFrameLock.run {
            lock()
            try {
                if (!isWorkerActive(claim)) return false
                pending.deadline.check()
                val ack = Handshake.createChildAck(
                    pending.hello,
                    pending.response,
                    frameSequence.current(),
                    pending.authKey
                )
                Handshake.writeChildAck(socket.getOutputStream(), ack)
                pending.deadline.check()
                socket.soTimeout = 0
                check(streamKey != null) { "Missing derived stream key" }
                clientManager.addClient(socket, pairingCode)
            } finally {
                pending.authKey.fill(0)
                unlock()
            }
        }
        if (client == null) {
            Log.w(TAG, "Rejected client - max clients reached")
            socket.close()
            return false
        }

        val clientCount = clientManager.getClientCount()
        val sessionActive = synchronized(sessionStateLock) {
            if (!isWorkerActive(claim)) {
                false
            } else {
                MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(clientCount))
                MonitorServiceRepository.updateConnectedClients(clientCount)
                true
            }
        }
        if (!sessionActive) {
            clientManager.removeClient(client)
            return false
        }

        if (!clientManager.canAcceptMoreClients()) {
            unregisterService()
            Log.i(TAG, "Max clients reached, unregistering NSD")
        }

        return true
    }

    private fun startAudioProducer(claim: WorkerClaim) {
        val canStart = synchronized(sessionStateLock) {
            if (!isWorkerActive(claim) || isStreaming || streamSessionId == null) {
                false
            } else {
                isStreaming = true
                MonitorServiceRepository.updateSessionState(MonitorSessionState.Starting)
                true
            }
        }
        if (!canStart) {
            Log.w(TAG, "Audio producer cannot start for an inactive or already streaming session")
            return
        }

        val producerThread = Thread {
            val frequency: Int = AudioCodecDefines.FREQUENCY
            val channelConfiguration: Int = AudioCodecDefines.CHANNEL_CONFIGURATION_IN
            val audioEncoding: Int = AudioCodecDefines.ENCODING
            val bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)

            if (bufferSize <= 0) {
                Log.e(TAG, "Invalid audio buffer size: $bufferSize")
                stopStreamingIfCurrent(claim)
                handleAudioProducerFailure(MonitorSessionError.AudioCapture, claim)
                return@Thread
            }

            val audioRecord: AudioRecord = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize)
            } catch (e: SecurityException) {
                Log.e(TAG, "AudioRecord permission denied", e)
                stopStreamingIfCurrent(claim)
                handleAudioProducerFailure(MonitorSessionError.AudioCapture, claim)
                return@Thread
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioRecord initialization failed", e)
                stopStreamingIfCurrent(claim)
                handleAudioProducerFailure(MonitorSessionError.AudioCapture, claim)
                return@Thread
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "AudioRecord parameters were rejected", e)
                stopStreamingIfCurrent(claim)
                handleAudioProducerFailure(MonitorSessionError.AudioCapture, claim)
                return@Thread
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly")
                audioRecord.release()
                stopStreamingIfCurrent(claim)
                handleAudioProducerFailure(MonitorSessionError.AudioCapture, claim)
                return@Thread
            }

            val pcmBuffer = ShortArray(AudioFrameTiming.FRAME_SAMPLES)
            val ulawBuffer = ByteArray(AudioFrameTiming.FRAME_SAMPLES)
            val frameBuffer = ByteArray(FrameCodec.MAX_FRAME_SIZE)
            val sessionStartTime = SystemClock.elapsedRealtime()
            var lastHeartbeatTime = 0L
            var failureType: MonitorSessionError? = null

            try {
                currentAudioRecord = audioRecord
                audioRecord.startRecording()
                while (isStreaming && isWorkerActive(claim) && !Thread.currentThread().isInterrupted) {
                    var capturedSamples = 0
                    while (capturedSamples < AudioFrameTiming.FRAME_SAMPLES && isStreaming &&
                        isWorkerActive(claim) && !Thread.currentThread().isInterrupted
                    ) {
                        val read = audioRecord.read(
                            pcmBuffer,
                            capturedSamples,
                            AudioFrameTiming.FRAME_SAMPLES - capturedSamples
                        )
                        if (read < 0) {
                            Log.e(TAG, "AudioRecord read error: $read")
                            failureType = MonitorSessionError.AudioCapture
                            break
                        }
                        if (read > 0) capturedSamples += read
                    }
                    if (failureType != null) break
                    if (capturedSamples != AudioFrameTiming.FRAME_SAMPLES) continue
                    if (clientManager.getClientCount() == 0) continue
                    val gain = microphoneGain
                    if (gain > 1.0f) {
                        MicrophoneSensitivity.applyGain(pcmBuffer, capturedSamples, gain)
                    }
                    val encoded = try {
                        AudioCodecDefines.CODEC.encode(pcmBuffer, capturedSamples, ulawBuffer, 0)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Audio encoding failed", e)
                        failureType = MonitorSessionError.AudioEncoding
                        break
                    }
                    if (encoded <= 0) {
                        Log.e(TAG, "Audio encoder produced no data")
                        failureType = MonitorSessionError.AudioEncoding
                        break
                    }
                    val timestampMs = (SystemClock.elapsedRealtime() - sessionStartTime).toInt()
                    try {
                        streamFrameLock.lock()
                        if (!isWorkerActive(claim)) break
                        val sessionId = streamSessionId ?: throw IllegalStateException("Missing stream session")
                        val key = streamKey
                        if (key != null) {
                            val sequence = frameSequence.take(clientManager.getClientCount()) ?: continue
                            val frameLength = FrameCodec.encodeFrameInto(
                                ulawBuffer,
                                0,
                                encoded,
                                sequence,
                                timestampMs,
                                key,
                                sessionId,
                                frameBuffer
                            )
                            clientManager.broadcastFrame(frameBuffer, 0, frameLength)

                            val currentTime = SystemClock.elapsedRealtime()
                            if (currentTime - lastHeartbeatTime >= AudioCodecDefines.HEARTBEAT_INTERVAL_MS) {
                                val heartbeatSequence = frameSequence.take(clientManager.getClientCount())
                                if (heartbeatSequence != null) {
                                    val heartbeatLength = FrameCodec.encodeHeartbeatInto(
                                        heartbeatSequence,
                                        timestampMs,
                                        key,
                                        sessionId,
                                        frameBuffer
                                    )
                                    clientManager.broadcastFrame(frameBuffer, 0, heartbeatLength)
                                    lastHeartbeatTime = currentTime
                                }
                            }
                        }
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Audio frame encoding failed", e)
                        failureType = MonitorSessionError.AudioEncoding
                        break
                    } finally {
                        if (streamFrameLock.isHeldByCurrentThread) streamFrameLock.unlock()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio producer failed", e)
                failureType = MonitorSessionError.AudioCapture
            } finally {
                try {
                    audioRecord.stop()
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "AudioRecord already stopped")
                }
                audioRecord.release()
                if (currentAudioRecord === audioRecord) currentAudioRecord = null
                val unexpectedFailure = failureType
                stopStreamingIfCurrent(claim)
                if (unexpectedFailure != null && isWorkerActive(claim)) {
                    handleAudioProducerFailure(unexpectedFailure, claim)
                }
            }
        }
        synchronized(sessionStateLock) {
            if (!isWorkerActive(claim)) {
                stopStreamingIfCurrent(claim)
                return
            }
            audioProducerThread = producerThread
            producerThread.start()
            Log.i(TAG, "Audio producer started")
        }
    }

    private fun handleAudioProducerFailure(type: MonitorSessionError, claim: WorkerClaim) {
        synchronized(sessionStateLock) {
            if (!workerGeneration.isCurrent(claim)) return
            connectionToken = null
            MonitorServiceRepository.updateError(type, getString(R.string.microphone_unavailable))
            clientManager.setClientCountListener(null)
            clientManager.removeAllClients()
            closeAuthenticatingSockets()
            unregisterService()
            currentSocket?.let(::closeServerSocket)
            ServiceHeartbeatScheduler.cancelMonitor(this)
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } finally {
                stopSelfResult(claim.startId)
            }
        }
    }

    override fun onCreate() {
        Log.i(TAG, "Open Babyphone start")
        super.onCreate()
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        this.nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
        this.connectivityManager = this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        this.childIdentityStore = ChildDeviceIdentityStore(this)
        this.currentPort = ConnectionConstants.DEFAULT_PORT
        this.currentSocket = null
        registerMicrophonePrefsListener()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId")
        val heartbeatRecovery = intent.getBooleanExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, false)
        if (heartbeatRecovery &&
            !MonitorServiceRepository.sessionState.value.allowsHeartbeatRecovery()
        ) {
            ServiceHeartbeatScheduler.cancelMonitor(this)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (heartbeatRecovery &&
            monitorThread?.isAlive == true && activeWorkerClaim?.let(workerGeneration::isCurrent) == true
        ) {
            ServiceHeartbeatScheduler.scheduleMonitor(this)
            return START_REDELIVER_INTENT
        }
        val claim = synchronized(sessionStateLock) {
            workerGeneration.claim(startId).also {
                activeWorkerClaim = it
                connectionToken = null
                isStreaming = false
            }
        }
        retireSessionWorkers()
        return try {
            startMonitoringCommand(claim)
        } catch (exception: RuntimeException) {
            handleStartupFailure(exception, claim)
            if (heartbeatRecovery) {
                ServiceHeartbeatScheduler.scheduleMonitor(this)
                ServiceRecoveryNotifier.notifyMonitorActionRequired(this)
            }
            START_NOT_STICKY
        }
    }

    private fun startMonitoringCommand(claim: WorkerClaim): Int {
        pairingCodeSnapshot = PairingSettings.load(this).pairingCode
        microphoneGain = MicrophoneSensitivityPreferences.read(this).gain
        createNotificationChannel()
        val n = buildNotification()
        ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        ServiceHeartbeatScheduler.scheduleMonitor(this)
        clientManager.setClientCountListener { count ->
            val sessionActive = synchronized(sessionStateLock) {
                if (!isWorkerActive(claim)) {
                    false
                } else {
                    MonitorServiceRepository.updateSessionState(
                        if (count > 0) MonitorSessionState.Connected(count) else MonitorSessionState.WaitingForParent
                    )
                    MonitorServiceRepository.updateConnectedClients(count)
                    true
                }
            }
            if (sessionActive && clientManager.canAcceptMoreClients() &&
                registrationListener == null && isWorkerActive(claim)
            ) {
                Log.i(TAG, "Capacity available again, re-registering NSD")
                currentSocket?.localPort?.let { registerService(it, claim) }
            }
            capacityLock.lock()
            try {
                capacityCondition.signalAll()
            } finally {
                capacityLock.unlock()
            }
        }
        startMonitorThread(claim)
        return START_REDELIVER_INTENT
    }

    private fun handleStartupFailure(exception: RuntimeException, claim: WorkerClaim) {
        Log.e(TAG, "Failed to start monitoring service", exception)
        synchronized(sessionStateLock) {
            if (!workerGeneration.isCurrent(claim)) return
            connectionToken = null
            MonitorServiceRepository.updateError(
                MonitorSessionError.Startup,
                getString(R.string.monitoring_start_failed)
            )
            ServiceHeartbeatScheduler.cancelMonitor(this)
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (stopException: RuntimeException) {
                Log.d(TAG, "Foreground service was not started", stopException)
            }
            stopSelfResult(claim.startId)
        }
    }

    private fun startMonitorThread(claim: WorkerClaim) {
        var mt: Thread
        streamFrameLock.lock()
        try {
            streamBaseKey?.fill(0)
            streamKey?.fill(0)
            streamBaseKey = null
            streamKey = null
            streamSessionId = null
            streamKdfSalt = null
        } finally {
            streamFrameLock.unlock()
        }
        val currentToken = Any()
        synchronized(sessionStateLock) {
            if (!workerGeneration.isCurrent(claim)) return
            this.connectionToken = currentToken
        }
        mt = Thread {
            try {
                val sessionId = CryptoHelper.generateSessionId()
                val kdfSalt = ensureKdfSalt()
                val baseKey = CryptoHelper.deriveKey(pairingCode, kdfSalt)
                val identity = childIdentityStore.identity
                val keyContext = Handshake.streamKeyContext(
                    Handshake.createChildHello(
                        identity,
                        sessionId,
                        kdfSalt,
                        ByteArray(CryptoHelper.CHALLENGE_SIZE)
                    )
                )
                val derivedStreamKey = try {
                    CryptoHelper.deriveStreamKey(baseKey, keyContext)
                } catch (exception: Exception) {
                    baseKey.fill(0)
                    throw exception
                }
                streamFrameLock.lock()
                try {
                    if (!isWorkerActive(claim) || connectionToken != currentToken) {
                        baseKey.fill(0)
                        derivedStreamKey.fill(0)
                        return@Thread
                    }
                    streamBaseKey?.fill(0)
                    streamKey?.fill(0)
                    streamSessionId = sessionId
                    streamKdfSalt = kdfSalt
                    streamBaseKey = baseKey
                    streamKey = derivedStreamKey
                    frameSequence.reset()
                } finally {
                    streamFrameLock.unlock()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to derive monitoring session keys", exception)
                handleSessionSetupFailure(currentToken, claim)
                return@Thread
            }

            while (isWorkerActive(claim) && this.connectionToken == currentToken) {
                val portToBind = currentPort
                val serverSocket = try {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(portToBind))
                    }
                } catch (e: IOException) {
                    if (isWorkerActive(claim) && this.connectionToken == currentToken) {
                        this.currentPort++
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Failed to open server socket. Port increased to $currentPort", e)
                        } else {
                            Log.e(TAG, "Failed to open server socket, trying next port")
                        }
                    }
                    continue
                }

                serverSocket.use {
                    val ownsSocket = synchronized(sessionStateLock) {
                        if (!isWorkerActive(claim) || this.connectionToken != currentToken) {
                            false
                        } else {
                            this.currentSocket = it
                            true
                        }
                    }
                    if (!ownsSocket) return@use
                    val localPort = it.localPort
                    registerService(localPort, claim)
                    startAudioProducer(claim)

                    while (isWorkerActive(claim) && this.connectionToken == currentToken &&
                        !Thread.currentThread().isInterrupted
                    ) {
                        if (!clientManager.canAcceptMoreClients()) {
                            Log.i(TAG, "Max clients reached, waiting for disconnect")
                            capacityLock.lock()
                            try {
                                while (!clientManager.canAcceptMoreClients() && isWorkerActive(claim) &&
                                    this.connectionToken == currentToken && !Thread.currentThread().isInterrupted
                                ) {
                                    capacityCondition.await()
                                }
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                            } finally {
                                capacityLock.unlock()
                            }
                            if (!isWorkerActive(claim) || this.connectionToken != currentToken ||
                                Thread.currentThread().isInterrupted
                            ) {
                                break
                            }
                        }

                        val socket = try {
                            it.accept()
                        } catch (e: IOException) {
                            if (isWorkerActive(claim) && this.connectionToken == currentToken &&
                                !Thread.currentThread().isInterrupted
                            ) {
                                Log.w(TAG, "Failed while accepting parent connection", e)
                            }
                            continue
                        }

                        Log.i(TAG, "Connection from parent device received")
                        dispatchParentHandshake(socket, claim)
                    }
                    if (this.currentSocket === it) {
                        this.currentSocket = null
                    }
                }
            }
        }
        synchronized(sessionStateLock) {
            if (!isWorkerActive(claim) || connectionToken !== currentToken) return
            monitorThread = mt
            mt.start()
        }
    }

    private fun handleSessionSetupFailure(token: Any, claim: WorkerClaim) {
        val active = synchronized(sessionStateLock) {
            if (!workerGeneration.isCurrent(claim) || connectionToken !== token) {
                false
            } else {
                connectionToken = null
                MonitorServiceRepository.updateError(
                    MonitorSessionError.Authentication,
                    getString(R.string.authentication_setup_failed)
                )
                true
            }
        }
        if (!active) return
        synchronized(sessionStateLock) {
            if (!workerGeneration.isCurrent(claim)) return
            ServiceHeartbeatScheduler.cancelMonitor(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(claim.startId)
        }
    }

    private fun isWorkerActive(claim: WorkerClaim): Boolean =
        workerGeneration.isCurrent(claim) && activeWorkerClaim == claim

    private fun stopStreamingIfCurrent(claim: WorkerClaim) {
        if (workerGeneration.isCurrent(claim)) isStreaming = false
    }

    private fun dispatchParentHandshake(socket: Socket, claim: WorkerClaim) {
        val acceptedForDispatch = synchronized(sessionStateLock) {
            if (!isWorkerActive(claim)) {
                false
            } else {
                authenticatingSockets.add(socket)
                currentAuthenticatingSocket = socket
                true
            }
        }
        if (!acceptedForDispatch) {
            closeSocket(socket, "stale parent handshake")
            return
        }
        val closeRejected = {
            authenticatingSockets.remove(socket)
            if (currentAuthenticatingSocket === socket) currentAuthenticatingSocket = null
            closeSocket(socket, "rejected parent handshake")
        }
        handshakeExecutor.submit(closeRejected) {
            val accepted = try {
                isWorkerActive(claim) && handleClient(socket, claim)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle parent connection", e)
                false
            } finally {
                authenticatingSockets.remove(socket)
                if (currentAuthenticatingSocket === socket) currentAuthenticatingSocket = null
            }
            if (!accepted) closeSocket(socket, "rejected parent socket")
        }
    }

    private fun retireSessionWorkers() {
        isStreaming = false
        clientManager.setClientCountListener(null)
        closeAuthenticatingSockets()
        unregisterService()
        clientManager.removeAllClients()
        currentAudioRecord?.let { record ->
            try {
                record.stop()
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "AudioRecord already stopped", exception)
            }
        }
        currentSocket?.let { closeServerSocket(it) }
        currentSocket = null
        capacityLock.lock()
        try {
            capacityCondition.signalAll()
        } finally {
            capacityLock.unlock()
        }
        monitorThread?.let { thread ->
            thread.interrupt()
            if (thread !== Thread.currentThread()) {
                try {
                    thread.join(1000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        monitorThread = null
        audioProducerThread?.let { thread ->
            thread.interrupt()
            if (thread !== Thread.currentThread()) {
                try {
                    thread.join(1000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        audioProducerThread = null
    }

    private fun closeServerSocket(socket: ServerSocket) {
        try {
            socket.close()
        } catch (exception: IOException) {
            Log.d(TAG, "Failed to close monitoring server socket", exception)
        }
    }

    private fun closeSocket(socket: Socket, reason: String) {
        try {
            socket.close()
        } catch (exception: IOException) {
            Log.d(TAG, "Failed to close $reason", exception)
        }
    }

    private fun closeAuthenticatingSockets() {
        val sockets = authenticatingSockets.toList()
        authenticatingSockets.clear()
        val socket = currentAuthenticatingSocket
        currentAuthenticatingSocket = null
        sockets.forEach { closeSocket(it, "authenticating parent socket") }
        if (socket != null && socket !in sockets) closeSocket(socket, "authenticating parent socket")
    }

    private fun buildServiceName(): String {
        val deviceName = getSharedPreferences(PAIRING_PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_KEY_DEVICE_NAME, "") ?: ""
        return if (deviceName.isBlank()) {
            "Open Babyphone \u2014 ${getString(R.string.default_child_name)}"
        } else {
            "Open Babyphone \u2014 $deviceName"
        }
    }

    private fun registerService(port: Int, claim: WorkerClaim) {
        if (!isWorkerActive(claim)) return
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = buildServiceName()
        serviceInfo.serviceType = ConnectionConstants.SERVICE_TYPE
        serviceInfo.port = port

        val identity = childIdentityStore.identity
        val savedDeviceName = getSharedPreferences(PAIRING_PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_KEY_DEVICE_NAME, "") ?: ""
        val deviceName = savedDeviceName.ifBlank { getString(R.string.default_child_name) }
        serviceInfo.setAttribute(ConnectionConstants.NSD_TXT_APP, ConnectionConstants.NSD_TXT_APP_VALUE)
        serviceInfo.setAttribute(ConnectionConstants.NSD_TXT_CHILD_ID, identity.childId)
        serviceInfo.setAttribute(ConnectionConstants.NSD_TXT_PAIRING_ID, identity.pairingId)
        serviceInfo.setAttribute(ConnectionConstants.NSD_TXT_NAME, deviceName)

        lateinit var listener: RegistrationListener
        listener = object : RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                val serviceName = nsdServiceInfo.serviceName
                val addresses = listenAddresses
                val shouldUnregister = synchronized(sessionStateLock) {
                    if (!isWorkerActive(claim)) {
                        true
                    } else {
                        Log.i(TAG, "Service name: $serviceName")
                        MonitorServiceRepository.updateServiceInfo(serviceName, port, addresses)
                        MonitorServiceRepository.updateSessionState(
                            if (addresses.isEmpty()) MonitorSessionState.NoNetwork
                            else MonitorSessionState.WaitingForParent
                        )
                        false
                    }
                }
                if (shouldUnregister) {
                    unregisterService(listener)
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
                synchronized(sessionStateLock) {
                    if (isWorkerActive(claim)) {
                        MonitorServiceRepository.updateError(
                            MonitorSessionError.Advertising,
                            getString(R.string.advertising_failed)
                        )
                    }
                }
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(TAG, "Unregistering service")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }
        synchronized(sessionStateLock) {
            if (!isWorkerActive(claim)) return
            registrationListener = listener
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterService(listener: RegistrationListener? = registrationListener) {
        listener ?: return
        synchronized(sessionStateLock) {
            if (registrationListener === listener) registrationListener = null
        }
        Log.i(TAG, "Unregistering monitoring service")
        try {
            nsdManager.unregisterService(listener)
        } catch (e: RuntimeException) {
            Log.d(TAG, "Monitoring service was already unregistered", e)
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_service_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        this.notificationManager.createNotificationChannel(serviceChannel)
    }

    private fun buildNotification(): Notification {
        val text: CharSequence = getText(R.string.child_device)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.listening_notification)
            .setOngoing(true)
            .setTicker(text)
            .setContentTitle(text)
            .build()
    }

    override fun onDestroy() {
        prefsListener?.let {
            getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        prefsListener = null
        synchronized(sessionStateLock) {
            workerGeneration.invalidate()
            activeWorkerClaim = null
            connectionToken = null
        }
        retireSessionWorkers()
        handshakeExecutor.shutdownNow()
        streamKey?.fill(0)
        streamKey = null
        streamBaseKey?.fill(0)
        streamBaseKey = null
        val terminalError = synchronized(sessionStateLock) {
            val isError = MonitorServiceRepository.sessionState.value is MonitorSessionState.Error
            MonitorServiceRepository.updateStopped()
            isError
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (!terminalError) {
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        }
        super.onDestroy()
    }

    private fun registerMicrophonePrefsListener() {
        val prefs = getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, MODE_PRIVATE)
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MicrophoneSensitivityPreferences.KEY) {
                microphoneGain = MicrophoneSensitivityPreferences.read(this).gain
                Log.i(TAG, "Microphone sensitivity changed, gain=$microphoneGain")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun ensureKdfSalt(): ByteArray {
        val prefs = getSharedPreferences(PAIRING_PREFS_NAME, MODE_PRIVATE)
        val existing = prefs.getString(PREF_KEY_KDF_SALT, null)
        if (existing != null) {
            val decoded = android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
            if (decoded.size == CryptoHelper.SALT_SIZE) {
                return decoded
            }
        }
        val newSalt = CryptoHelper.generateSalt()
        prefs.edit()
            .putString(PREF_KEY_KDF_SALT, android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP))
            .apply()
        return newSalt
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
        const val PREF_KEY_DEVICE_NAME = "deviceName"
        const val PREF_KEY_MICROPHONE_SENSITIVITY = "microphoneSensitivity"
        const val PREF_KEY_KDF_SALT = "kdfSalt"
        private const val AUTH_TIMEOUT_MS = 10_000L
    }
}
