package org.openbabyphone.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFrameTimingTest {
    @Test
    fun `producer frame is exactly 20 milliseconds at 8 kHz`() {
        assertEquals(8_000, AudioFrameTiming.SAMPLE_RATE_HZ)
        assertEquals(20, AudioFrameTiming.FRAME_DURATION_MS)
        assertEquals(160, AudioFrameTiming.FRAME_SAMPLES)
        assertEquals(
            AudioFrameTiming.FRAME_SAMPLES,
            AudioFrameTiming.SAMPLE_RATE_HZ * AudioFrameTiming.FRAME_DURATION_MS / 1_000
        )
    }
}
