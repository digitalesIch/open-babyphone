package org.openbabyphone.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSampleWriterTest {
    @Test
    fun `partial writes continue from the written offset until complete`() {
        val writes = mutableListOf<Pair<Int, Int>>()

        val result = writeAllAudioSamples(
            sampleCount = 7,
            write = { offset, count ->
                writes += offset to count
                minOf(3, count)
            },
            elapsedRealtime = { 0L },
            pauseAfterNoProgress = {}
        )

        assertEquals(AudioWriteResult.Complete, result)
        assertEquals(listOf(0 to 7, 3 to 4, 6 to 1), writes)
    }

    @Test
    fun `negative write result fails immediately`() {
        val result = writeAllAudioSamples(
            sampleCount = 4,
            write = { _, _ -> -6 },
            elapsedRealtime = { 0L },
            pauseAfterNoProgress = {}
        )

        assertEquals(AudioWriteResult.Failed, result)
    }

    @Test
    fun `repeated zero writes fail after monotonic stall deadline`() {
        var now = 100L

        val result = writeAllAudioSamples(
            sampleCount = 4,
            write = { _, _ -> 0 },
            elapsedRealtime = { now },
            pauseAfterNoProgress = { now += 25L },
            stallTimeoutMs = 100L
        )

        assertEquals(AudioWriteResult.Stalled, result)
    }

    @Test
    fun `interruption stops an incomplete write`() {
        val result = writeAllAudioSamples(
            sampleCount = 4,
            write = { _, _ -> 0 },
            elapsedRealtime = { 0L },
            pauseAfterNoProgress = {},
            isInterrupted = { true }
        )

        assertEquals(AudioWriteResult.Interrupted, result)
    }
}
