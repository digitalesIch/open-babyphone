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
    fun `listen heartbeat intent preserves restart extras`() {
        val restartIntent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "nursery.local")
            putExtra("port", 10000)
            putExtra("pairingCode", "ABCDEF12")
        }

        val heartbeatIntent = ServiceHeartbeatScheduler.listenHeartbeatIntent(context, restartIntent)

        assertEquals(ServiceHeartbeatScheduler.ACTION_LISTEN_HEARTBEAT, heartbeatIntent.action)
        assertEquals("Nursery", heartbeatIntent.getStringExtra("name"))
        assertEquals("nursery.local", heartbeatIntent.getStringExtra("address"))
        assertEquals(10000, heartbeatIntent.getIntExtra("port", 0))
        assertEquals("ABCDEF12", heartbeatIntent.getStringExtra("pairingCode"))
    }
}
