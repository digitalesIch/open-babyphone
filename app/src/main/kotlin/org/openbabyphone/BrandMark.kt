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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openbabyphone.ui.theme.LiveAudioCyan
import org.openbabyphone.ui.theme.NetworkBlue

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val cyan = LiveAudioCyan
    val blue = NetworkBlue
    val iconColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.21f))
            .background(Brush.linearGradient(listOf(cyan, blue))),
        contentAlignment = Alignment.Center
    ) {
        PairedPhonesGlyph(
            color = iconColor,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
fun PairedPhonesGlyph(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.05f
        val phoneW = w * 0.22f
        val phoneH = h * 0.4f
        val phoneR = w * 0.05f
        val leftX = w * 0.14f
        val rightX = w * 0.64f
        val phoneY = h * 0.25f

        val leftPhone = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = leftX,
                    top = phoneY,
                    right = leftX + phoneW,
                    bottom = phoneY + phoneH,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(phoneR, phoneR)
                )
            )
        }
        val rightPhone = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = rightX,
                    top = phoneY,
                    right = rightX + phoneW,
                    bottom = phoneY + phoneH,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(phoneR, phoneR)
                )
            )
        }
        drawPath(leftPhone, color = color, style = Stroke(width = stroke))
        drawPath(rightPhone, color = color, style = Stroke(width = stroke))

        val waveStart = leftX + phoneW
        val waveEnd = rightX
        val waveCenterY = phoneY + phoneH / 2
        val wavePath = Path().apply {
            moveTo(waveStart, waveCenterY)
            quadraticTo(
                (waveStart + waveEnd) / 2, waveCenterY - h * 0.1f,
                waveEnd, waveCenterY
            )
        }
        drawPath(wavePath, color = color, style = Stroke(width = stroke))
    }
}