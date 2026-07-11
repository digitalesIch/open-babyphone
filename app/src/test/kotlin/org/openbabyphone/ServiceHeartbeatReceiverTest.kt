package org.openbabyphone

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ServiceHeartbeatReceiverTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
    }

    @Test
    fun `monitor heartbeat starts monitor service`() {
        ServiceHeartbeatReceiver().onReceive(
            context,
            ServiceHeartbeatScheduler.monitorHeartbeatIntent(context)
        )

        val startedService = shadowOf(context).nextStartedService
        assertEquals(MonitorService::class.java.name, startedService.component?.className)
        assertTrue(startedService.getBooleanExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, false))
    }

    @Test
    fun `listen heartbeat starts listen service with restart extras`() {
        val restartIntent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "nursery.local")
            putExtra("port", 10000)
            putExtra("pairingCode", "ABCDEF12")
        }

        ServiceHeartbeatReceiver().onReceive(
            context,
            ServiceHeartbeatScheduler.listenHeartbeatIntent(context, restartIntent)
        )

        val startedService = shadowOf(context).nextStartedService
        assertEquals(ListenService::class.java.name, startedService.component?.className)
        assertTrue(startedService.getBooleanExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, false))
        assertEquals("Nursery", startedService.getStringExtra("name"))
        assertEquals("nursery.local", startedService.getStringExtra("address"))
        assertEquals(10000, startedService.getIntExtra("port", 0))
        assertEquals("ABCDEF12", startedService.getStringExtra("pairingCode"))
    }
}
