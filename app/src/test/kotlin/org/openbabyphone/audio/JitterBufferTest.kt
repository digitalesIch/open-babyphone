/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

import org.junit.Assert.assertArrayEquals
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
    fun `only capacity overflow results disrupt delivery`() {
        assertTrue(JitterBuffer.AddResult.AcceptedAfterDroppingOldest.indicatesOverflow())
        assertTrue(JitterBuffer.AddResult.DroppedOverflow.indicatesOverflow())
        assertFalse(JitterBuffer.AddResult.Accepted.indicatesOverflow())
        assertFalse(JitterBuffer.AddResult.DroppedDuplicate.indicatesOverflow())
        assertFalse(JitterBuffer.AddResult.DroppedLate.indicatesOverflow())
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

    @Test
    fun `addFrameFromScratch copies into pooled slots and drains in order`() {
        val buffer = JitterBuffer()
        val scratch = ByteArray(256) { 0x55 }

        assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 0, 128, 10, 200, 1_000L))
        assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 128, 128, 11, 220, 1_020L))
        assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 128, 128, 12, 240, 1_040L))

        val first = buffer.getFrame(0)!!
        val second = buffer.getFrame(0)!!
        assertEquals(10, first.seqNum)
        assertEquals(11, second.seqNum)
        assertEquals(128, first.ulawLength)
        assertEquals(128, second.ulawLength)
        assertArrayEquals(scratch.copyOfRange(0, 128), first.ulawData.copyOfRange(first.ulawOffset, first.ulawOffset + 128))
        assertArrayEquals(scratch.copyOfRange(128, 256), second.ulawData.copyOfRange(second.ulawOffset, second.ulawOffset + 128))
        buffer.releaseFrame(first)
        buffer.releaseFrame(second)
        buffer.releaseFrame(buffer.getFrame(0)!!)

        // All slots are free again and can serve a full capacity round trip.
        repeat(JitterBuffer.CAPACITY_FRAMES) { sequence ->
            assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 0, 128, 20 + sequence, 0, 1_000L))
        }
        repeat(JitterBuffer.CAPACITY_FRAMES) { sequence ->
            val frame = buffer.getFrame(0)!!
            assertEquals(20 + sequence, frame.seqNum)
            buffer.releaseFrame(frame)
        }
    }

    @Test
    fun `slot pool survives full buffer with borrowed frame without dropping or crashing`() {
        val buffer = JitterBuffer()
        val scratch = ByteArray(64)

        // Fill the buffer to capacity while one frame stays borrowed.
        repeat(JitterBuffer.CAPACITY_FRAMES) { sequence ->
            assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 0, 64, sequence, 0, 1_000L))
        }
        val borrowed = buffer.getFrame(0)!!
        assertEquals(0, borrowed.seqNum)

        // The borrowed frame freed one buffer slot, so one more frame is accepted.
        assertEquals(
            JitterBuffer.AddResult.Accepted,
            buffer.addFrameFromScratch(scratch, 0, 64, JitterBuffer.CAPACITY_FRAMES, 0, 1_100L)
        )
        // The buffer is full again; the oldest queued frame is dropped to make room.
        assertEquals(
            JitterBuffer.AddResult.AcceptedAfterDroppingOldest,
            buffer.addFrameFromScratch(scratch, 0, 64, JitterBuffer.CAPACITY_FRAMES + 1, 0, 1_120L)
        )

        buffer.releaseFrame(borrowed)
        val next = buffer.getFrame(0)!!
        assertEquals(2, next.seqNum)
        buffer.releaseFrame(next)
    }

    @Test
    fun `clear releases pooled slots borrowed and queued`() {
        val buffer = JitterBuffer()
        val scratch = ByteArray(64)
        repeat(JitterBuffer.CAPACITY_FRAMES) { sequence ->
            buffer.addFrameFromScratch(scratch, 0, 64, sequence, 0, 1_000L)
        }
        val borrowed = buffer.getFrame(0)!!
        buffer.addFrameFromScratch(scratch, 0, 64, 90, 0, 1_050L)

        buffer.clear()
        buffer.releaseFrame(borrowed)

        // After clear plus release the pool serves a full round trip again.
        repeat(JitterBuffer.CAPACITY_FRAMES) { sequence ->
            assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 0, 64, sequence, 0, 2_000L))
        }
        repeat(JitterBuffer.CAPACITY_FRAMES) { sequence ->
            val frame = buffer.getFrame(0)!!
            assertEquals(sequence, frame.seqNum)
            buffer.releaseFrame(frame)
        }
    }

    @Test
    fun `addFrameFromScratch never blocks or throws under receiver and playback contention`() {
        val buffer = JitterBuffer()
        val scratch = ByteArray(64)

        // Simulate the receiver outpacing playback: fill to capacity, borrow one
        // frame for playback, then keep inserting. Slots must recycle through
        // drops without the pool starving the receiver.
        var sequence = 0
        val borrowed = mutableListOf<JitterBuffer.DecodedFrame>()
        repeat(10) {
            val addResult = buffer.addFrameFromScratch(scratch, 0, 64, sequence, 0, 1_000L + sequence)
            sequence++
            assertTrue(
                addResult == JitterBuffer.AddResult.Accepted ||
                    addResult == JitterBuffer.AddResult.AcceptedAfterDroppingOldest ||
                    addResult == JitterBuffer.AddResult.DroppedOverflow
            )
            if (borrowed.size < 2 && buffer.getStats().levelFrames > 0) {
                buffer.getFrame(0)?.let { frame ->
                    borrowed += frame
                    if (borrowed.size == 2) buffer.releaseFrame(frame)
                }
            }
        }
        assertTrue(borrowed.isNotEmpty())

        // Everything borrowed and queued is reclaimable.
        buffer.clear()
        borrowed.forEach(buffer::releaseFrame)
        repeat(JitterBuffer.CAPACITY_FRAMES) { offset ->
            assertEquals(JitterBuffer.AddResult.Accepted, buffer.addFrameFromScratch(scratch, 0, 64, 100 + offset, 0, 2_000L))
        }
    }

    private fun frame(
        sequence: Int,
        timestampMs: Int = sequence * AudioFrameTiming.FRAME_DURATION_MS,
        receiveTime: Long = 1_000L + sequence * AudioFrameTiming.FRAME_DURATION_MS
    ) = JitterBuffer.DecodedFrame(sequence, timestampMs, byteArrayOf(sequence.toByte()), receiveTime)
}
