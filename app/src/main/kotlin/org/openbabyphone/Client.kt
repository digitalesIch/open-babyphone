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

import android.os.SystemClock
import android.util.Log
import org.openbabyphone.audio.FrameCodec
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class Client(
    val socket: Socket,
    val id: Int,
    val pairingCode: String,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    companion object {
        private const val TAG = "Client"
        const val QUEUE_CAPACITY = 100
        const val MAX_DROPPED_FRAMES = 50
        const val SLOW_CLIENT_GRACE_MS = 10_000L

        // One slot may be in flight while all QUEUE_CAPACITY slots are waiting:
        // 101 * 32,027 bytes = 3,234,727 frame-buffer bytes per client.
        const val FRAME_SLOT_COUNT = QUEUE_CAPACITY + 1
        const val MAX_FRAME_BUFFER_BYTES = FRAME_SLOT_COUNT * FrameCodec.MAX_FRAME_SIZE
    }

    private class FrameSlot {
        val data = ByteArray(FrameCodec.MAX_FRAME_SIZE)
        var length = 0

        fun clear() {
            data.fill(0, 0, length)
            length = 0
        }
    }

    private val slots = Array(FRAME_SLOT_COUNT) { FrameSlot() }
    private val freeSlots = ArrayBlockingQueue<FrameSlot>(FRAME_SLOT_COUNT)
    private val readySlots = ArrayBlockingQueue<FrameSlot>(QUEUE_CAPACITY)
    private val slotLock = Any()
    private val totalDroppedFrames = AtomicInteger(0)
    private val consecutiveDroppedFrames = AtomicInteger(0)
    private val firstConsecutiveDropAtMs = AtomicLong(0L)
    private var sendThread: Thread? = null
    @Volatile private var isRunning = false

    init {
        for (slot in slots) {
            check(freeSlots.offer(slot))
        }
    }

    fun startSending() {
        isRunning = true
        sendThread = Thread {
            try {
                val out = socket.getOutputStream()
                socket.soTimeout = 0
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val slot = readySlots.take()
                    try {
                        out.write(slot.data, 0, slot.length)
                        out.flush()
                        consecutiveDroppedFrames.set(0)
                        firstConsecutiveDropAtMs.set(0L)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to send frame to client $id", e)
                        totalDroppedFrames.addAndGet(MAX_DROPPED_FRAMES)
                        consecutiveDroppedFrames.set(MAX_DROPPED_FRAMES)
                        firstConsecutiveDropAtMs.set(clock())
                        break
                    } finally {
                        recycle(slot)
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Send thread interrupted for client $id")
            } finally {
                isRunning = false
                synchronized(slotLock) {
                    drainReadySlots()
                }
            }
        }
        sendThread?.start()
    }

    fun queueFrame(frame: ByteArray): Boolean {
        return queueFrame(frame, 0, frame.size)
    }

    fun queueFrame(frame: ByteArray, offset: Int, length: Int): Boolean {
        require(offset >= 0 && length >= 0 && offset <= frame.size - length) {
            "Frame range is outside the array"
        }
        require(length in 1..FrameCodec.MAX_FRAME_SIZE) { "Invalid frame length" }

        synchronized(slotLock) {
            if (!isRunning) return false
            val slot = freeSlots.poll()
            if (slot == null) {
                recordDroppedFrame()
                return false
            }
            frame.copyInto(slot.data, 0, offset, offset + length)
            slot.length = length
            if (readySlots.offer(slot)) return true

            recycle(slot)
            recordDroppedFrame()
            return false
        }
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

    internal fun getAllocatedFrameSlotCount(): Int = slots.size

    internal fun getAvailableFrameSlotCount(): Int = freeSlots.size

    internal fun getQueuedFrameCount(): Int = readySlots.size

    fun stop() {
        synchronized(slotLock) {
            isRunning = false
            drainReadySlots()
        }
        val thread = sendThread
        thread?.interrupt()
        try {
            socket.close()
        } catch (e: IOException) {
            Log.d(TAG, "Failed to close socket for client $id", e)
        }
    }

    private fun recordDroppedFrame() {
        totalDroppedFrames.incrementAndGet()
        val consecutiveDrops = consecutiveDroppedFrames.incrementAndGet()
        if (consecutiveDrops == 1) {
            firstConsecutiveDropAtMs.set(clock())
        }
        Log.w(TAG, "Queue full for client $id, consecutive dropped frame $consecutiveDrops/$MAX_DROPPED_FRAMES")
    }

    private fun recycle(slot: FrameSlot) {
        slot.clear()
        check(freeSlots.offer(slot)) { "Frame slot returned more than once" }
    }

    private fun drainReadySlots() {
        while (true) {
            recycle(readySlots.poll() ?: return)
        }
    }
}
