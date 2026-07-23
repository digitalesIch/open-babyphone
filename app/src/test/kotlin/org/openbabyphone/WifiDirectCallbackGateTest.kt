package org.openbabyphone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectCallbackGateTest {
    @Test
    fun callbacksFromStoppedAndRetriedOperationsAreIgnored() {
        val gate = WifiDirectCallbackGate()
        val first = gate.begin()
        gate.cancel()
        val retry = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(retry))
    }

    @Test
    fun connectedCanBeClaimedOnlyOncePerGeneration() {
        val gate = WifiDirectCallbackGate()
        val token = gate.begin()

        assertTrue(gate.claimConnected(token))
        assertFalse(gate.claimConnected(token))

        val retry = gate.begin()
        assertFalse(gate.claimConnected(token))
        assertTrue(gate.claimConnected(retry))
    }

    @Test
    fun peersAreDeduplicatedByDeviceAddressAndUpdatedInPlace() {
        val original = WifiDirectPeer("address", "raw", 10000, "Old name")
        val updated = WifiDirectPeer("address", "raw-new", 10001, "Nursery")

        val peers = upsertWifiDirectPeer(listOf(original), updated)

        assertEquals(listOf(updated), peers)
    }
}
