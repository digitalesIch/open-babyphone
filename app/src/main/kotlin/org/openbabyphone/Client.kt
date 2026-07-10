/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import android.util.Log
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class Client(
    val socket: Socket,
    val id: Int,
    val pairingCode: String,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val TAG = "Client"
        const val QUEUE_CAPACITY = 100
        const val MAX_DROPPED_FRAMES = 50
        const val SLOW_CLIENT_GRACE_MS = 10_000L
    }

    private val frameQueue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private val totalDroppedFrames = AtomicInteger(0)
    private val consecutiveDroppedFrames = AtomicInteger(0)
    private val firstConsecutiveDropAtMs = AtomicLong(0L)
    private var sendThread: Thread? = null
    @Volatile private var isRunning = false

    fun startSending() {
        isRunning = true
        sendThread = Thread {
            try {
                val out = socket.getOutputStream()
                socket.soTimeout = 0
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val frame = frameQueue.take()
                    try {
                        out.write(frame)
                        out.flush()
                        consecutiveDroppedFrames.set(0)
                        firstConsecutiveDropAtMs.set(0L)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to send frame to client $id", e)
                        totalDroppedFrames.addAndGet(MAX_DROPPED_FRAMES)
                        consecutiveDroppedFrames.set(MAX_DROPPED_FRAMES)
                        firstConsecutiveDropAtMs.set(clock())
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Send thread interrupted for client $id")
            } finally {
                isRunning = false
            }
        }
        sendThread?.start()
    }

    fun queueFrame(frame: ByteArray): Boolean {
        if (!isRunning) {
            return false
        }
        val success = frameQueue.offer(frame)
        if (!success) {
            totalDroppedFrames.incrementAndGet()
            val consecutiveDrops = consecutiveDroppedFrames.incrementAndGet()
            if (consecutiveDrops == 1) {
                firstConsecutiveDropAtMs.set(clock())
            }
            Log.w(TAG, "Queue full for client $id, consecutive dropped frame $consecutiveDrops/$MAX_DROPPED_FRAMES")
        }
        return success
    }

    fun shouldDisconnect(): Boolean {
        val drops = consecutiveDroppedFrames.get()
        if (drops < MAX_DROPPED_FRAMES) {
            return false
        }
        val firstDrop = firstConsecutiveDropAtMs.get()
        if (firstDrop == 0L) {
            return true
        }
        val elapsed = clock() - firstDrop
        return elapsed >= SLOW_CLIENT_GRACE_MS
    }

    fun getDroppedFrameCount(): Int = totalDroppedFrames.get()

    fun getConsecutiveDroppedFrameCount(): Int = consecutiveDroppedFrames.get()

    fun stop() {
        isRunning = false
        sendThread?.interrupt()
        try {
            socket.close()
        } catch (e: IOException) {
            Log.d(TAG, "Failed to close socket for client $id", e)
        }
    }
}