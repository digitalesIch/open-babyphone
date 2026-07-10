package org.openbabyphone

class ReconnectBackoff(
    private val random: () -> Double = { Math.random() },
    private val baseDelayMs: Long = 2000L,
    private val maxDelayMs: Long = 30_000L,
    private val jitterMs: Long = 500L
) {
    fun delayForAttempt(attempt: Int): Long {
        val exponential = baseDelayMs * (1L shl (attempt - 1))
        val capped = minOf(exponential, maxDelayMs)
        val jitter = (random() * 2 - 1) * jitterMs
        return (capped + jitter.toLong()).coerceAtLeast(0)
    }
}