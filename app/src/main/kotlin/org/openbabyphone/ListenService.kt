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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.openbabyphone.BuildConfig
import org.openbabyphone.audio.FrameCodec
import org.openbabyphone.audio.FrameHeader
import org.openbabyphone.audio.FrameSequence
import org.openbabyphone.audio.FrameSequenceDecision
import org.openbabyphone.audio.JitterBuffer
import org.openbabyphone.audio.AudioFrameTiming
import org.openbabyphone.audio.PacketLossConcealer
import org.openbabyphone.audio.SenderTimestampClock
import org.openbabyphone.audio.AudioDeliveryHealth
import org.openbabyphone.audio.AudioDeliveryStatus
import org.openbabyphone.audio.AudioWriteResult
import org.openbabyphone.audio.writeAllAudioSamples
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import org.openbabyphone.service.TerminalConnectionFailure
import org.openbabyphone.service.classifyTerminalConnectionFailure
import org.openbabyphone.service.shouldConsumePendingConnection
import org.openbabyphone.service.allowsHeartbeatRecovery
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ListenService : Service() {
    private val frequency: Int = AudioCodecDefines.FREQUENCY
    private val channelConfiguration: Int = AudioCodecDefines.CHANNEL_CONFIGURATION_OUT
    private val audioEncoding: Int = AudioCodecDefines.ENCODING
    private val bufferSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
    private val binder: IBinder = ListenBinder()
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var connectivityManager: ConnectivityManager
    private var listenThread: Thread? = null
    private var deliveryHealthThread: Thread? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var currentSocket: Socket? = null
    @Volatile private var isRunning = false
    @Volatile private var hasAudioFocus = false
    val volumeHistory = VolumeHistory(16384)
    var childDeviceName: String? = null
        private set
    private var activeRequestId: String? = null
    private var registeredSessionToken: Long? = null
    private var wifiDirectOwnershipToken: Long? = null
    private val deliveryHealth = AudioDeliveryHealth(SystemClock::elapsedRealtime)
    private val lossAlertSent = AtomicBoolean(false)
    private val sessionStateLock = Any()
    private var terminalFailure = false
    private val workerGeneration = WorkerGeneration()

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
        this.connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        createAlertNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId")
        if (intent.getBooleanExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, false) && isRunning) {
            ServiceHeartbeatScheduler.scheduleListen(this, intent, registeredSessionToken)
            return START_REDELIVER_INTENT
        }
        if (intent.getBooleanExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, false) &&
            !ListenServiceRepository.sessionState.value.allowsHeartbeatRecovery()
        ) {
            ServiceHeartbeatScheduler.cancelListen(this)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val claim = synchronized(sessionStateLock) {
            workerGeneration.claim(startId).also {
                terminalFailure = false
                isRunning = true
                deliveryHealth.disarm()
            }
        }
        ActiveListenSessionRegistry.markInactive(registeredSessionToken)
        registeredSessionToken = null
        wifiDirectOwnershipToken = wifiDirectCleanupCoordinator().claimForListenSession()
        stopListenThread()
        stopDeliveryHealthWatchdog()

        createNotificationChannel()
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        val requestId = intent.getStringExtra("requestId").orEmpty()
        val expectedChildId = intent.getStringExtra("expectedChildId").orEmpty()
        val expectedPairingId = intent.getStringExtra("expectedPairingId").orEmpty()
        val resolution = resolveConnection(requestId, expectedChildId, expectedPairingId)
        val connection = (resolution as? ConnectionResolution.Available)?.connection
        val name = connection?.name
        childDeviceName = name
        ListenServiceRepository.updateChildDeviceName(name ?: "")
        activeRequestId = requestId.takeIf { it.isNotBlank() }
        val resumableIdentity = when {
            connection != null -> connection.expectedIdentity
            expectedChildId.isNotBlank() && expectedPairingId.isNotBlank() ->
                ExpectedChildIdentity(expectedChildId, expectedPairingId)
            else -> null
        }
        registeredSessionToken = ActiveListenSessionRegistry.register(
            resumableIdentity,
            activeRequestId
        )
        if (BuildConfig.DEBUG) {
            connection?.let { Log.d(TAG, "Connecting to ${it.address}:${it.port}") }
        }
        ListenServiceRepository.startConnecting(name ?: "")
        val n = buildForegroundNotification(name)
        ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        ServiceHeartbeatScheduler.scheduleListen(this, intent, registeredSessionToken)
        registerNetworkCallback()
        if (connection == null) {
            val error = if (resolution == ConnectionResolution.CredentialUnavailable) {
                ListenSessionError.CredentialStorage
            } else {
                ListenSessionError.Unreachable
            }
            handleTerminalFailure(error, claim)
        } else {
            doListen(connection, claim)
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        isRunning = false
        synchronized(sessionStateLock) {
            workerGeneration.invalidate()
            deliveryHealth.disarm()
        }
        unregisterNetworkCallback()
        stopListenThread()
        stopDeliveryHealthWatchdog()
        abandonAudioFocus()
        activeRequestId = null
        ActiveListenSessionRegistry.markInactive(registeredSessionToken)
        registeredSessionToken = null
        val terminalError = ListenServiceRepository.sessionState.value.let {
            it is ListenSessionState.Error || it is ListenSessionState.Lost
        }
        ListenServiceRepository.updateStopped()

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (!terminalError) {
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        }
        wifiDirectOwnershipToken?.let(wifiDirectCleanupCoordinator()::cleanup)
        wifiDirectOwnershipToken = null
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
            if (lt !== Thread.currentThread()) {
                try {
                    lt.join(1000)
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Interrupted while waiting for listen thread to stop")
                    Thread.currentThread().interrupt()
                }
            }
        }
        listenThread = null
        currentSocket = null
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network available, waking reconnect loop")
                reconnectWakeSignal.signal()
            }

            override fun onLost(network: Network) {
                if (isRunning) {
                    Log.i(TAG, "Network lost during listening session")
                    ListenServiceRepository.updateDisrupted()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: RuntimeException) {
            Log.d(TAG, "Network callback already unregistered", e)
        } finally {
            networkCallback = null
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun buildForegroundNotification(name: String?): Notification {
        val text = getText(R.string.listening)
        val resumeIntent = Intent(this, ListenResumeActivity::class.java).apply {
            registeredSessionToken?.let { putExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            FOREGROUND_REQUEST_CODE,
            resumeIntent,
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
        val notification = buildConnectionLostAlertNotification()
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun sendAudioInterruptedAlert() {
        val notification = buildAlertNotification(
            R.string.audio_interrupted_alert_title,
            R.string.audio_interrupted_alert_text
        )
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun buildConnectionLostAlertNotification(): Notification = buildAlertNotification(
        R.string.connection_lost_alert_title,
        R.string.connection_lost_alert_text
    )

    private fun buildAlertNotification(titleRes: Int, textRes: Int): Notification {
        val contentIntent = buildResumePendingIntent(ALERT_REQUEST_CODE)
        return NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.listening_notification)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun buildResumePendingIntent(requestCode: Int): PendingIntent {
        val resumeIntent = Intent(this, ListenResumeActivity::class.java).apply {
            registeredSessionToken?.let { putExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, it) }
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            resumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    inner class ListenBinder : Binder() {
        val service: ListenService
            get() = this@ListenService
    }

    var onError: (() -> Unit)? = null
    var onUpdate: (() -> Unit)? = null
    var onStatusChange: ((String) -> Unit)? = null

    fun clearCallbacks() {
        onError = null
        onUpdate = null
        onStatusChange = null
    }

    private val reconnectBackoff = ReconnectBackoff()
    private val reconnectWakeSignal = ReconnectWakeSignal()

    private fun resolveConnection(
        requestId: String,
        expectedChildId: String,
        expectedPairingId: String
    ): ConnectionResolution {
        if ((expectedChildId.isEmpty()) != (expectedPairingId.isEmpty())) return ConnectionResolution.Missing
        val requestedIdentity = if (expectedChildId.isNotEmpty()) {
            ExpectedChildIdentity(expectedChildId, expectedPairingId)
        } else {
            null
        }
        val pending = requestId.takeIf { it.isNotBlank() }?.let(PendingConnections.store::lease)
        if (pending != null) {
            if (pending.address.isBlank() || pending.port !in VALID_PORT_RANGE) return ConnectionResolution.Missing
            val pendingIdentity = pending.expectedChildId?.let { childId ->
                ExpectedChildIdentity(childId, checkNotNull(pending.expectedPairingId))
            }
            if (requestedIdentity != null && requestedIdentity != pendingIdentity) return ConnectionResolution.Missing
            val identity = pendingIdentity ?: requestedIdentity
            val pairingCode = pending.pairingCode?.copyOf() ?: identity?.let {
                when (val trusted = trustedChildStore().resolveConnection(it.childId, it.pairingId)) {
                    is TrustedConnectionResult.Available -> trusted.pairingCode
                    TrustedConnectionResult.Missing -> null
                    TrustedConnectionResult.Unavailable -> return ConnectionResolution.CredentialUnavailable
                }
            } ?: return ConnectionResolution.Missing
            return ConnectionResolution.Available(
                ListenConnection(
                    requestId = requestId,
                    address = pending.address,
                    port = pending.port,
                    name = pending.name,
                    pairingCode = pairingCode,
                    expectedIdentity = identity,
                    rememberAfterAuthentication = pending.rememberAfterAuthentication
                )
            )
        }
        val identity = requestedIdentity ?: return ConnectionResolution.Missing
        return when (val trusted = trustedChildStore().resolveConnection(identity.childId, identity.pairingId)) {
            TrustedConnectionResult.Missing -> ConnectionResolution.Missing
            TrustedConnectionResult.Unavailable -> ConnectionResolution.CredentialUnavailable
            is TrustedConnectionResult.Available -> {
                val address = trusted.child.lastKnownAddress
                val port = trusted.child.lastKnownPort
                if (address == null || port == null) {
                    trusted.pairingCode.fill('\u0000')
                    return ConnectionResolution.Missing
                }
                ConnectionResolution.Available(
                    ListenConnection(
                        requestId = null,
                        address = address,
                        port = port,
                        name = trusted.child.displayName,
                        pairingCode = trusted.pairingCode,
                        expectedIdentity = identity,
                        rememberAfterAuthentication = false
                    )
                )
            }
        }
    }

    private fun doListen(connection: ListenConnection, claim: WorkerClaim) {
        val address = connection.address
        val port = connection.port
        val hasVerifiedAudio = AtomicBoolean(false)
        synchronized(sessionStateLock) {
            deliveryHealth.disarm()
        }
        if (port !in VALID_PORT_RANGE) {
            connection.pairingCode.fill('\u0000')
            Log.e(TAG, "Invalid socket port")
            handleTerminalFailure(ListenSessionError.Unreachable, claim)
            return
        }
        startDeliveryHealthWatchdog(claim)
        val lt = Thread {
            try {
            var reconnectAttempts = 0
            var shouldReconnect: Boolean
            var trustPersisted = !connection.rememberAfterAuthentication
            do {
                if (!isWorkerActive(claim)) break
                try {
                    val socket = Socket()
                    val canConnect = synchronized(sessionStateLock) {
                        if (!isWorkerActive(claim)) {
                            false
                        } else {
                            currentSocket = socket
                            true
                        }
                    }
                    if (!canConnect) {
                        socket.close()
                        break
                    }
                    socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = SOCKET_READ_TIMEOUT_MS

                    val sessionInfo = performHandshake(socket, connection.pairingCode, connection.expectedIdentity)
                    if (sessionInfo == null) {
                        Log.e(TAG, "Handshake failed")
                        socket.close()
                        clearCurrentSocket(socket)
                        if (isWorkerActive(claim)) {
                            handleTerminalFailure(ListenSessionError.Authentication, claim)
                        }
                        shouldReconnect = false
                    } else {
                        if (!isWorkerActive(claim)) {
                            sessionInfo.streamKey.fill(0)
                            socket.close()
                            break
                        }
                        val verifiedAudioThisConnection = AtomicBoolean(false)
                        val streamResult = try {
                            val connectedAddress = address
                            val trustedChildStore = trustedChildStore()
                            val storageResult = if (!trustPersisted) {
                                trustedChildStore.trustAuthenticated(
                                        childId = sessionInfo.childId,
                                        pairingId = sessionInfo.pairingId,
                                        displayName = connection.name,
                                        pairingCode = connection.pairingCode,
                                        address = connectedAddress,
                                        port = port
                                    )
                            } else {
                                trustedChildStore.updateLastKnownAuthenticated(
                                        sessionInfo.childId,
                                        sessionInfo.pairingId,
                                        connectedAddress,
                                        port
                                    )
                                CredentialStorageResult.Success
                            }
                            if (storageResult != CredentialStorageResult.Success) {
                                socket.close()
                                clearCurrentSocket(socket)
                                handleTerminalFailure(ListenSessionError.CredentialStorage, claim)
                                StreamResult.Stopped
                            } else {
                                trustPersisted = true
                                if (shouldConsumePendingConnection(connection.expectedIdentity != null)) {
                                    connection.requestId?.let(PendingConnections.store::consume)
                                }
                                val trustedIdentity = connection.expectedIdentity
                                if (trustedIdentity != null) {
                                    if (registeredSessionToken == null) {
                                        registeredSessionToken = ActiveListenSessionRegistry.register(
                                            trustedIdentity,
                                            connection.requestId
                                        )
                                    }
                                }
                                synchronized(sessionStateLock) {
                                    if (isWorkerActive(claim) && !terminalFailure) {
                                        notificationManager.notify(ID, buildForegroundNotification(childDeviceName))
                                    }
                                }
                                streamAudio(
                                    socket,
                                    sessionInfo,
                                    claim,
                                    hasVerifiedAudio,
                                    verifiedAudioThisConnection
                                )
                            }
                        } finally {
                            sessionInfo.streamKey.fill(0)
                        }
                        if (verifiedAudioThisConnection.get()) reconnectAttempts = 0
                        shouldReconnect = if (!isWorkerActive(claim)) {
                            false
                        } else when (streamResult) {
                            StreamResult.Reconnect -> true
                            StreamResult.Stopped -> false
                            is StreamResult.Fatal -> {
                                handleTerminalFailure(streamResult.type, claim)
                                false
                            }
                        }
                    }

                    if (shouldReconnect && isWorkerActive(claim)) {
                        reconnectAttempts++
                        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                            postReconnecting(reconnectAttempts, claim)
                            try {
                                waitBeforeReconnect(reconnectAttempts, claim)
                            } catch (ie: InterruptedException) {
                                Thread.currentThread().interrupt()
                                shouldReconnect = false
                            }
                        } else {
                            Log.e(TAG, "Max reconnect attempts reached")
                            handleConnectionFailure(hasVerifiedAudio.get(), claim)
                            shouldReconnect = false
                        }
                    }
                } catch (e: IOException) {
                    closeCurrentSocket(claim)
                    shouldReconnect = isWorkerActive(claim)
                    if (shouldReconnect) {
                        reconnectAttempts++
                        if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                            postReconnecting(reconnectAttempts, claim)
                            try {
                                waitBeforeReconnect(reconnectAttempts, claim)
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
                            handleConnectionFailure(hasVerifiedAudio.get(), claim)
                            shouldReconnect = false
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    closeCurrentSocket(claim)
                    Log.e(TAG, "Invalid socket parameters", e)
                    if (isWorkerActive(claim)) {
                        handleConnectionFailure(hasVerifiedAudio.get(), claim)
                    }
                    shouldReconnect = false
                } catch (e: RuntimeException) {
                    closeCurrentSocket(claim)
                    Log.e(TAG, "Unexpected listen worker failure", e)
                    if (isWorkerActive(claim)) {
                        handleConnectionFailure(hasVerifiedAudio.get(), claim)
                    }
                    shouldReconnect = false
                }
            } while (shouldReconnect && isWorkerActive(claim))
            } finally {
                connection.pairingCode.fill('\u0000')
            }
        }
        synchronized(sessionStateLock) {
            if (!isWorkerActive(claim)) {
                connection.pairingCode.fill('\u0000')
                return
            }
            listenThread = lt
            lt.start()
        }
    }

    private fun waitBeforeReconnect(attempt: Int, claim: WorkerClaim) {
        reconnectWakeSignal.waitFor(reconnectBackoff.delayForAttempt(attempt)) {
            isWorkerActive(claim)
        }
    }

    private fun isWorkerActive(claim: WorkerClaim): Boolean =
        isRunning && workerGeneration.isCurrent(claim)

    private fun closeCurrentSocket(claim: WorkerClaim) {
        if (!workerGeneration.isCurrent(claim)) return
        val socket = synchronized(sessionStateLock) {
            currentSocket.also { currentSocket = null }
        }
        try {
            socket?.close()
        } catch (exception: IOException) {
            Log.d(TAG, "Failed to close current listen socket", exception)
        }
    }

    private fun clearCurrentSocket(socket: Socket) {
        synchronized(sessionStateLock) {
            if (currentSocket === socket) currentSocket = null
        }
    }

    private fun startDeliveryHealthWatchdog(claim: WorkerClaim) {
        val thread = synchronized(sessionStateLock) {
            if (!isWorkerActive(claim)) return
            deliveryHealth.disarm()
            lossAlertSent.set(false)
            if (deliveryHealthThread?.isAlive == true) return
            Thread {
                runDeliveryHealthWatchdog(claim)
            }.also { deliveryHealthThread = it }
        }
        thread.start()
    }

    private fun runDeliveryHealthWatchdog(claim: WorkerClaim) {
        var previousStatus = AudioDeliveryStatus.Disarmed
        while (isWorkerActive(claim) && !Thread.currentThread().isInterrupted) {
            var lossTransition = false
            synchronized(sessionStateLock) {
                val status = deliveryHealth.status()
                when {
                    status == AudioDeliveryStatus.Disrupted && previousStatus != AudioDeliveryStatus.Disrupted -> {
                        Log.w(TAG, "Audio delivery disrupted")
                        ListenServiceRepository.updateDisrupted()
                    }
                    status == AudioDeliveryStatus.Lost && previousStatus != AudioDeliveryStatus.Lost -> {
                        Log.e(TAG, "Audio delivery lost")
                        ListenServiceRepository.updateDisrupted()
                        lossTransition = true
                    }
                }
                previousStatus = status
            }
            if (lossTransition) {
                synchronized(sessionStateLock) {
                    if (isWorkerActive(claim) && !terminalFailure &&
                        deliveryHealth.status() == AudioDeliveryStatus.Lost &&
                        lossAlertSent.compareAndSet(false, true)
                    ) {
                        try {
                            playAlert(terminal = false)
                        } catch (e: RuntimeException) {
                            Log.e(TAG, "Failed to raise audio delivery alert", e)
                        }
                    }
                }
                closeCurrentSocket(claim)
            }
            try {
                Thread.sleep(DELIVERY_HEALTH_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun stopDeliveryHealthWatchdog() {
        val thread = deliveryHealthThread ?: return
        deliveryHealthThread = null
        thread.interrupt()
        if (thread !== Thread.currentThread()) {
            try {
                thread.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun handleConnectionFailure(hasVerifiedAudio: Boolean, claim: WorkerClaim) {
        when (classifyTerminalConnectionFailure(hasVerifiedAudio)) {
            TerminalConnectionFailure.Unreachable ->
                handleTerminalFailure(ListenSessionError.Unreachable, claim)
            TerminalConnectionFailure.Lost -> handleTerminalLoss(claim)
        }
    }

    private fun handleTerminalLoss(claim: WorkerClaim) {
        handleTerminalState(claim, raiseConnectionAlert = true) {
            ListenServiceRepository.updateLost()
        }
    }

    private fun handleTerminalFailure(type: ListenSessionError, claim: WorkerClaim) {
        handleTerminalState(claim, raiseConnectionAlert = false) {
            ListenServiceRepository.updateError(type, getString(R.string.disconnected))
        }
    }

    private fun handleTerminalState(
        claim: WorkerClaim,
        raiseConnectionAlert: Boolean,
        publishState: () -> Unit
    ) {
        val terminalOwnership = synchronized(sessionStateLock) {
            if (!workerGeneration.isCurrent(claim) || terminalFailure) {
                null
            } else {
                terminalFailure = true
                isRunning = false
                deliveryHealth.disarm()
                publishState()
                TerminalOwnership(registeredSessionToken, wifiDirectOwnershipToken)
            }
        }
        if (terminalOwnership == null) return

        ActiveListenSessionRegistry.markInactive(terminalOwnership.sessionToken)
        terminalOwnership.wifiDirectToken?.let(wifiDirectCleanupCoordinator()::cleanup)
        closeCurrentSocket(claim)
        if (!workerGeneration.isCurrent(claim)) return
        ServiceHeartbeatScheduler.cancelListen(this)
        if (raiseConnectionAlert && workerGeneration.isCurrent(claim) &&
            lossAlertSent.compareAndSet(false, true)
        ) {
            try {
                playAlert(terminal = true)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to raise terminal connection alert", e)
            }
        }
        if (!workerGeneration.isCurrent(claim)) return
        try {
            onError?.invoke()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Listen error callback failed", e)
        } finally {
            synchronized(sessionStateLock) {
                if (workerGeneration.isCurrent(claim)) {
                    try {
                        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    } finally {
                        stopSelfResult(claim.startId)
                    }
                }
            }
        }
    }

    private sealed interface StreamResult {
        data object Reconnect : StreamResult
        data object Stopped : StreamResult
        data class Fatal(val type: ListenSessionError) : StreamResult
    }

    private data class TerminalOwnership(
        val sessionToken: Long?,
        val wifiDirectToken: Long?
    )

    private fun performHandshake(
        socket: Socket,
        pairingCode: CharArray,
        expectedIdentity: ExpectedChildIdentity?
    ): SessionInfo? {
        val code = PairingCode.normalize(pairingCode.concatToString())
        if (!PairingCode.isValid(code)) {
            Log.e(TAG, "A valid pairing code is required")
            return null
        }
        var baseKey: ByteArray? = null
        var authKey: ByteArray? = null
        return try {
            val deadline = HandshakeDeadline(AUTH_TIMEOUT_MS, SystemClock::elapsedRealtime)
            val input = deadline.input(socket.getInputStream()) { socket.soTimeout = it }
            val hello = Handshake.readChildHello(input)
                ?: run {
                    Log.e(TAG, "Failed to read handshake from child device")
                    return null
                }
            if (!Handshake.isCompatible(hello.protocolVersion, hello.capabilities)) {
                Log.e(TAG, "Child protocol or capabilities are not supported")
                return null
            }
            if (expectedIdentity != null && !expectedIdentity.matches(hello)) {
                Log.e(TAG, "Authenticated child identity does not match the trusted child")
                return null
            }
            deadline.check()
            baseKey = CryptoHelper.deriveKey(code, hello.kdfSalt)
            deadline.check()
            authKey = CryptoHelper.deriveAuthKey(baseKey)
            val response = Handshake.createParentResponse(hello, authKey)
            Handshake.writeParentResponse(socket.getOutputStream(), response)
            deadline.check()
            val ack = Handshake.readChildAck(input)
                ?: run {
                    Log.e(TAG, "Child did not authenticate the session")
                    return null
                }
            if (!Handshake.verifyChildAck(hello, response, ack, authKey)) {
                Log.e(TAG, "Child authentication proof is invalid")
                return null
            }
            deadline.check()
            val streamKey = CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(hello))
            deadline.check()
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            SessionInfo(
                hello.sessionId,
                streamKey,
                ack.firstSequence,
                hello.childId,
                hello.pairingId
            )
        } catch (e: IOException) {
            Log.e(TAG, "Handshake failed", e)
            null
        } finally {
            authKey?.fill(0)
            baseKey?.fill(0)
        }
    }

    private fun postReconnecting(attempt: Int, claim: WorkerClaim) {
        val status = getString(R.string.reconnecting_status, attempt, MAX_RECONNECT_ATTEMPTS)
        val published = synchronized(sessionStateLock) {
            if (!isWorkerActive(claim) || terminalFailure) {
                false
            } else {
                Log.i(TAG, status)
                ListenServiceRepository.updateReconnecting(attempt, MAX_RECONNECT_ATTEMPTS)
                true
            }
        }
        if (!published) return
        onStatusChange?.let { callback ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (isWorkerActive(claim)) callback(status)
            }
        }
    }

    private fun streamAudio(
        socket: Socket,
        sessionInfo: SessionInfo,
        claim: WorkerClaim,
        hasVerifiedAudio: AtomicBoolean,
        verifiedAudioThisConnection: AtomicBoolean
    ): StreamResult {
        Log.i(TAG, "Setting up stream")
        if (bufferSize <= 0 || bufferSize > MAX_AUDIO_TRACK_BUFFER_BYTES) {
            Log.e(TAG, "AudioTrack reported an invalid buffer size")
            return StreamResult.Fatal(ListenSessionError.Playback)
        }
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
            return StreamResult.Fatal(ListenSessionError.Playback)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioTrack initialization failed", e)
            return StreamResult.Fatal(ListenSessionError.Playback)
        }

        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not initialized properly")
            audioTrack.release()
            return StreamResult.Fatal(ListenSessionError.Playback)
        }

        try {
            audioTrack.play()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start AudioTrack playback", e)
            audioTrack.release()
            return StreamResult.Fatal(ListenSessionError.Playback)
        }

        val inputStream = try {
            socket.getInputStream()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get input stream from socket", e)
            try {
                audioTrack.stop()
            } catch (stopError: IllegalStateException) {
                Log.d(TAG, "AudioTrack already stopped")
            }
            audioTrack.release()
            return StreamResult.Reconnect
        }

        val streamActive = synchronized(sessionStateLock) {
            if (!isWorkerActive(claim) || terminalFailure) {
                false
            } else {
                deliveryHealth.arm()
                true
            }
        }
        if (!streamActive) {
            try {
                audioTrack.stop()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "AudioTrack already stopped")
            }
            audioTrack.release()
            return StreamResult.Stopped
        }

        val frameSequence = FrameSequence(sessionInfo.firstSequence)
        val jitterBuffer = JitterBuffer()
        val encryptedPayloadBuffer = ByteArray(FrameCodec.MAX_ENCRYPTED_AUDIO_SIZE)

        val streamRunning = AtomicBoolean(true)
        val playbackFailure = AtomicReference<ListenSessionError?>(null)
        val failPlayback: (ListenSessionError) -> Unit = { type ->
            playbackFailure.compareAndSet(null, type)
            streamRunning.set(false)
            try {
                socket.close()
            } catch (e: IOException) {
                Log.d(TAG, "Failed to close socket after playback failure", e)
            }
        }
        val playbackThread = Thread {
            val decodedBuffer = ShortArray(FrameCodec.MAX_G711_AUDIO_SIZE)
            val concealmentBuffer = ShortArray(AudioFrameTiming.FRAME_SAMPLES)
            val concealer = PacketLossConcealer()
            Log.i(TAG, "Starting playback from jitter buffer")
            try {
                while (streamRunning.get() && isWorkerActive(claim) && !Thread.currentThread().isInterrupted) {
                    val jitterFrame = jitterBuffer.getFrame(AudioFrameTiming.FRAME_DURATION_MS.toLong())
                    val realFrame = jitterFrame != null
                    val playbackBuffer: ShortArray
                    val sampleCount: Int
                    if (jitterFrame != null) {
                        playbackBuffer = decodedBuffer
                        sampleCount = try {
                            AudioCodecDefines.CODEC.decode(
                                decodedBuffer,
                                jitterFrame.ulawData,
                                jitterFrame.ulawData.size,
                                0
                            )
                        } catch (e: RuntimeException) {
                            Log.e(TAG, "Audio frame decoding failed", e)
                            failPlayback(ListenSessionError.Decoding)
                            break
                        }
                        if (sampleCount <= 0) {
                            Log.e(TAG, "Audio decoder produced no samples")
                            failPlayback(ListenSessionError.Decoding)
                            break
                        }
                    } else {
                        if (!jitterBuffer.hasPlaybackStarted()) continue
                        playbackBuffer = concealmentBuffer
                        sampleCount = concealer.concealInto(concealmentBuffer)
                    }

                    val writeResult = writeAllAudioSamples(
                        sampleCount = sampleCount,
                        write = { offset, count ->
                            audioTrack.write(playbackBuffer, offset, count, AudioTrack.WRITE_NON_BLOCKING)
                        },
                        elapsedRealtime = SystemClock::elapsedRealtime,
                        pauseAfterNoProgress = { Thread.sleep(AUDIO_WRITE_RETRY_MS) }
                    )
                    when (writeResult) {
                        AudioWriteResult.Complete -> {
                            if (!realFrame) continue
                            val concealmentSamples = sampleCount.coerceAtMost(AudioFrameTiming.FRAME_SAMPLES)
                            concealer.onRealFrame(
                                decodedBuffer,
                                concealmentSamples,
                                sampleCount - concealmentSamples
                            )
                            val delivered = synchronized(sessionStateLock) {
                                if (isWorkerActive(claim) && !terminalFailure && streamRunning.get() &&
                                    deliveryHealth.markDelivered()
                                ) {
                                    volumeHistory.onAudioData(decodedBuffer, 0, sampleCount)
                                    hasVerifiedAudio.set(true)
                                    verifiedAudioThisConnection.set(true)
                                    lossAlertSent.set(false)
                                    ListenServiceRepository.updateListening()
                                    notificationManager.cancel(ALERT_NOTIFICATION_ID)
                                    true
                                } else {
                                    false
                                }
                            }
                            if (delivered && isWorkerActive(claim)) onUpdate?.invoke()
                        }
                        AudioWriteResult.Failed,
                        AudioWriteResult.Stalled -> {
                            Log.e(TAG, "AudioTrack failed to write a complete decoded frame: $writeResult")
                            failPlayback(ListenSessionError.Playback)
                            break
                        }
                        AudioWriteResult.Interrupted -> break
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Playback thread interrupted")
                Thread.currentThread().interrupt()
            } catch (e: RuntimeException) {
                Log.e(TAG, "Playback thread failed", e)
                failPlayback(ListenSessionError.Playback)
            }
        }

        try {
            playbackThread.start()
            val senderClock = SenderTimestampClock()
            while (streamRunning.get() && isWorkerActive(claim) && !Thread.currentThread().isInterrupted) {
                val header = try {
                    FrameHeader.readFrom(inputStream)
                } catch (e: SocketTimeoutException) {
                    Log.w(TAG, "Timed out while reading frame header; reconnecting")
                    return StreamResult.Reconnect
                } ?: run {
                    Log.e(TAG, "Failed to read frame header")
                    return playbackFailure.get()?.let(StreamResult::Fatal) ?: StreamResult.Reconnect
                }

                if (!FrameCodec.isValidHeader(header)) {
                    Log.e(TAG, "Rejected frame with invalid flags or payload length")
                    return StreamResult.Fatal(ListenSessionError.Decoding)
                }
                val sequenceDecision = frameSequence.classify(header.seqNum)
                if (sequenceDecision is FrameSequenceDecision.Replay ||
                    sequenceDecision is FrameSequenceDecision.InvalidFirst
                ) {
                    Log.e(TAG, "Rejected replayed or invalid initial frame sequence")
                    return StreamResult.Fatal(ListenSessionError.Decoding)
                }

                var bytesRead = 0
                while (bytesRead < header.payloadLength) {
                    val chunk = inputStream.read(
                        encryptedPayloadBuffer,
                        bytesRead,
                        header.payloadLength - bytesRead
                    )
                    if (chunk < 0) {
                        Log.e(TAG, "Incomplete payload read")
                        return playbackFailure.get()?.let(StreamResult::Fatal) ?: StreamResult.Reconnect
                    }
                    bytesRead += chunk
                }

                val frame = try {
                    FrameCodec.decodeFrame(
                        header,
                        encryptedPayloadBuffer,
                        0,
                        header.payloadLength,
                        sessionInfo.streamKey,
                        sessionInfo.sessionId
                    )
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Failed to decode frame", e)
                    return StreamResult.Fatal(ListenSessionError.Decoding)
                }
                    ?: run {
                        Log.e(TAG, "Failed to decode frame")
                        return StreamResult.Fatal(ListenSessionError.Decoding)
                    }

                val acceptedSequence = frameSequence.acceptAuthenticated(header.seqNum)
                if (acceptedSequence is FrameSequenceDecision.InvalidFirst ||
                    acceptedSequence is FrameSequenceDecision.Replay
                ) {
                    Log.e(TAG, "Stream sequence space exhausted")
                    return StreamResult.Fatal(ListenSessionError.Decoding)
                }
                if (acceptedSequence is FrameSequenceDecision.ForwardGap) {
                    Log.w(
                        TAG,
                        "Authenticated stream sequence gap: ${acceptedSequence.missingFrames} value(s) skipped"
                    )
                }

                if (frame.isHeartbeat) {
                    Log.d(TAG, "Received authenticated heartbeat frame")
                    continue
                }

                val receiveTime = SystemClock.elapsedRealtime()
                val frameAge = senderClock.frameAgeMillis(receiveTime, frame.timestampMs)
                if (frameAge > AudioCodecDefines.MAX_FRAME_AGE_MS) {
                    Log.d(TAG, "Dropping stale frame: ${frameAge}ms old")
                    continue
                }

                val addResult = jitterBuffer.addFrame(
                    JitterBuffer.DecodedFrame(frame.seqNum, frame.timestampMs, frame.ulawData, receiveTime)
                )
                if (addResult != JitterBuffer.AddResult.Accepted) {
                    val droppedFrames = jitterBuffer.getDroppedFrameCount()
                    if (addResult == JitterBuffer.AddResult.AcceptedAfterDroppingOldest &&
                        (droppedFrames == 1 || droppedFrames % JITTER_OVERFLOW_LOG_INTERVAL == 0)
                    ) {
                        Log.w(TAG, "Jitter buffer full; dropped $droppedFrames frame(s) total")
                    }
                }
            }

            return playbackFailure.get()?.let(StreamResult::Fatal)
                ?: if (isWorkerActive(claim)) StreamResult.Reconnect else StreamResult.Stopped
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            return playbackFailure.get()?.let(StreamResult::Fatal)
                ?: if (isWorkerActive(claim)) StreamResult.Reconnect else StreamResult.Stopped
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
            clearCurrentSocket(socket)
        }
    }

    private fun playAlert(terminal: Boolean) {
        if (terminal) sendConnectionLostAlert() else sendAudioInterruptedAlert()
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
        private const val FOREGROUND_REQUEST_CODE = 0
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val SOCKET_READ_TIMEOUT_MS = 1000
        private const val AUTH_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val DELIVERY_HEALTH_POLL_MS = 250L
        private const val AUDIO_WRITE_RETRY_MS = 5L
        private const val JITTER_OVERFLOW_LOG_INTERVAL = 50
        private const val MAX_AUDIO_TRACK_BUFFER_BYTES = 128_000
        private val VALID_PORT_RANGE = 1..65535
    }

    private data class ListenConnection(
        val requestId: String?,
        val address: String,
        val port: Int,
        val name: String,
        val pairingCode: CharArray,
        val expectedIdentity: ExpectedChildIdentity?,
        val rememberAfterAuthentication: Boolean
    )

    private sealed interface ConnectionResolution {
        data class Available(val connection: ListenConnection) : ConnectionResolution
        data object Missing : ConnectionResolution
        data object CredentialUnavailable : ConnectionResolution
    }
}
