/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class BoundedHandshakeExecutor(
    workerCount: Int = 2,
    queueCapacity: Int = 2,
    threadFactory: ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "MonitorHandshake").apply { isDaemon = true }
    }
) {
    private val executor: ThreadPoolExecutor

    init {
        require(workerCount > 0)
        require(queueCapacity > 0)
        executor = ThreadPoolExecutor(
            workerCount,
            workerCount,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
            threadFactory,
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    fun submit(onRejected: () -> Unit, task: () -> Unit): Boolean = try {
        executor.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        onRejected()
        false
    }

    fun shutdownNow() {
        executor.shutdownNow()
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
