package org.openbabyphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class ServiceHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ServiceHeartbeatScheduler.ACTION_MONITOR_HEARTBEAT -> {
                val started = tryStartHeartbeatService {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, MonitorService::class.java)
                            .putExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, true)
                    )
                }
                if (!started) {
                    Log.w(TAG, "Android rejected background monitor recovery")
                    ServiceHeartbeatScheduler.scheduleMonitor(context)
                    ServiceRecoveryNotifier.notifyMonitorActionRequired(context)
                }
            }
            ServiceHeartbeatScheduler.ACTION_LISTEN_HEARTBEAT -> {
                val restartIntent = Intent(context, ListenService::class.java).apply {
                    putExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, true)
                    putExtra("expectedChildId", intent.getStringExtra("expectedChildId"))
                    putExtra("expectedPairingId", intent.getStringExtra("expectedPairingId"))
                }
                val started = tryStartHeartbeatService {
                    ContextCompat.startForegroundService(context, restartIntent)
                }
                if (!started) {
                    Log.w(TAG, "Android rejected background listen recovery")
                    var token = intent.takeIf {
                        it.hasExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN)
                    }?.getLongExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, Long.MIN_VALUE)
                    if (token == null || ActiveListenSessionRegistry.resolve(token) == null) {
                        val childId = intent.getStringExtra("expectedChildId").orEmpty()
                        val pairingId = intent.getStringExtra("expectedPairingId").orEmpty()
                        if (childId.isNotBlank() && pairingId.isNotBlank()) {
                            token = ActiveListenSessionRegistry.register(
                                ExpectedChildIdentity(childId, pairingId)
                            ).also(ActiveListenSessionRegistry::markInactive)
                        }
                    }
                    ServiceHeartbeatScheduler.scheduleListen(context, restartIntent, token)
                    ServiceRecoveryNotifier.notifyListenActionRequired(context, token)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ServiceHeartbeat"
    }
}

internal fun tryStartHeartbeatService(start: () -> Unit): Boolean = try {
    start()
    true
} catch (_: SecurityException) {
    false
} catch (_: IllegalStateException) {
    false
}
