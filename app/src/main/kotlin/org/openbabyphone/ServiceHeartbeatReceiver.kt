package org.openbabyphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ServiceHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ServiceHeartbeatScheduler.ACTION_MONITOR_HEARTBEAT -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitorService::class.java).putExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, true)
                )
            }
            ServiceHeartbeatScheduler.ACTION_LISTEN_HEARTBEAT -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ListenService::class.java).apply {
                        putExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, true)
                        putExtra("name", intent.getStringExtra("name"))
                        putExtra("address", intent.getStringExtra("address"))
                        putExtra("port", intent.getIntExtra("port", 0))
                        putExtra("pairingCode", intent.getStringExtra("pairingCode"))
                    }
                )
            }
        }
    }
}
