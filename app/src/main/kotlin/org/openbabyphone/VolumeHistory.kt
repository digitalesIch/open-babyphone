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

    val volumeNorm: Double
        get() = stats.volumeNorm

    operator fun get(i: Int): Double {
        return stats[i]
    }

    fun size(): Int {
        return stats.size()
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
        uiHandler.post { stats.addLast(volume) }
    }
}
