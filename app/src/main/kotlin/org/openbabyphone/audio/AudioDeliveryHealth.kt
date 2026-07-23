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
package org.openbabyphone.audio

internal enum class AudioDeliveryStatus {
    Disarmed,
    AwaitingAudio,
    Healthy,
    Disrupted,
    Lost
}

internal class AudioDeliveryHealth(
    private val elapsedRealtime: () -> Long,
    private val disruptedAfterMs: Long = 5_000L,
    private val lostAfterMs: Long = 10_000L
) {
    private var lastDeliveryAt = 0L
    private var hasDeliveredAudio = false
    private var armed = false

    @Synchronized
    fun arm() {
        lastDeliveryAt = elapsedRealtime()
        hasDeliveredAudio = false
        armed = true
    }

    @Synchronized
    fun disarm() {
        armed = false
        hasDeliveredAudio = false
    }

    @Synchronized
    fun markDelivered(): Boolean {
        if (!armed) return false
        lastDeliveryAt = elapsedRealtime()
        hasDeliveredAudio = true
        return true
    }

    @Synchronized
    fun status(): AudioDeliveryStatus {
        if (!armed) return AudioDeliveryStatus.Disarmed
        val elapsed = (elapsedRealtime() - lastDeliveryAt).coerceAtLeast(0L)
        return when {
            elapsed >= lostAfterMs -> AudioDeliveryStatus.Lost
            elapsed >= disruptedAfterMs -> AudioDeliveryStatus.Disrupted
            hasDeliveredAudio -> AudioDeliveryStatus.Healthy
            else -> AudioDeliveryStatus.AwaitingAudio
        }
    }
}
