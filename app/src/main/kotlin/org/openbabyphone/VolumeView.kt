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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class VolumeView : View {
    private val paint: Paint
    var volumeHistory: VolumeHistory? = null

    constructor(context: Context?) : super(context) {
        paint = initPaint()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        paint = initPaint()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        paint = initPaint()
    }

    private fun initPaint(): Paint {
        val paint = Paint()
        paint.color = Color.rgb(255, 127, 0)
        return paint
    }

    override fun onDraw(canvas: Canvas) {
        val volumeHistory = this.volumeHistory ?: return
        val height = height
        val width = width
        val size = volumeHistory.size() // Size is at most width
        val volumeNorm = volumeHistory.volumeNorm
        val minBrightness = 0.3
        val relativeBrightness: Double = if (size > 0) {
            (volumeNorm * volumeHistory[size - 1]).coerceAtLeast(minBrightness)
        } else {
            minBrightness
        }
        val blue: Int
        val rest: Int
        if (relativeBrightness > 0.5) {
            blue = 255
            rest = (2 * 255 * (relativeBrightness - 0.5)).toInt()
        } else {
            blue = (255 * (relativeBrightness - 0.2) / 0.3).toInt()
            rest = 0
        }
        val rgb = Color.rgb(rest, rest, blue)
        canvas.drawColor(rgb)
        if (size == 0) {
            return
        }
        val margins = height * 0.1
        val graphHeight = height - 2.0 * margins
        val leftMost = (volumeHistory.size() - width).coerceAtLeast(0)
        val graphScale = graphHeight * volumeNorm
        var xPrev = 0
        var yPrev = (margins + graphHeight - volumeHistory[leftMost] * graphScale).toInt()
        val length = min(size, width)
        for (xNext in 1 until length - 1) {
            val yNext = (margins + graphHeight - volumeHistory[leftMost + xNext] * graphScale).toInt()
            canvas.drawLine(xPrev.toFloat(), yPrev.toFloat(), xNext.toFloat(), yNext.toFloat(), paint)
            xPrev = xNext
            yPrev = yNext
        }
    }
}
