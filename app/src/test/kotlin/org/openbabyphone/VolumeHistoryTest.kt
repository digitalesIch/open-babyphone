package org.openbabyphone

import org.junit.Assert.assertEquals
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
    fun `pending samples from multiple frames drain in order without loss`() {
        val history = VolumeHistory(100)
        val loud = ShortArray(160) { 128 }
        val silent = ShortArray(160) { 0 }

        // Post several frames from the playback thread before the main
        // looper runs; each frame must still become one history sample.
        history.onAudioData(loud)
        history.onAudioData(loud)
        history.onAudioData(silent)

        ShadowLooper.idleMainLooper()

        assertEquals(3, history.size())
        assertEquals(1.0, history[0], 0.001)
        assertEquals(1.0, history[1], 0.001)
        assertEquals(0.0, history[2], 0.001)
    }
}
