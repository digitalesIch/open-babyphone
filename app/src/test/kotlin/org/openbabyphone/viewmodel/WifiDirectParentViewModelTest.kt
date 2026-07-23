package org.openbabyphone.viewmodel

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.OpenBabyphoneApplication
import org.openbabyphone.WifiDirectCleanupCoordinator
import org.openbabyphone.WifiDirectEndpoint
import org.openbabyphone.WifiDirectPeer
import org.openbabyphone.WifiDirectSession
import org.openbabyphone.WifiDirectState
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WifiDirectParentViewModelTest {
    @Test
    fun connectedHandoffIsConsumedAndCleanedOnlyOnce() {
        val session = FakeWifiDirectSession()
        val coordinator = WifiDirectCleanupCoordinator()
        val viewModel = WifiDirectParentViewModel(application(), session, coordinator)
        val endpoint = WifiDirectEndpoint("child", 10000, "Nursery")
        session.mutableState.value = WifiDirectState.Connected(endpoint)

        assertEquals(endpoint, viewModel.consumeConnectedEndpoint())
        assertNull(viewModel.consumeConnectedEndpoint())
        assertEquals(1, session.handoffCount)
        assertEquals(0, session.stopCount)
        assertEquals(true, coordinator.hasOwnership())
    }

    @Test
    fun cancelStopsConnectionAndRetryStartsFreshGeneration() {
        val session = FakeWifiDirectSession()
        val viewModel = WifiDirectParentViewModel(
            application(), session, WifiDirectCleanupCoordinator()
        )

        viewModel.startDiscovery()
        session.mutableState.value = WifiDirectState.Error("failed")
        viewModel.startDiscovery()
        viewModel.cancel()

        assertEquals(2, session.startCount)
        assertEquals(1, session.stopCount)
        assertEquals(WifiDirectState.Idle, session.state.value)
    }

    @Test
    fun disconnectBeforeHandoffDoesNotProduceEndpoint() {
        val session = FakeWifiDirectSession()
        val viewModel = WifiDirectParentViewModel(
            application(), session, WifiDirectCleanupCoordinator()
        )
        session.mutableState.value = WifiDirectState.Connecting(peer())
        session.mutableState.value = WifiDirectState.Error("disconnected")

        assertNull(viewModel.consumeConnectedEndpoint())
        assertEquals(0, session.handoffCount)
    }

    @Test
    fun terminalCancelCleansHandedOffGroupExactlyOnce() {
        val session = FakeWifiDirectSession()
        val coordinator = WifiDirectCleanupCoordinator()
        val viewModel = WifiDirectParentViewModel(application(), session, coordinator)
        session.mutableState.value = WifiDirectState.Connected(
            WifiDirectEndpoint("child", 10000, "Nursery")
        )
        viewModel.consumeConnectedEndpoint()

        viewModel.cancel()
        coordinator.cleanup()

        assertEquals(1, session.stopCount)
        assertEquals(false, coordinator.hasOwnership())
    }

    @Test
    fun staleListenSessionCannotCleanGroupClaimedByRapidRetry() {
        val session = FakeWifiDirectSession()
        val coordinator = WifiDirectCleanupCoordinator()
        coordinator.handoff(session)
        val oldClaim = coordinator.claimForListenSession()!!
        val retryClaim = coordinator.claimForListenSession()!!

        coordinator.cleanup(oldClaim)

        assertEquals(0, session.stopCount)
        assertEquals(true, coordinator.hasOwnership())
        coordinator.cleanup(retryClaim)
        assertEquals(1, session.stopCount)
    }

    private fun application(): OpenBabyphoneApplication = ApplicationProvider.getApplicationContext()

    private fun peer() = WifiDirectPeer("address", "device", 10000, "Nursery")

    private class FakeWifiDirectSession : WifiDirectSession {
        val mutableState = MutableStateFlow<WifiDirectState>(WifiDirectState.Idle)
        override val state: StateFlow<WifiDirectState> = mutableState
        var startCount = 0
        var stopCount = 0
        var handoffCount = 0

        override fun isSupported(): Boolean = true

        override fun startChildAdvertising(port: Int, name: String) = Unit

        override fun startParentDiscovery() {
            startCount++
            mutableState.value = WifiDirectState.Starting
        }

        override fun connectToPeer(peer: WifiDirectPeer) {
            mutableState.value = WifiDirectState.Connecting(peer)
        }

        override fun handoffToListen() {
            handoffCount++
        }

        override fun stop() {
            stopCount++
            mutableState.value = WifiDirectState.Idle
        }
    }
}
