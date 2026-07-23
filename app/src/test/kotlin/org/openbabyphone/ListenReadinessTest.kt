package org.openbabyphone

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenReadinessTest {
    @Test
    fun `muted media volume has highest notice priority`() {
        assertEquals(
            ListenReadinessNotice.MutedMediaVolume,
            selectListenReadinessNotice(status(muted = true, notifications = false, external = true))
        )
    }

    @Test
    fun `disabled connection alerts precede external output`() {
        assertEquals(
            ListenReadinessNotice.ConnectionAlertsDisabled,
            selectListenReadinessNotice(status(notifications = false, external = true))
        )
    }

    @Test
    fun `external output is shown when audio and alerts are ready`() {
        assertEquals(
            ListenReadinessNotice.ExternalAudioOutput,
            selectListenReadinessNotice(status(external = true))
        )
    }

    @Test
    fun `ready listener has no notice`() {
        assertNull(selectListenReadinessNotice(status()))
    }

    @Test
    fun `wired bluetooth and usb devices are likely external outputs`() {
        listOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET
        ).forEach { assertEquals(true, isLikelyExternalOutput(it)) }
        assertEquals(false, isLikelyExternalOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }

    private fun status(
        muted: Boolean = false,
        notifications: Boolean = true,
        external: Boolean = false
    ) = ListenReadinessStatus(
        mediaVolumeMuted = muted,
        postNotificationsGranted = notifications,
        appNotificationsEnabled = notifications,
        alertChannelEnabled = notifications,
        likelyExternalOutput = external
    )
}
