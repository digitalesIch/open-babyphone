package org.openbabyphone

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ServiceHeartbeatSchedulerTest {

    private lateinit var context: Application
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        alarmManager = context.getSystemService(Application.ALARM_SERVICE) as AlarmManager
    }

    @Test
    fun `scheduleMonitor creates monitor alarm`() {
        ServiceHeartbeatScheduler.scheduleMonitor(context)

        val scheduledAlarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(scheduledAlarm)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduledAlarm.type)
    }

    @Test
    fun `cancelMonitor removes monitor alarm`() {
        ServiceHeartbeatScheduler.scheduleMonitor(context)

        assertNotNull(shadowOf(alarmManager).nextScheduledAlarm)

        ServiceHeartbeatScheduler.cancelMonitor(context)

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }

    @Test
    fun `listen heartbeat intent preserves only non-secret identity extras`() {
        val restartIntent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "nursery.local")
            putExtra("port", 10000)
            putExtra("pairingCode", "ABCDEF12")
            putExtra("expectedChildId", "abcdefghijklmnop")
            putExtra("expectedPairingId", "1234567890abcdef")
        }

        val heartbeatIntent = ServiceHeartbeatScheduler.listenHeartbeatIntent(
            context,
            restartIntent,
            sessionToken = 42L
        )

        assertEquals(ServiceHeartbeatScheduler.ACTION_LISTEN_HEARTBEAT, heartbeatIntent.action)
        assertEquals(false, heartbeatIntent.hasExtra("name"))
        assertEquals(false, heartbeatIntent.hasExtra("address"))
        assertEquals(false, heartbeatIntent.hasExtra("port"))
        assertEquals(false, heartbeatIntent.hasExtra("pairingCode"))
        assertEquals("abcdefghijklmnop", heartbeatIntent.getStringExtra("expectedChildId"))
        assertEquals("1234567890abcdef", heartbeatIntent.getStringExtra("expectedPairingId"))
        assertEquals(42L, heartbeatIntent.getLongExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, -1L))
    }
}
