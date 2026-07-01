package org.openbabyphone.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import org.openbabyphone.ListenService
import org.openbabyphone.MonitorService
import org.openbabyphone.viewmodel.ListenViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

object ServiceConnectionManager {
    data class ServiceBinding(
        val intent: Intent,
        val connection: ServiceConnection,
        val bound: Boolean,
        val clearCallbacks: () -> Unit = {}
    )

    private val _monitorServiceConnected = MutableStateFlow(false)
    val monitorServiceConnected: StateFlow<Boolean> = _monitorServiceConnected.asStateFlow()

    private val _listenServiceConnected = MutableStateFlow(false)
    val listenServiceConnected: StateFlow<Boolean> = _listenServiceConnected.asStateFlow()

    fun bindMonitorService(
        context: Context
    ): ServiceBinding {
        val intent = Intent(context, MonitorService::class.java)
        ContextCompat.startForegroundService(context, intent)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                _monitorServiceConnected.value = true
            }

            override fun onServiceDisconnected(className: ComponentName) {
                _monitorServiceConnected.value = false
            }
        }

        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        return ServiceBinding(intent, connection, bound)
    }

    fun bindListenService(
        context: Context,
        viewModel: ListenViewModel,
        address: String,
        port: Int,
        name: String,
        pairingCode: String,
        resumeOnly: Boolean = false
    ): ServiceBinding {
        val intent = Intent(context, ListenService::class.java).apply {
            if (!resumeOnly) {
                putExtra("address", address)
                putExtra("port", port)
                putExtra("name", name)
                putExtra("pairingCode", pairingCode)
            }
        }
        if (!resumeOnly) {
            ContextCompat.startForegroundService(context, intent)
        }

        var serviceRef: WeakReference<ListenService>? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                _listenServiceConnected.value = true
                val binder = service as ListenService.ListenBinder
                val listenService = binder.service
                serviceRef = WeakReference(listenService)

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
                serviceRef?.get()?.clearCallbacks()
                serviceRef = null
            }
        }

        val flags = if (resumeOnly) 0 else Context.BIND_AUTO_CREATE
        val bound = context.bindService(intent, connection, flags)
        return ServiceBinding(
            intent = intent,
            connection = connection,
            bound = bound,
            clearCallbacks = { serviceRef?.get()?.clearCallbacks() }
        )
    }

    fun unbindAndStopService(context: Context, binding: ServiceBinding) {
        binding.clearCallbacks()
        if (binding.bound) {
            try {
                context.unbindService(binding.connection)
            } catch (e: IllegalArgumentException) {
                // Service was already unbound.
            }
        }
        context.stopService(binding.intent)
    }

    fun unbindService(context: Context, binding: ServiceBinding) {
        binding.clearCallbacks()
        if (binding.bound) {
            try {
                context.unbindService(binding.connection)
            } catch (e: IllegalArgumentException) {
                // Service was already unbound.
            }
        }
    }
}
