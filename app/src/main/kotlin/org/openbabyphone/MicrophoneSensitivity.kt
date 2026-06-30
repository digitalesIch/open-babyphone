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

import kotlin.math.abs
import kotlin.math.sign

/**
 * Child-side microphone sensitivity levels.
 *
 * Each level applies a fixed gain factor to PCM samples before G.711
 * encoding. Soft saturation is used instead of hard clipping to reduce
 * harsh distortion on loud sounds.
 */
enum class MicrophoneSensitivity(val preferenceValue: String, val gain: Float) {
    NORMAL("normal", 1.0f),
    HIGH("high", 2.0f),
    VERY_HIGH("very_high", 4.0f);

    companion object {
        fun fromPreferenceValue(value: String?): MicrophoneSensitivity {
            return entries.firstOrNull { it.preferenceValue == value } ?: NORMAL
        }

        /**
         * Applies [sensitivity] gain to the first [length] samples of
         * [samples] in place. Uses soft saturation (tanh-like curve) to
         * avoid harsh clipping when the amplified signal exceeds the
         * 16-bit range.
         *
         * At [NORMAL] sensitivity (gain 1.0) this is a no-op.
         */
        fun applyGain(samples: ShortArray, length: Int, gain: Float) {
            if (gain <= 1.0f) return
            val maxSample = 32767.0f
            for (i in 0 until length) {
                val scaled = samples[i].toFloat() * gain
                if (abs(scaled) <= maxSample) {
                    samples[i] = scaled.toInt().toShort()
                } else {
                    val sign = sign(scaled)
                    val overshoot = (abs(scaled) - maxSample) / maxSample
                    val softLimit = maxSample * (1.0f - 0.15f * overshoot.coerceAtMost(1.0f))
                    samples[i] = (sign * softLimit).toInt().toShort()
                }
            }
        }
    }
}