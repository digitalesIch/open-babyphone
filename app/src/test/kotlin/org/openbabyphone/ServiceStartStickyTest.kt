package org.openbabyphone

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
class ServiceStartStickyTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        ActiveListenSessionRegistry.clearForTests()
    }

    @Test
    fun `ListenService returns START_REDELIVER_INTENT`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("requestId", PendingConnections.store.put(
                PendingConnection("127.0.0.1", 10000, "Nursery", "code1234".toCharArray())
            ))
        }
        val controller = Robolectric.buildService(ListenService::class.java, intent)
        controller.create()
        val service = controller.get()
        val result = service.onStartCommand(intent, 0, 0)
        assertEquals(
            "ListenService should return START_REDELIVER_INTENT for restart after low-memory kill",
            android.app.Service.START_REDELIVER_INTENT,
            result
        )
        controller.destroy()
    }

    @Test
    fun `failed listen redelivery stops retry loop and requests user action`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("expectedChildId", "missing-child")
            putExtra("expectedPairingId", "missing-pairing")
        }
        val controller = Robolectric.buildService(ListenService::class.java, intent).create()

        val result = controller.get().onStartCommand(intent, Service.START_FLAG_REDELIVERY, 42)

        assertEquals(Service.START_NOT_STICKY, result)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = (shadowOf(manager) as ShadowNotificationManager).activeNotifications
            .firstOrNull {
                it.notification.extras.getString(Notification.EXTRA_TITLE) ==
                    context.getString(R.string.listen_recovery_action_title)
            }
        assertNotNull(notification)
        val savedIntent = (shadowOf(notification!!.notification.contentIntent) as ShadowPendingIntent).savedIntent
        assertEquals(ListenResumeActivity::class.java.name, savedIntent.component?.className)
        assertFalse(savedIntent.hasExtra("pairingCode"))
        assertFalse(savedIntent.hasExtra("address"))
        val token = savedIntent.getLongExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, Long.MIN_VALUE)
        assertFalse(ActiveListenSessionRegistry.resolve(token)?.active ?: true)
        controller.destroy()
    }
}
