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

class VolumeStatistics internal constructor(private val maxHistory: Int) {
    private var maxVolume = 0.25
    var volumeNorm = 1.0 / this.maxVolume
        private set
    private val historyData = DoubleArray(maxHistory)
    private var startIndex = 0
    private var count = 0

    companion object {
        private const val DECAY_FACTOR = 0.9999
        private const val MAX_VOLUME_FLOOR = 0.25
    }

    operator fun get(i: Int): Double {
        if (i < 0 || i >= count) {
            throw IndexOutOfBoundsException("Index $i out of bounds for size $count")
        }
        return historyData[(startIndex + i) % maxHistory]
    }

    fun size(): Int {
        return count
    }

    fun addLast(volume: Double) {
        maxVolume *= DECAY_FACTOR
        if (maxVolume < MAX_VOLUME_FLOOR) {
            maxVolume = MAX_VOLUME_FLOOR
        }
        if (volume > this.maxVolume) {
            this.maxVolume = volume
        }
        this.volumeNorm = 1.0 / maxVolume
        if (count < maxHistory) {
            historyData[(startIndex + count) % maxHistory] = volume
            count++
        } else {
            historyData[startIndex] = volume
            startIndex = (startIndex + 1) % maxHistory
        }
    }

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
        addLast(volume)
    }
}
