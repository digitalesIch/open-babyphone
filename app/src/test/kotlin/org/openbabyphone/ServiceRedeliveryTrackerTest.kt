package org.openbabyphone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRedeliveryTrackerTest {
    private val tracker = ServiceRedeliveryTracker()
    private val claim = WorkerClaim(generation = 1L, startId = 10)

    @Test
    fun `redelivered worker failure requires one recovery action`() {
        tracker.record(claim, redelivered = true)

        assertTrue(tracker.consumeFailure(claim))
        assertFalse(tracker.consumeFailure(claim))
    }

    @Test
    fun `verified recovery suppresses later recovery action`() {
        tracker.record(claim, redelivered = true)

        tracker.markRecovered(claim)

        assertFalse(tracker.consumeFailure(claim))
    }

    @Test
    fun `new explicit start replaces pending redelivery`() {
        tracker.record(claim, redelivered = true)
        val explicitClaim = WorkerClaim(generation = 2L, startId = 11)

        tracker.record(explicitClaim, redelivered = false)

        assertFalse(tracker.consumeFailure(claim))
        assertFalse(tracker.consumeFailure(explicitClaim))
    }
}
