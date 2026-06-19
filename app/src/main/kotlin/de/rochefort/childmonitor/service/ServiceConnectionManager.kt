package de.rochefort.childmonitor.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.rochefort.childmonitor.ListenService
import de.rochefort.childmonitor.MonitorService
import de.rochefort.childmonitor.viewmodel.ListenViewModel
import de.rochefort.childmonitor.viewmodel.MonitorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ServiceConnectionManager {
    private val _monitorServiceConnected = MutableStateFlow(false)
    val monitorServiceConnected: StateFlow<Boolean> = _monitorServiceConnected.asStateFlow()

    private val _listenServiceConnected = MutableStateFlow(false)
    val listenServiceConnected: StateFlow<Boolean> = _listenServiceConnected.asStateFlow()

    fun bindMonitorService(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        viewModel: MonitorViewModel
    ) {
        val intent = Intent(context, MonitorService::class.java)
        context.startForegroundService(intent)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                _monitorServiceConnected.value = true
                // Service is running, Repository will be updated via callbacks in MonitorService
            }

            override fun onServiceDisconnected(className: ComponentName) {
                _monitorServiceConnected.value = false
            }
        }

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        lifecycleOwner.lifecycleScope.launch {
            // Monitor service updates repository directly
        }
    }

    fun bindListenService(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        viewModel: ListenViewModel,
        address: String,
        port: Int,
        name: String,
        pairingCode: String
    ) {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("address", address)
            putExtra("port", port)
            putExtra("name", name)
            putExtra("pairingCode", pairingCode)
        }
        context.startForegroundService(intent)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                _listenServiceConnected.value = true
                val binder = service as ListenService.ListenBinder
                val listenService = binder.service

                // Set up callbacks to update ViewModel
                listenService.onUpdate = {
                    val floatHistory = FloatArray(listenService.volumeHistory.size()) { i ->
                        listenService.volumeHistory[i].toFloat().coerceAtLeast(0f).coerceAtMost(1f)
                    }
                    val volumeNorm = listenService.volumeHistory.volumeNorm.toFloat()
                    viewModel.updateVolumeHistory(floatHistory, volumeNorm)
                }

                listenService.onError = {
                    // Error handled by Repository
                }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                _listenServiceConnected.value = false
            }
        }

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindMonitorService(context: Context, connection: ServiceConnection) {
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Service not bound
        }
    }

    fun unbindListenService(context: Context, connection: ServiceConnection) {
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Service not bound
        }
    }
}
