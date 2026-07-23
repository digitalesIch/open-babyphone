package org.openbabyphone

import android.os.SystemClock

class ReconnectWakeSignal {
    private val lock = Object()
    private var generation = 0

    fun signal() {
        synchronized(lock) {
            generation++
            lock.notifyAll()
        }
    }

    @Throws(InterruptedException::class)
    fun waitFor(delayMs: Long, keepWaiting: () -> Boolean = { true }): Boolean {
        if (delayMs <= 0) return false

        synchronized(lock) {
            val observedGeneration = generation
            val deadline = SystemClock.elapsedRealtime() + delayMs
            var remaining = delayMs

            while (keepWaiting() && observedGeneration == generation && remaining > 0) {
                lock.wait(remaining)
                remaining = deadline - SystemClock.elapsedRealtime()
            }

            return observedGeneration != generation
        }
    }
}
