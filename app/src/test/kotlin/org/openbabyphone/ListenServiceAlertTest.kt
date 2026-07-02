package org.openbabyphone

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
class ListenServiceAlertTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
    }

    @Test
    fun `foreground notification resume intent does not contain pairing code`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "192.168.1.1")
            putExtra("port", 10000)
            putExtra("pairingCode", "secretCode123")
        }

        val controller = Robolectric.buildService(ListenService::class.java, intent)
        controller.create()
        controller.startCommand(0, 0)

        val nm = context.getSystemService(NotificationManager::class.java)
        val shadowNm = org.robolectric.Shadows.shadowOf(nm) as ShadowNotificationManager
        val notifications = shadowNm.activeNotifications
        assertTrue("Foreground notification should be posted, got ${notifications.size}", notifications.isNotEmpty())

        val foregroundEntry = notifications.firstOrNull { it.id == ListenService.ID }
        assertNotNull("Foreground service notification should exist", foregroundEntry)

        val contentIntent = foregroundEntry!!.notification.contentIntent
        assertNotNull("Content intent should exist", contentIntent)

        val shadowPendingIntent = org.robolectric.Shadows.shadowOf(contentIntent) as ShadowPendingIntent
        val savedIntent = shadowPendingIntent.savedIntent
        assertNotNull("Saved intent should exist", savedIntent)
        val data: Uri? = savedIntent!!.data
        assertNotNull("Deep link URI should exist", data)
        assertFalse(
            "Resume URI must not contain pairingCode",
            data!!.queryParameterNames.contains("pairingCode")
        )
        assertTrue("Resume URI should have resumeOnly=true", data.getQueryParameter("resumeOnly") == "true")
        assertTrue("Resume URI should have address", data.getQueryParameter("address") == "192.168.1.1")

        controller.destroy()
    }

    @Test
    fun `terminal listen failure removes foreground notification and keeps alert notification`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "192.168.1.1")
            putExtra("port", 0)
            putExtra("pairingCode", "secretCode123")
        }

        val controller = Robolectric.buildService(ListenService::class.java, intent)
        controller.create()
        controller.startCommand(0, 0)

        val nm = context.getSystemService(NotificationManager::class.java)
        val shadowNm = org.robolectric.Shadows.shadowOf(nm) as ShadowNotificationManager
        val notifications = shadowNm.activeNotifications

        assertNull(
            "Foreground service notification should be removed after terminal failure",
            notifications.firstOrNull { it.id == ListenService.ID }
        )
        val alert = notifications.firstOrNull {
            it.notification.extras.getString(Notification.EXTRA_TITLE) ==
                context.getString(R.string.connection_lost_alert_title)
        }
        assertNotNull("Connection-lost alert notification should remain visible", alert)
        assertEquals(
            context.getString(R.string.connection_lost_alert_text),
            alert!!.notification.extras.getString(Notification.EXTRA_TEXT)
        )

        controller.destroy()
    }
}
