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
}