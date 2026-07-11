package org.openbabyphone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object ServiceHeartbeatScheduler {
    const val EXTRA_HEARTBEAT = "org.openbabyphone.extra.HEARTBEAT"
    const val ACTION_MONITOR_HEARTBEAT = "org.openbabyphone.action.MONITOR_HEARTBEAT"
    const val ACTION_LISTEN_HEARTBEAT = "org.openbabyphone.action.LISTEN_HEARTBEAT"
    private const val MONITOR_REQUEST_CODE = 15801
    private const val LISTEN_REQUEST_CODE = 15802
    private const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L

    fun scheduleMonitor(context: Context) {
        schedule(context, monitorHeartbeatIntent(context), MONITOR_REQUEST_CODE)
    }

    fun scheduleListen(context: Context, restartIntent: Intent) {
        schedule(context, listenHeartbeatIntent(context, restartIntent), LISTEN_REQUEST_CODE)
    }

    fun cancelMonitor(context: Context) {
        cancel(context, monitorHeartbeatIntent(context), MONITOR_REQUEST_CODE)
    }

    fun cancelListen(context: Context) {
        cancel(context, listenHeartbeatIntent(context, Intent()), LISTEN_REQUEST_CODE)
    }

    internal fun monitorHeartbeatIntent(context: Context): Intent = Intent(context, ServiceHeartbeatReceiver::class.java)
        .setAction(ACTION_MONITOR_HEARTBEAT)

    internal fun listenHeartbeatIntent(context: Context, restartIntent: Intent): Intent =
        Intent(context, ServiceHeartbeatReceiver::class.java)
            .setAction(ACTION_LISTEN_HEARTBEAT)
            .putExtra("name", restartIntent.getStringExtra("name"))
            .putExtra("address", restartIntent.getStringExtra("address"))
            .putExtra("port", restartIntent.getIntExtra("port", 0))
            .putExtra("pairingCode", restartIntent.getStringExtra("pairingCode"))

    private fun schedule(context: Context, intent: Intent, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS,
            createPendingIntent(context, intent, requestCode)
        )
    }

    private fun cancel(context: Context, intent: Intent, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = findPendingIntent(context, intent, requestCode) ?: return
        alarmManager.cancel(pendingIntent)
    }

    private fun createPendingIntent(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun findPendingIntent(context: Context, intent: Intent, requestCode: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
}
