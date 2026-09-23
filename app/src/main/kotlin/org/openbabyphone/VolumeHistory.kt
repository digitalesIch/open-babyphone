/*
 * This file is part of Child Monitor.
 *
 * Child Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Child Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Child Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import android.os.Handler
import android.os.Looper

class VolumeHistory internal constructor(maxHistory: Int) {
    private val stats = VolumeStatistics(maxHistory)
    private val uiHandler: Handler = Handler(Looper.getMainLooper())

    // Single-producer (playback thread) / single-consumer (main thread) handoff
    // of volume samples without per-frame allocation.
    private val pendingVolumes = DoubleArray(PENDING_CAPACITY)
    private var pendingCount = 0
    private val pendingLock = Any()

    private val drainPendingVolumes = Runnable {
        synchronized(pendingLock) {
            val count = pendingCount
            for (index in 0 until count) {
                stats.addLast(pendingVolumes[index])
            }
            pendingCount = 0
        }
    }

    val volumeNorm: Double
        get() = synchronized(stats) { stats.volumeNorm }

    operator fun get(i: Int): Double = synchronized(stats) { stats[i] }

    fun size(): Int = synchronized(stats) { stats.size() }

    fun onAudioData(data: ShortArray) {
        onAudioData(data, 0, data.size)
    }

    fun onAudioData(data: ShortArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= data.size - length) {
            "Audio data range is outside the array"
        }
        if (length == 0) {
            return
        }
        val scale = 1.0 / 128.0
        var sum = 0.0
        for (index in offset until offset + length) {
            val rel = data[index] * scale
            sum += rel * rel
        }
        val volume = sum / length

        val posted = synchronized(pendingLock) {
            val index = pendingCount
            if (index < PENDING_CAPACITY) {
                pendingVolumes[index] = volume
                pendingCount = index + 1
                true
            } else {
                false
            }
        }
        if (posted) {
            uiHandler.post(drainPendingVolumes)
        }
    }

    private companion object {
        private const val PENDING_CAPACITY = 64
    }
}
