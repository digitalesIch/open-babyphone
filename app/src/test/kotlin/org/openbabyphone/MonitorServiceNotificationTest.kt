package org.openbabyphone

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
class MonitorServiceNotificationTest {
    @Test
    fun `ongoing monitor notification opens app without session secrets`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val method = MonitorService::class.java.getDeclaredMethod("buildNotification").apply {
            isAccessible = true
        }

        val notification = method.invoke(controller.get()) as Notification
        val contentIntent = notification.contentIntent

        assertNotNull(contentIntent)
        val savedIntent = org.robolectric.Shadows.shadowOf(contentIntent) as ShadowPendingIntent
        val intent = savedIntent.savedIntent
        assertNotNull(intent)
        assertEquals(MainActivity::class.java.name, intent!!.component?.className)
        assertEquals(MainActivity.ACTION_RESUME_MONITOR, intent.action)
        assertNull(intent.data)
        assertFalse(intent.hasExtra("pairingCode"))
        assertFalse(intent.hasExtra("address"))

        controller.destroy()
    }
}
