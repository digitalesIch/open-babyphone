/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

internal object ServiceRecoveryNotifier {
    private const val CHANNEL_ID = "service_recovery"
    private const val MONITOR_NOTIFICATION_ID = 902938411
    private const val LISTEN_NOTIFICATION_ID = 902938412

    fun notifyMonitorActionRequired(context: Context) {
        notify(
            context,
            MONITOR_NOTIFICATION_ID,
            context.getString(R.string.monitor_recovery_action_title),
            context.getString(R.string.monitor_recovery_action_text),
            Intent(context, MainActivity::class.java)
        )
    }

    fun notifyListenActionRequired(context: Context, sessionToken: Long?) {
        val intent = Intent(context, ListenResumeActivity::class.java).apply {
            sessionToken?.takeIf { it != Long.MIN_VALUE }
                ?.let { putExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, it) }
        }
        notify(
            context,
            LISTEN_NOTIFICATION_ID,
            context.getString(R.string.listen_recovery_action_title),
            context.getString(R.string.listen_recovery_action_text),
            intent
        )
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
        intent: Intent
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.service_recovery_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.listening_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        )
    }
}
