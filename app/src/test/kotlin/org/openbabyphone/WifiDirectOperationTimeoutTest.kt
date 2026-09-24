package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WifiDirectOperationTimeoutTest {
    private class ManualScheduler : TimeoutScheduler {
        private val actions = mutableListOf<() -> Unit>()

        override fun schedule(delayMs: Long, action: () -> Unit): TimeoutScheduler.Cancellable {
            actions += action
            return TimeoutScheduler.Cancellable { actions -= action }
        }

        fun fireAll() {
            val pending = actions.toList()
            actions.clear()
            pending.forEach { it() }
        }

        fun pendingCount(): Int = actions.size
    }

    @Test
    fun `timeout fires while its own operation is still pending`() {
        val scheduler = ManualScheduler()
        val timeout = WifiDirectOperationTimeout(scheduler, 30_000L)
        var fired = 0

        timeout.arm(isCurrent = { it == 1L }, token = 1L, isPending = { true }) { fired++ }
        scheduler.fireAll()

        assertEquals(1, fired)
    }

    @Test
    fun `cancel prevents a scheduled timeout`() {
        val scheduler = ManualScheduler()
        val timeout = WifiDirectOperationTimeout(scheduler, 30_000L)
        var fired = 0

        timeout.arm(isCurrent = { true }, token = 1L, isPending = { true }) { fired++ }
        timeout.cancel()
        scheduler.fireAll()

        assertEquals(0, fired)
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun `stale generation never fires after a new operation begins`() {
        val scheduler = ManualScheduler()
        val timeout = WifiDirectOperationTimeout(scheduler, 30_000L)
        var generation = 1L
        var fired = 0

        timeout.arm(isCurrent = { it == generation }, token = 1L, isPending = { true }) { fired++ }
        generation = 2L
        timeout.arm(isCurrent = { it == generation }, token = 2L, isPending = { true }) { fired++ }
        scheduler.fireAll()

        assertEquals(1, fired)
    }

    @Test
    fun `completed operation does not time out`() {
        val scheduler = ManualScheduler()
        val timeout = WifiDirectOperationTimeout(scheduler, 30_000L)
        var fired = 0
        var pending = true

        timeout.arm(isCurrent = { true }, token = 1L, isPending = { pending }) { fired++ }
        pending = false
        scheduler.fireAll()

        assertEquals(0, fired)
    }
}
