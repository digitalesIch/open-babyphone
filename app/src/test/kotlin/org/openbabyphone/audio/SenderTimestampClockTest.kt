package org.openbabyphone.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SenderTimestampClockTest {
    @Test
    fun `signed maximum boundary remains monotonic`() {
        val clock = SenderTimestampClock()

        assertEquals(0L, clock.frameAgeMillis(1_000L, Int.MAX_VALUE - 1))
        assertEquals(0L, clock.frameAgeMillis(1_002L, Int.MIN_VALUE))
        assertEquals(0L, clock.frameAgeMillis(1_003L, Int.MIN_VALUE + 1))
    }

    @Test
    fun `unsigned maximum wraps to zero without making frames stale`() {
        val clock = SenderTimestampClock()

        assertEquals(0L, clock.frameAgeMillis(5_000L, -2))
        assertEquals(0L, clock.frameAgeMillis(5_001L, -1))
        assertEquals(0L, clock.frameAgeMillis(5_002L, 0))
        assertEquals(0L, clock.frameAgeMillis(5_003L, 1))
    }

    @Test
    fun `receiver delay is measured across wrap`() {
        val clock = SenderTimestampClock()

        clock.frameAgeMillis(10_000L, -1)

        assertEquals(40L, clock.frameAgeMillis(10_041L, 0))
    }
}
