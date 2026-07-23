package org.openbabyphone

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `listen heartbeat starts listen service with only identity extras`() {
        val restartIntent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "nursery.local")
            putExtra("port", 10000)
            putExtra("pairingCode", "ABCDEF12")
            putExtra("expectedChildId", "abcdefghijklmnop")
            putExtra("expectedPairingId", "1234567890abcdef")
        }

        ServiceHeartbeatReceiver().onReceive(
            context,
            ServiceHeartbeatScheduler.listenHeartbeatIntent(context, restartIntent)
        )

        val startedService = shadowOf(context).nextStartedService
        assertEquals(ListenService::class.java.name, startedService.component?.className)
        assertTrue(startedService.getBooleanExtra(ServiceHeartbeatScheduler.EXTRA_HEARTBEAT, false))
        assertTrue(!startedService.hasExtra("name"))
        assertTrue(!startedService.hasExtra("address"))
        assertTrue(!startedService.hasExtra("port"))
        assertTrue(!startedService.hasExtra("pairingCode"))
        assertEquals("abcdefghijklmnop", startedService.getStringExtra("expectedChildId"))
        assertEquals("1234567890abcdef", startedService.getStringExtra("expectedPairingId"))
    }

    @Test
    fun `foreground service rejection is contained for recovery fallback`() {
        assertFalse(tryStartHeartbeatService { throw IllegalStateException("background start rejected") })
        assertFalse(tryStartHeartbeatService { throw SecurityException("while-in-use denied") })
    }
}
