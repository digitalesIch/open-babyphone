package org.openbabyphone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class ReconnectWakeSignalTest {

    @Test
    fun `wait returns true when signal arrives before timeout`() {
        val wakeSignal = ReconnectWakeSignal()
        val wokeEarly = AtomicReference<Boolean>()

        val thread = Thread {
            wokeEarly.set(wakeSignal.waitFor(5_000))
        }

        thread.start()
        Thread.sleep(50)
        wakeSignal.signal()
        thread.join(1_000)

        assertFalse("Waiting thread should finish after signal", thread.isAlive)
        assertTrue("Signal should wake reconnect delay", wokeEarly.get())
    }

    @Test
    fun `wait returns false when keepWaiting turns false`() {
        val wakeSignal = ReconnectWakeSignal()
        val wokeEarly = AtomicReference<Boolean>()

        val thread = Thread {
            wokeEarly.set(wakeSignal.waitFor(5_000) { false })
        }

        thread.start()
        thread.join(1_000)

        assertFalse("Waiting thread should finish after shutdown", thread.isAlive)
        assertFalse("Shutdown without network signal should not report a network wake", wokeEarly.get())
    }
}
