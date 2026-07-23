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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeStatisticsTest {

    @Test
    fun `empty statistics has size zero`() {
        val stats = VolumeStatistics(100)
        assertEquals(0, stats.size())
    }

    @Test
    fun `addLast increases size`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.5)
        assertEquals(1, stats.size())
    }

    @Test
    fun `ring buffer does not exceed max size`() {
        val stats = VolumeStatistics(3)
        stats.addLast(0.1)
        stats.addLast(0.2)
        stats.addLast(0.3)
        stats.addLast(0.4)
        stats.addLast(0.5)
        assertEquals(3, stats.size())
    }

    @Test
    fun `ring buffer drops oldest when full`() {
        val stats = VolumeStatistics(3)
        stats.addLast(0.1)
        stats.addLast(0.2)
        stats.addLast(0.3)
        stats.addLast(0.4)
        assertEquals(0.2, stats[0], 0.001)
        assertEquals(0.3, stats[1], 0.001)
        assertEquals(0.4, stats[2], 0.001)
    }

    @Test
    fun `normalization uses initial max when values are small`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.1)
        stats.addLast(0.1)
        stats.addLast(0.1)
        assertEquals(1.0 / 0.25, stats.volumeNorm, 0.001)
    }

    @Test
    fun `normalization updates when a larger value appears`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.1)
        assertEquals(1.0 / 0.25, stats.volumeNorm, 0.001)
        stats.addLast(0.5)
        assertEquals(1.0 / 0.5, stats.volumeNorm, 0.001)
    }

    @Test
    fun `max volume decays slowly after loud transient`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.8)
        val normAfterLoud = stats.volumeNorm
        repeat(1000) {
            stats.addLast(0.01)
        }
        assertTrue(
            "volumeNorm should increase (maxVolume should decay) after many quiet samples",
            stats.volumeNorm > normAfterLoud
        )
    }

    @Test
    fun `max volume does not decay below floor`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.01)
        repeat(10000) {
            stats.addLast(0.01)
        }
        assertTrue(
            "volumeNorm should not exceed 1.0 / floor (maxVolume should not go below floor)",
            stats.volumeNorm <= 1.0 / 0.25 + 0.001
        )
    }

    @Test
    fun `decay allows visualization to recover after loud transient`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.9)
        val normDuringLoud = stats.volumeNorm
        repeat(5000) {
            stats.addLast(0.1)
        }
        val normAfterRecovery = stats.volumeNorm
        assertTrue(
            "After many samples, norm should be higher (maxVolume lower) than during loud period",
            normAfterRecovery > normDuringLoud
        )
        assertTrue(
            "But norm should not over-amplify: maxVolume should be >= 0.1 (current volume)",
            stats.volumeNorm <= 1.0 / 0.1 + 0.001
        )
    }

    @Test
    fun `onAudioData with empty array does not add entry`() {
        val stats = VolumeStatistics(100)
        stats.onAudioData(ShortArray(0))
        assertEquals(0, stats.size())
    }

    @Test
    fun `onAudioData computes mean square volume`() {
        val stats = VolumeStatistics(100)
        stats.onAudioData(ShortArray(10) { 64 })
        assertEquals(1, stats.size())
        val expected = (64.0 / 128.0) * (64.0 / 128.0)
        assertEquals(expected, stats[0], 0.001)
    }

    @Test
    fun `onAudioData with zero samples produces zero volume`() {
        val stats = VolumeStatistics(100)
        stats.onAudioData(ShortArray(10) { 0 })
        assertEquals(0.0, stats[0], 0.001)
    }

    @Test
    fun `onAudioData with max samples produces max volume`() {
        val stats = VolumeStatistics(100)
        stats.onAudioData(ShortArray(10) { Short.MAX_VALUE })
        val expected = (Short.MAX_VALUE.toDouble() / 128.0)
        val expectedVolume = expected * expected
        assertEquals(expectedVolume, stats[0], 1.0)
    }

    @Test
    fun `onAudioData offset and length ignore surrounding samples`() {
        val stats = VolumeStatistics(10)

        stats.onAudioData(shortArrayOf(Short.MAX_VALUE, 32, 64, Short.MAX_VALUE), 1, 2)

        val expected = ((32.0 / 128.0).let { it * it } + (64.0 / 128.0).let { it * it }) / 2.0
        assertEquals(expected, stats[0], 0.001)
    }

    @Test
    fun `get throws IndexOutOfBoundsException for invalid index`() {
        val stats = VolumeStatistics(100)
        stats.addLast(0.5)
        var threw = false
        try {
            stats[5]
        } catch (e: IndexOutOfBoundsException) {
            threw = true
        }
        assertTrue(threw)
    }
}
