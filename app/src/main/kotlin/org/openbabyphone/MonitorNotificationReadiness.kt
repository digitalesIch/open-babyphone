/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class MonitorNotificationStatus(
    val postNotificationsGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val foregroundChannelEnabled: Boolean
) {
    val shouldWarn: Boolean
        get() = !postNotificationsGranted || !appNotificationsEnabled || !foregroundChannelEnabled
}

object MonitorNotificationReadiness {
    fun shouldWarn(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(MonitorService.CHANNEL_ID)
        } else {
            null
        }
        return MonitorNotificationStatus(
            postNotificationsGranted = permissionGranted,
            appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            foregroundChannelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        ).shouldWarn
    }
}
