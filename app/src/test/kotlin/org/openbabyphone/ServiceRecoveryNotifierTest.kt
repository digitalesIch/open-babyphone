package org.openbabyphone

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
class ServiceRecoveryNotifierTest {
    private lateinit var context: Application
    private lateinit var notifications: ShadowNotificationManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    }

    @Test
    fun `successful monitor start can clear stale recovery notification`() {
        ServiceRecoveryNotifier.notifyMonitorActionRequired(context)
        assertTrue(hasNotification(R.string.monitor_recovery_action_title))

        ServiceRecoveryNotifier.cancelMonitorActionRequired(context)

        assertFalse(hasNotification(R.string.monitor_recovery_action_title))
    }

    @Test
    fun `successful listen start can clear stale recovery notification`() {
        ServiceRecoveryNotifier.notifyListenActionRequired(context, sessionToken = 7L)
        assertTrue(hasNotification(R.string.listen_recovery_action_title))

        ServiceRecoveryNotifier.cancelListenActionRequired(context)

        assertFalse(hasNotification(R.string.listen_recovery_action_title))
    }

    private fun hasNotification(titleResource: Int): Boolean =
        notifications.activeNotifications.any {
            it.notification.extras.getString(Notification.EXTRA_TITLE) == context.getString(titleResource)
        }
}
