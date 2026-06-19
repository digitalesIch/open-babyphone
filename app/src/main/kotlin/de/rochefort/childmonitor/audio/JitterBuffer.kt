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
package de.rochefort.childmonitor.audio

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Jitter buffer for audio frames.
 * 
 * Buffers frames to smooth out network jitter before playback.
 * Target buffer size is 100ms (5-6 frames at 50 FPS).
 */
class JitterBuffer {
    companion object {
        private const val TARGET_BUFFER_SIZE_MS = 100
        private const val FRAME_DURATION_MS = 20 // Approximate at 50 FPS
        private const val TARGET_FRAME_COUNT = TARGET_BUFFER_SIZE_MS / FRAME_DURATION_MS
    }

    private val frameQueue = ArrayBlockingQueue<DecodedFrame>(TARGET_FRAME_COUNT + 2)
    private var totalFrames = 0
    private var droppedFrames = 0

    data class DecodedFrame(
        val seqNum: Int,
        val timestampMs: Int,
        val ulawData: ByteArray,
        val receiveTime: Long = System.currentTimeMillis()
    )

    /**
     * Add a frame to the buffer.
     * Returns true if frame was accepted, false if buffer is full.
     */
    fun addFrame(frame: DecodedFrame): Boolean {
        totalFrames++
        val success = frameQueue.offer(frame)
        if (!success) {
            droppedFrames++
        }
        return success
    }

    /**
     * Get the next frame for playback.
     * Blocks until a frame is available or timeout expires.
     * Returns null if buffer is empty (underrun).
     */
    fun getFrame(timeoutMs: Long = 100): DecodedFrame? {
        return frameQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Check if buffer has enough frames to start playback.
     */
    fun isReady(): Boolean = frameQueue.size >= TARGET_FRAME_COUNT

    /**
     * Get current buffer fill level in milliseconds.
     */
    fun getBufferLevelMs(): Int = frameQueue.size * FRAME_DURATION_MS

    /**
     * Get statistics.
     */
    fun getStats(): String = "Total: $totalFrames, Dropped: $droppedFrames, Level: ${getBufferLevelMs()}ms"

    /**
     * Clear the buffer.
     */
    fun clear() {
        frameQueue.clear()
        totalFrames = 0
        droppedFrames = 0
    }
}
