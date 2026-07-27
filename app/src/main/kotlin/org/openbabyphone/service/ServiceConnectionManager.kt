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
        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                _monitorServiceConnected.value = true
            }

            override fun onServiceDisconnected(className: ComponentName) {
                _monitorServiceConnected.value = false
            }
        }

        val bound = try {
            context.bindService(intent, connection, 0)
        } catch (exception: RuntimeException) {
            false
        }
        if (!bound) {
            publishMonitorStartupFailure(context)
            try {
                stopMonitorService(context)
            } catch (exception: RuntimeException) {
                // The failed start or bind may mean there is no service to stop.
            }
        }
        return ServiceBinding(intent, connection, bound)
    }

    fun startMonitorService(context: Context): Boolean = try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, MonitorService::class.java)
        )
        true
    } catch (exception: RuntimeException) {
        publishMonitorStartupFailure(context)
        false
    }

    fun bindListenService(
        context: Context,
        viewModel: ListenViewModel,
        requestId: String,
        expectedChildId: String = "",
        expectedPairingId: String = "",
        resumeOnly: Boolean = false
    ): ServiceBinding {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("requestId", requestId)
            putExtra("expectedChildId", expectedChildId)
            putExtra("expectedPairingId", expectedPairingId)
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

        var bound = if (resumeOnly) {
            try {
                context.bindService(Intent(context, ListenService::class.java), connection, 0)
            } catch (exception: RuntimeException) {
                false
            }
        } else {
            false
        }
        val validExpectedIdentity = expectedChildId.isBlank() == expectedPairingId.isBlank()
        val validConnection = (requestId.isNotBlank() || expectedChildId.isNotBlank()) && validExpectedIdentity
        if (!bound && validConnection) {
            val started = try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (exception: RuntimeException) {
                publishListenStartupFailure(context)
                false
            }
            if (started) {
                bound = try {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (exception: RuntimeException) {
                    false
                }
                if (!bound) {
                    try {
                        context.stopService(intent)
                    } catch (exception: RuntimeException) {
                        // The failed bind may mean there is no service to stop.
                    }
                    publishListenStartupFailure(context)
                }
            }
        }
        return ServiceBinding(
            intent = intent,
            connection = connection,
            bound = bound,
            clearCallbacks = { serviceRef?.get()?.clearCallbacks() }
        )
    }

    fun disposeServiceBinding(context: Context, binding: ServiceBinding) {
        unbindService(context, binding)
    }

    fun stopMonitorService(context: Context) {
        context.stopService(Intent(context, MonitorService::class.java))
    }

    fun stopListenService(context: Context) {
        context.stopService(Intent(context, ListenService::class.java))
    }

    private fun publishMonitorStartupFailure(context: Context) {
        MonitorServiceRepository.updateError(
            MonitorSessionError.Startup,
            context.getString(org.openbabyphone.R.string.monitoring_start_failed)
        )
    }

    private fun publishListenStartupFailure(context: Context) {
        ListenServiceRepository.updateError(
            ListenSessionError.Unreachable,
            context.getString(org.openbabyphone.R.string.disconnected)
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
