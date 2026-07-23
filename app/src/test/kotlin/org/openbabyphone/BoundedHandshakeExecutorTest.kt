package org.openbabyphone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BoundedHandshakeExecutorTest {
    @Test
    fun `saturation rejects excess handshake immediately`() {
        val executor = BoundedHandshakeExecutor(workerCount = 1, queueCapacity = 1)
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        var rejected = false

        assertTrue(executor.submit({}) {
            workerStarted.countDown()
            releaseWorker.await()
        })
        assertTrue(workerStarted.await(1, TimeUnit.SECONDS))
        assertTrue(executor.submit({}) {})

        val accepted = executor.submit({ rejected = true }) {}

        assertFalse(accepted)
        assertTrue(rejected)
        releaseWorker.countDown()
        executor.shutdownNow()
    }
}
