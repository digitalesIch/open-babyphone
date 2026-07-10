package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun `first attempt uses base delay with jitter`() {
        val backoff = ReconnectBackoff(random = { 0.5 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        val delay = backoff.delayForAttempt(1)
        assertEquals(2000L, delay)
    }

    @Test
    fun `delays grow exponentially`() {
        val backoff = ReconnectBackoff(random = { 0.5 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        assertEquals(2000L, backoff.delayForAttempt(1))
        assertEquals(4000L, backoff.delayForAttempt(2))
        assertEquals(8000L, backoff.delayForAttempt(3))
        assertEquals(16000L, backoff.delayForAttempt(4))
        assertEquals(30000L, backoff.delayForAttempt(5))
    }

    @Test
    fun `delay is capped at maxDelayMs`() {
        val backoff = ReconnectBackoff(random = { 0.5 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 0)
        assertEquals(30000L, backoff.delayForAttempt(5))
        assertEquals(30000L, backoff.delayForAttempt(10))
    }

    @Test
    fun `positive jitter increases delay`() {
        val backoff = ReconnectBackoff(random = { 1.0 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        assertEquals(2500L, backoff.delayForAttempt(1))
    }

    @Test
    fun `negative jitter decreases delay`() {
        val backoff = ReconnectBackoff(random = { 0.0 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        assertEquals(1500L, backoff.delayForAttempt(1))
    }

    @Test
    fun `jitter stays within bounds`() {
        val backoff = ReconnectBackoff(random = { Math.random() }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        for (attempt in 1..10) {
            val delay = backoff.delayForAttempt(attempt)
            val exponential = minOf(2000L * (1L shl (attempt - 1)), 30_000L)
            assertTrue("Delay $delay should be >= ${exponential - 500}", delay >= exponential - 500)
            assertTrue("Delay $delay should be <= ${exponential + 500}", delay <= exponential + 500)
        }
    }

    @Test
    fun `different random values produce different delays for same attempt`() {
        val backoff1 = ReconnectBackoff(random = { 0.0 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        val backoff2 = ReconnectBackoff(random = { 1.0 }, baseDelayMs = 2000, maxDelayMs = 30_000, jitterMs = 500)
        assertTrue(backoff1.delayForAttempt(1) != backoff2.delayForAttempt(1))
    }
}