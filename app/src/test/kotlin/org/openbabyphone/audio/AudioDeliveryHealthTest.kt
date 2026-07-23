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

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDeliveryHealthTest {
    @Test
    fun `health remains disarmed while connecting and handshaking`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })

        now = 20_000L

        assertEquals(AudioDeliveryStatus.Disarmed, health.status())
    }

    @Test
    fun `health deadlines begin at arm before first audio`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })
        health.arm()

        now = 5_999L
        assertEquals(AudioDeliveryStatus.AwaitingAudio, health.status())
        now = 6_000L
        assertEquals(AudioDeliveryStatus.Disrupted, health.status())
        now = 11_000L
        assertEquals(AudioDeliveryStatus.Lost, health.status())
    }

    @Test
    fun `heartbeats stale frames and receipt cannot turn awaiting audio healthy`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })
        health.arm()

        // Non-audio events deliberately have no health mutation API.
        now = 6_000L
        assertEquals(AudioDeliveryStatus.Disrupted, health.status())
        now = 11_000L
        assertEquals(AudioDeliveryStatus.Lost, health.status())
    }

    @Test
    fun `verified delivery restores healthy state and resets deadlines`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })
        health.arm()
        health.markDelivered()
        now = 7_000L
        assertEquals(AudioDeliveryStatus.Disrupted, health.status())

        health.markDelivered()

        assertEquals(AudioDeliveryStatus.Healthy, health.status())
        now = 11_999L
        assertEquals(AudioDeliveryStatus.Healthy, health.status())
        now = 12_000L
        assertEquals(AudioDeliveryStatus.Disrupted, health.status())
    }

    @Test
    fun `rearming starts a fresh delivery deadline`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })
        health.arm()
        health.markDelivered()
        now = 7_000L
        assertEquals(AudioDeliveryStatus.Disrupted, health.status())

        health.arm()

        assertEquals(AudioDeliveryStatus.AwaitingAudio, health.status())
        now = 12_000L
        assertEquals(AudioDeliveryStatus.Disrupted, health.status())
        now = 17_000L
        assertEquals(AudioDeliveryStatus.Lost, health.status())
    }

    @Test
    fun `delivery while disarmed cannot publish healthy status`() {
        val health = AudioDeliveryHealth({ 1_000L })

        assertEquals(false, health.markDelivered())
        assertEquals(AudioDeliveryStatus.Disarmed, health.status())
    }

    @Test
    fun `disarming clears an active delivery deadline`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })
        health.arm()
        health.markDelivered()
        now = 20_000L
        assertEquals(AudioDeliveryStatus.Lost, health.status())

        health.disarm()

        assertEquals(AudioDeliveryStatus.Disarmed, health.status())
    }
}
