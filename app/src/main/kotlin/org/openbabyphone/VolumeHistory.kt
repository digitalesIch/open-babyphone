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
import androidx.collection.CircularArray

class VolumeHistory internal constructor(private val maxHistory: Int) {
    private var maxVolume = 0.25
    var volumeNorm = 1.0 / this.maxVolume
        private set
    private val historyData: CircularArray<Double> = CircularArray(maxHistory)
    private val uiHandler: Handler = Handler(Looper.getMainLooper())

    operator fun get(i: Int): Double {
        return historyData[i]
    }

    fun size(): Int {
        return historyData.size()
    }

    private fun addLast(volume: Double) {
        // schedule editing of member vars on the ui event loop to avoid concurrency problems
        uiHandler.post {
            if (volume > this.maxVolume) {
                this.maxVolume = volume
                this.volumeNorm = 1.0 / volume
            }
            historyData.addLast(volume)
            historyData.removeFromStart(historyData.size() - maxHistory)
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
