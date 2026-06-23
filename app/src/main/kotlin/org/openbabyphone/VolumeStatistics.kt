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
    private var count = 0

    operator fun get(i: Int): Double {
        if (i < 0 || i >= count) {
            throw IndexOutOfBoundsException("Index $i out of bounds for size $count")
        }
        return historyData[i]
    }

    fun size(): Int {
        return count
    }

    fun addLast(volume: Double) {
        if (volume > this.maxVolume) {
            this.maxVolume = volume
            this.volumeNorm = 1.0 / volume
        }
        if (count < maxHistory) {
            historyData[count] = volume
            count++
        } else {
            for (i in 0 until maxHistory - 1) {
                historyData[i] = historyData[i + 1]
            }
            historyData[maxHistory - 1] = volume
        }
    }

    fun onAudioData(data: ShortArray) {
        if (data.isEmpty()) {
            return
        }
        val scale = 1.0 / 128.0
        var sum = 0.0
        for (datum in data) {
            val rel = datum * scale
            sum += rel * rel
        }
        val volume = sum / data.size
        addLast(volume)
    }
}
