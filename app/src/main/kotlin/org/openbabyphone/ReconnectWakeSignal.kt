package org.openbabyphone

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ReconnectWakeSignal {
    private val lock = ReentrantLock()
    private val signaled = lock.newCondition()
    private var generation = 0

    fun signal() {
        lock.withLock {
            generation++
            signaled.signalAll()
        }
    }

    @Throws(InterruptedException::class)
    fun waitFor(delayMs: Long, keepWaiting: () -> Boolean = { true }): Boolean {
        if (delayMs <= 0) return false

        lock.withLock {
            val observedGeneration = generation
            val deadline = SystemClock.elapsedRealtime() + delayMs
            var remaining = delayMs

            while (keepWaiting() && observedGeneration == generation && remaining > 0) {
                signaled.await(remaining, TimeUnit.MILLISECONDS)
                remaining = deadline - SystemClock.elapsedRealtime()
            }

            return observedGeneration != generation
        }
    }
}
