/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {
    @Test
    fun `out of order insertion returns sequence sorted frames`() {
        val buffer = JitterBuffer()
        buffer.addFrame(frame(12))
        buffer.addFrame(frame(10))
        buffer.addFrame(frame(11))

        assertEquals(10, buffer.getFrame(0)?.seqNum)
        assertEquals(11, buffer.getFrame(0)?.seqNum)
        assertEquals(12, buffer.getFrame(0)?.seqNum)
    }

    @Test
    fun `duplicates and already played frames are dropped`() {
        val buffer = JitterBuffer()
        assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrame(frame(0)))
        assertEquals(JitterBuffer.AddResult.DroppedDuplicate, buffer.addFrame(frame(0)))
        buffer.addFrame(frame(1))
        buffer.addFrame(frame(2))
        assertEquals(0, buffer.getFrame(0)?.seqNum)

        assertEquals(JitterBuffer.AddResult.DroppedLate, buffer.addFrame(frame(0)))
        assertEquals(2, buffer.getDroppedFrameCount())
        assertEquals(1, buffer.getStats().duplicateFrames)
        assertEquals(1, buffer.getStats().lateFrames)
    }

    @Test
    fun `pre-roll gates playback until adaptive target is buffered`() {
        val buffer = JitterBuffer()
        buffer.addFrame(frame(0))
        buffer.addFrame(frame(1))

        assertFalse(buffer.isReady())
        assertNull(buffer.getFrame(0))
        assertFalse(buffer.hasPlaybackStarted())

        buffer.addFrame(frame(2))

        assertTrue(buffer.isReady())
        assertEquals(0, buffer.getFrame(0)?.seqNum)
        assertTrue(buffer.hasPlaybackStarted())
    }

    @Test
    fun `heartbeat sequence gaps do not create missing audio frames`() {
        val buffer = JitterBuffer()
        buffer.addFrame(frame(0))
        buffer.addFrame(frame(2))
        buffer.addFrame(frame(3))

        assertEquals(0, buffer.getFrame(0)?.seqNum)
        assertEquals(2, buffer.getFrame(0)?.seqNum)
        assertEquals(3, buffer.getFrame(0)?.seqNum)
    }

    @Test
    fun `underrun reapplies adaptive pre-roll before playback resumes`() {
        val buffer = JitterBuffer()
        repeat(JitterBuffer.BASE_TARGET_FRAMES) { buffer.addFrame(frame(it)) }
        repeat(JitterBuffer.BASE_TARGET_FRAMES) { assertEquals(it, buffer.getFrame(0)?.seqNum) }

        assertNull(buffer.getFrame(0))
        buffer.addFrame(frame(3))
        buffer.addFrame(frame(4))
        assertNull(buffer.getFrame(0))

        buffer.addFrame(frame(5))
        assertEquals(3, buffer.getFrame(0)?.seqNum)
    }

    @Test
    fun `overflow removes oldest audio and bounds queued latency`() {
        val buffer = JitterBuffer()
        repeat(JitterBuffer.CAPACITY_FRAMES) { buffer.addFrame(frame(it)) }

        assertEquals(
            JitterBuffer.AddResult.AcceptedAfterDroppingOldest,
            buffer.addFrame(frame(JitterBuffer.CAPACITY_FRAMES))
        )

        assertEquals(1, buffer.getDroppedFrameCount())
        assertEquals(JitterBuffer.CAPACITY_FRAMES * AudioFrameTiming.FRAME_DURATION_MS, buffer.getBufferLevelMs())
        assertEquals(1, buffer.getFrame(0)?.seqNum)
        repeat(JitterBuffer.CAPACITY_FRAMES - 2) { buffer.getFrame(0) }
        assertEquals(JitterBuffer.CAPACITY_FRAMES, buffer.getFrame(0)?.seqNum)
    }

    @Test
    fun `older incoming frame is overflow victim`() {
        val buffer = JitterBuffer()
        for (sequence in 1..JitterBuffer.CAPACITY_FRAMES) buffer.addFrame(frame(sequence))

        assertEquals(JitterBuffer.AddResult.DroppedOverflow, buffer.addFrame(frame(0)))
        assertEquals(1, buffer.getFrame(0)?.seqNum)
    }

    @Test
    fun `stable arrivals keep base target`() {
        val buffer = JitterBuffer()
        repeat(20) { index ->
            buffer.addFrame(frame(index, index * 20, 1_000L + index * 20L))
        }

        assertEquals(0.0, buffer.getArrivalJitterMs(), 0.0)
        assertEquals(JitterBuffer.BASE_TARGET_FRAMES, buffer.getTargetFrames())
    }

    @Test
    fun `jitter raises target and stable arrivals decay it conservatively`() {
        val buffer = JitterBuffer()
        var receiveTime = 1_000L
        repeat(80) { index ->
            receiveTime += if (index % 2 == 0) 0 else 40
            buffer.addFrame(frame(index, index * 20, receiveTime))
        }
        val raisedTarget = buffer.getTargetFrames()

        repeat(240) { offset ->
            val index = 80 + offset
            receiveTime += 20
            buffer.addFrame(frame(index, index * 20, receiveTime))
        }

        assertEquals(JitterBuffer.MAX_TARGET_FRAMES, raisedTarget)
        assertEquals(JitterBuffer.BASE_TARGET_FRAMES, buffer.getTargetFrames())
    }

    @Test
    fun `timestamp wrap does not create arrival jitter`() {
        val buffer = JitterBuffer()
        buffer.addFrame(frame(0, -21, 1_000L))
        buffer.addFrame(frame(1, -1, 1_020L))
        buffer.addFrame(frame(2, 19, 1_040L))

        assertEquals(0.0, buffer.getArrivalJitterMs(), 0.0)
        assertEquals(JitterBuffer.BASE_TARGET_FRAMES, buffer.getTargetFrames())
    }

    @Test
    fun `clear resets ordering pre-roll and jitter state`() {
        val buffer = JitterBuffer()
        buffer.addFrame(frame(0, 0, 1_000L))
        buffer.addFrame(frame(1, 20, 1_100L))
        buffer.addFrame(frame(2, 40, 1_120L))
        buffer.getFrame(0)

        buffer.clear()

        assertEquals(0, buffer.getBufferLevelMs())
        assertEquals(0.0, buffer.getArrivalJitterMs(), 0.0)
        assertEquals(JitterBuffer.BASE_TARGET_FRAMES, buffer.getTargetFrames())
        assertFalse(buffer.hasPlaybackStarted())
        assertEquals(0, buffer.getStats().totalFrames)
    }

    private fun frame(
        sequence: Int,
        timestampMs: Int = sequence * AudioFrameTiming.FRAME_DURATION_MS,
        receiveTime: Long = 1_000L + sequence * AudioFrameTiming.FRAME_DURATION_MS
    ) = JitterBuffer.DecodedFrame(sequence, timestampMs, byteArrayOf(sequence.toByte()), receiveTime)
}
