package org.openbabyphone

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WifiDirectListenCleanupTest {
    private lateinit var application: OpenBabyphoneApplication
    private lateinit var coordinator: WifiDirectCleanupCoordinator

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication() as OpenBabyphoneApplication
        coordinator = application.wifiDirectCleanupCoordinator
        coordinator.cleanup()
    }

    @Test
    fun `terminal listen start removes handed off group`() {
        val session = FakeWifiDirectSession()
        coordinator.handoff(session)
        val intent = Intent(application, ListenService::class.java)
            .putExtra("requestId", "missing")
        val controller = Robolectric.buildService(ListenService::class.java, intent).create()

        controller.get().onStartCommand(intent, 0, 1)

        assertEquals(1, session.stopCount)
        controller.destroy()
    }

    @Test
    fun `final listen destruction removes owned group idempotently`() {
        val session = FakeWifiDirectSession()
        coordinator.handoff(session)
        val token = coordinator.claimForListenSession()
        val controller = Robolectric.buildService(ListenService::class.java).create()
        controller.get().javaClass.getDeclaredField("wifiDirectOwnershipToken").apply {
            isAccessible = true
            set(controller.get(), token)
        }

        controller.destroy()
        coordinator.cleanup()

        assertEquals(1, session.stopCount)
    }

    private class FakeWifiDirectSession : WifiDirectSession {
        override val state: StateFlow<WifiDirectState> = MutableStateFlow(WifiDirectState.Idle)
        var stopCount = 0

        override fun isSupported(): Boolean = true
        override fun startChildAdvertising(port: Int, name: String) = Unit
        override fun startParentDiscovery() = Unit
        override fun connectToPeer(peer: WifiDirectPeer) = Unit
        override fun handoffToListen() = Unit
        override fun stop() {
            stopCount++
        }
    }
}
