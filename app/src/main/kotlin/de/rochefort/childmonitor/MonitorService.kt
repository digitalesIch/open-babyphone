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
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Locale

class MonitorService : Service() {
    private val binder: IBinder = MonitorBinder()
    private lateinit var nsdManager: NsdManager
    private var registrationListener: RegistrationListener? = null
    private var currentSocket: ServerSocket? = null
    private var connectionToken: Any? = null
    private var currentPort = 0
    private lateinit var notificationManager: NotificationManager
    private var monitorThread: Thread? = null
    private lateinit var pairingPin: String
    var monitorActivity: MonitorActivity? = null

    fun updateMonitorActivity() {
        val ma = this.monitorActivity ?: return
        ma.runOnUiThread {
            val pairingPinText = ma.findViewById<TextView>(R.id.pairingPin)
            pairingPinText.text = pairingPin
        }
    }

    private fun authenticateParent(socket: Socket): Boolean {
        return try {
            socket.soTimeout = AUTH_TIMEOUT_MS
            val providedPin = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()?.trim()
            val authenticated = providedPin == pairingPin
            if (!authenticated) {
                Log.w(TAG, "Rejected parent connection with invalid pairing PIN")
            }
            authenticated
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

    private fun serviceConnection(socket: Socket) {
        val ma = this.monitorActivity
        ma?.runOnUiThread {
            val statusText = ma.findViewById<TextView>(R.id.textStatus)
            statusText.setText(R.string.streaming)
        }
        val frequency: Int = AudioCodecDefines.FREQUENCY
        val channelConfiguration: Int = AudioCodecDefines.CHANNEL_CONFIGURATION_IN
        val audioEncoding: Int = AudioCodecDefines.ENCODING
        val bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
        val audioRecord: AudioRecord = try {
            AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    frequency,
                    channelConfiguration,
                    audioEncoding,
                    bufferSize
            )
        } catch (e: SecurityException) {
            // This should never happen, we asked for permission before
            throw RuntimeException(e)
        }
        val pcmBufferSize = bufferSize * 2
        val pcmBuffer = ShortArray(pcmBufferSize)
        val ulawBuffer = ByteArray(pcmBufferSize)
        try {
            audioRecord.startRecording()
            val out = socket.getOutputStream()
            socket.sendBufferSize = pcmBufferSize
            Log.d(TAG, "Socket send buffer size: " + socket.sendBufferSize)
            while (socket.isConnected && (this.currentSocket != null) && !Thread.currentThread().isInterrupted) {
                val read = audioRecord.read(pcmBuffer, 0, bufferSize)
                val encoded: Int = AudioCodecDefines.CODEC.encode(pcmBuffer, read, ulawBuffer, 0)
                out.write(ulawBuffer, 0, encoded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
        } finally {
            audioRecord.stop()
        }
    }

    override fun onCreate() {
        Log.i(TAG, "ChildMonitor start")
        super.onCreate()
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        this.nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
        this.currentPort = 10000
        this.currentSocket = null
        this.pairingPin = generatePairingPin()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start id $startId: $intent")
        // Display a notification about us starting.  We put an icon in the status bar.
        createNotificationChannel()
        val n = buildNotification()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0 // Keep the linter happy
        ServiceCompat.startForeground(this, ID, n, foregroundServiceType)
        ensureMonitorThread()
        return START_REDELIVER_INTENT
    }

    private fun ensureMonitorThread() {
        var mt = this.monitorThread
        if (mt != null && mt.isAlive) {
            return
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
                        var authenticatedConnectionHandled = false
                        while (!authenticatedConnectionHandled &&
                                this.connectionToken == currentToken &&
                                !Thread.currentThread().isInterrupted) {
                            serverSocket.accept().use { socket ->
                                Log.i(TAG, "Connection from parent device received")

                                if (authenticateParent(socket)) {
                                    // We now have an authenticated client connection.
                                    // Unregister so no other clients will attempt to connect.
                                    unregisterService()
                                    serviceConnection(socket)
                                    authenticatedConnectionHandled = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (this.connectionToken == currentToken) {
                        // Just in case
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
                            val pairingPinText = ma.findViewById<TextView>(R.id.pairingPin)
                            pairingPinText.text = pairingPin
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
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            this.notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        val text: CharSequence = "Child Device"
        // Set the info for the views that show in the notification panel.
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
        b.setSmallIcon(R.drawable.listening_notification) // the status icon
                .setOngoing(true)
                .setTicker(text) // the status text
                .setContentTitle(text) // the label of the entry
        return b.build()
    }

    override fun onDestroy() {
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

        // Cancel the persistent notification.
        this.notificationManager.cancel(R.string.listening)
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
        private const val AUTH_TIMEOUT_MS = 10_000
        private val PIN_RANDOM = SecureRandom()

        private fun generatePairingPin(): String {
            return String.format(Locale.US, "%06d", PIN_RANDOM.nextInt(1_000_000))
        }
    }
}
