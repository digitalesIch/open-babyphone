package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class VolumeHistoryTest {

    @Test
    fun `empty history has size zero`() {
        val history = VolumeHistory(100)
        assertEquals(0, history.size())
    }

    @Test
    fun `adding audio data increases size`() {
        val history = VolumeHistory(100)
        val audioData = ShortArray(100) { 64 }
        history.onAudioData(audioData)
        ShadowLooper.idleMainLooper()
        assertEquals(1, history.size())
    }

    @Test
    fun `empty audio data does not add entry`() {
        val history = VolumeHistory(100)
        history.onAudioData(ShortArray(0))
        ShadowLooper.idleMainLooper()
        assertEquals(0, history.size())
    }

    @Test
    fun `history does not exceed max size`() {
        val history = VolumeHistory(5)
        repeat(10) {
            history.onAudioData(ShortArray(10) { 64 })
        }
        ShadowLooper.idleMainLooper()
        assertEquals(5, history.size())
    }

    @Test
    fun `volume norm is positive`() {
        val history = VolumeHistory(100)
        assertTrue(history.volumeNorm > 0)
    }

    @Test
    fun `volume values are within valid range`() {
        val history = VolumeHistory(100)
        history.onAudioData(ShortArray(100) { 64 })
        ShadowLooper.idleMainLooper()
        assertTrue(history.size() > 0)
        val value = history[0]
        assertTrue(value >= 0.0)
        assertTrue(value <= 1.0)
    }

    @Test
    fun `offset and length ignore samples outside the requested range`() {
        val history = VolumeHistory(10)
        val audioData = shortArrayOf(Short.MAX_VALUE, 64, 64, Short.MAX_VALUE)

        history.onAudioData(audioData, 1, 2)
        ShadowLooper.idleMainLooper()

        assertEquals(0.25, history[0], 0.001)
    }
}
