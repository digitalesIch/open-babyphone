package org.openbabyphone

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
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
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState

@RunWith(RobolectricTestRunner::class)
class ListenServiceAlertTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences("trusted_children", Application.MODE_PRIVATE).edit().clear().apply()
        PendingConnections.store.clear()
        ListenServiceRepository.reset()
    }

    @Test
    fun `foreground notification resume intent does not contain pairing code`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("requestId", PendingConnections.store.put(
                PendingConnection("192.168.1.1", 10000, "Nursery", "secretCode123".toCharArray())
            ))
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
        assertEquals(ListenResumeActivity::class.java.name, savedIntent!!.component?.className)
        assertNull(savedIntent.data)
        assertFalse(savedIntent.hasExtra("pairingCode"))
        assertFalse(savedIntent.hasExtra("address"))

        controller.destroy()
    }

    @Test
    fun `alert notification resume intent does not contain pairing code`() {
        val controller = Robolectric.buildService(ListenService::class.java)
        controller.create()
        val service = controller.get()
        val token = ActiveListenSessionRegistry.register(
            ExpectedChildIdentity("child-1", "pair-1")
        )
        setPrivateField(service, "registeredSessionToken", token)
        service.javaClass.getDeclaredMethod("sendConnectionLostAlert").apply {
            isAccessible = true
            invoke(service)
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        val shadowNm = org.robolectric.Shadows.shadowOf(nm) as ShadowNotificationManager
        val notifications = shadowNm.activeNotifications

        val alert = notifications.firstOrNull {
            it.notification.extras.getString(Notification.EXTRA_TITLE) ==
                context.getString(R.string.connection_lost_alert_title)
        }
        assertNotNull("Connection-lost alert notification should exist", alert)

        val contentIntent = alert!!.notification.contentIntent
        assertNotNull("Alert content intent should exist", contentIntent)

        val shadowPendingIntent = org.robolectric.Shadows.shadowOf(contentIntent) as ShadowPendingIntent
        val savedIntent = shadowPendingIntent.savedIntent
        assertNotNull("Saved intent should exist", savedIntent)
        assertEquals(ListenResumeActivity::class.java.name, savedIntent!!.component?.className)
        assertNull(savedIntent.data)
        assertFalse(savedIntent.hasExtra("pairingCode"))
        assertEquals(token, savedIntent.getLongExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, -1L))

        controller.destroy()
    }

    @Test
    fun `initial unreachable failure removes foreground notification without false loss alert`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("requestId", "missing-request")
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
        assertNull("Initial connection failure must not be announced as post-connect loss", alert)

        controller.destroy()

        assertEquals(
            ListenSessionState.Error(ListenSessionError.Unreachable, context.getString(R.string.disconnected)),
            ListenServiceRepository.sessionState.value
        )
    }

    @Test
    fun `rapid null connection retry resets terminal latch for new start id`() {
        val first = Intent(context, ListenService::class.java).putExtra("requestId", "missing-one")
        val retry = Intent(context, ListenService::class.java).putExtra("requestId", "missing-two")
        val controller = Robolectric.buildService(ListenService::class.java, first).create()
        var terminalCallbacks = 0
        controller.get().onError = { terminalCallbacks++ }

        controller.get().onStartCommand(first, 0, 41)
        controller.get().onStartCommand(retry, 0, 42)

        assertEquals(2, terminalCallbacks)
        assertTrue(ListenServiceRepository.sessionState.value is ListenSessionState.Error)
        controller.destroy()
    }

    @Test
    fun `service destruction after terminal failure retains pending request for retry`() {
        val requestId = PendingConnections.store.put(
            PendingConnection("host", 10000, "Nursery", "code1234".toCharArray())
        )
        val controller = Robolectric.buildService(ListenService::class.java).create()
        setPrivateField(controller.get(), "activeRequestId", requestId)

        controller.destroy()

        assertTrue(PendingConnections.store.contains(requestId))
        assertEquals("code1234", PendingConnections.store.lease(requestId)?.pairingCode?.concatToString())
    }

    private fun setPrivateField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }
}
