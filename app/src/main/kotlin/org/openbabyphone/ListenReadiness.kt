/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class ListenReadinessStatus(
    val mediaVolumeMuted: Boolean,
    val postNotificationsGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val alertChannelEnabled: Boolean,
    val likelyExternalOutput: Boolean
)

enum class ListenReadinessNotice {
    MutedMediaVolume,
    ConnectionAlertsDisabled,
    ExternalAudioOutput
}

internal fun selectListenReadinessNotice(status: ListenReadinessStatus): ListenReadinessNotice? = when {
    status.mediaVolumeMuted -> ListenReadinessNotice.MutedMediaVolume
    !status.postNotificationsGranted || !status.appNotificationsEnabled || !status.alertChannelEnabled ->
        ListenReadinessNotice.ConnectionAlertsDisabled
    status.likelyExternalOutput -> ListenReadinessNotice.ExternalAudioOutput
    else -> null
}

object ListenReadiness {
    fun status(context: Context): ListenReadinessStatus {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val alertChannel = notificationManager.getNotificationChannel(ListenService.ALERT_CHANNEL_ID)
        return ListenReadinessStatus(
            mediaVolumeMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0,
            postNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            alertChannelEnabled = alertChannel == null ||
                alertChannel.importance != NotificationManager.IMPORTANCE_NONE,
            likelyExternalOutput = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { isLikelyExternalOutput(it.type) }
        )
    }
}

internal fun isLikelyExternalOutput(type: Int): Boolean = type in setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET
)
