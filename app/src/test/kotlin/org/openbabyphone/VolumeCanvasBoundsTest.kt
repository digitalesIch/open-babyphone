package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCanvasBoundsTest {

    @Test
    fun `rollingLoudness returns zero for empty history`() {
        assertEquals(0f, rollingLoudness(floatArrayOf(), 1.0f), 0.001f)
    }

    @Test
    fun `rollingLoudness handles single sample without crash`() {
        val result = rollingLoudness(floatArrayOf(0.5f), 1.0f)
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `normalizedRecentSample returns zero for empty history`() {
        assertEquals(0f, normalizedRecentSample(floatArrayOf(), 1.0f, 0, 7), 0.001f)
    }

    @Test
    fun `normalizedRecentSample handles single sample without crash`() {
        val result = normalizedRecentSample(floatArrayOf(0.7f), 1.0f, 0, 7)
        assertEquals(0.7f, result, 0.001f)
    }

    @Test
    fun `normalizedRecentSample handles single sample at last index`() {
        val result = normalizedRecentSample(floatArrayOf(0.9f), 1.0f, 6, 7)
        assertEquals(0.9f, result, 0.001f)
    }

    @Test
    fun `audioSignalState returns no recent audio when history is empty`() {
        assertEquals(AudioSignalState.NoRecentAudio, audioSignalState(floatArrayOf(), 1.0f, 0L, 1000L))
    }

    @Test
    fun `audioSignalState returns no recent audio when frames are stale`() {
        assertEquals(AudioSignalState.NoRecentAudio, audioSignalState(floatArrayOf(0.3f), 1.0f, 1000L, 4000L))
    }

    @Test
    fun `audioSignalState returns quiet for fresh silent frames`() {
        assertEquals(AudioSignalState.Quiet, audioSignalState(floatArrayOf(0f, 0f), 1.0f, 1000L, 2000L))
    }

    @Test
    fun `audioSignalState returns sound detected for recent sound`() {
        assertEquals(
            AudioSignalState.SoundDetected,
            audioSignalState(floatArrayOf(0.1f, 0.2f), 1.0f, 1000L, 2000L)
        )
    }

    @Test
    fun `audioSignalState returns loud sound for recent loud frames`() {
        assertEquals(AudioSignalState.LoudSound, audioSignalState(floatArrayOf(0.8f, 0.9f), 1.0f, 1000L, 2000L))
    }

    @Test
    fun `signalBarCount maps states to glanceable levels`() {
        assertEquals(0, signalBarCount(AudioSignalState.NoRecentAudio, 0.0f))
        assertEquals(1, signalBarCount(AudioSignalState.Quiet, 0.01f))
        assertEquals(2, signalBarCount(AudioSignalState.SoundDetected, 0.1f))
        assertEquals(3, signalBarCount(AudioSignalState.SoundDetected, 0.5f))
        assertEquals(6, signalBarCount(AudioSignalState.LoudSound, 0.9f))
    }
}
