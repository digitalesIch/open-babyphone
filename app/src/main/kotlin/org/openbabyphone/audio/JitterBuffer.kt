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
 */
package org.openbabyphone.audio

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

/** A bounded, sequence-ordered audio buffer with adaptive pre-roll. */
internal class JitterBuffer {
    enum class AddResult {
        Accepted,
        AcceptedAfterDroppingOldest,
        DroppedDuplicate,
        DroppedLate,
        DroppedOverflow
    }

    class DecodedFrame(
        val seqNum: Int,
        val timestampMs: Int,
        val ulawData: ByteArray,
        val ulawOffset: Int,
        val ulawLength: Int,
        val receiveTime: Long
    ) {
        constructor(
            seqNum: Int,
            timestampMs: Int,
            ulawData: ByteArray,
            receiveTime: Long
        ) : this(seqNum, timestampMs, ulawData, 0, ulawData.size, receiveTime)
    }

    data class Stats(
        val totalFrames: Int,
        val droppedFrames: Int,
        val duplicateFrames: Int,
        val lateFrames: Int,
        val levelFrames: Int,
        val targetFrames: Int,
        val arrivalJitterMs: Double
    )

    /**
     * A window into this buffer's pooled frame storage. One window holds one
     * audio frame; the receiver thread copies decoded frames into windows and
     * the playback thread borrows them in sequence order.
     */
    private class SlotWindow(
        val offset: Int,
        val inUse: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(true)
    )

    private val slotStorage = ByteArray(SLOT_COUNT * MAX_FRAME_BYTES)
    private val slotWindows = Array(SLOT_COUNT) { index -> SlotWindow(index * MAX_FRAME_BYTES) }
    private val freeSlots = java.util.concurrent.ArrayBlockingQueue<SlotWindow>(SLOT_COUNT)

    init {
        repeat(SLOT_COUNT) { index ->
            slotWindows[index].inUse.set(false)
            check(freeSlots.offer(slotWindows[index]))
        }
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val frames = arrayOfNulls<DecodedFrame>(CAPACITY_FRAMES)
    private var size = 0
    private var lastPlayedSequence = -1
    private var playbackStarted = false
    private var preRollRequired = true
    private var totalFrames = 0
    private var droppedFrames = 0
    private var duplicateFrames = 0
    private var lateFrames = 0
    private var targetFrames = BASE_TARGET_FRAMES
    private var targetDecayObservations = 0
    private var jitterInitialized = false
    private var previousReceiveTime = 0L
    private var previousTimestamp = 0L
    private var arrivalJitterMs = 0.0

    fun addFrame(frame: DecodedFrame): AddResult = lock.withLock {
        totalFrames++
        if (frame.seqNum <= lastPlayedSequence) {
            lateFrames++
            droppedFrames++
            releaseFrame(frame)
            return AddResult.DroppedLate
        }

        var insertionIndex = 0
        while (insertionIndex < size && frames[insertionIndex]!!.seqNum < frame.seqNum) {
            insertionIndex++
        }
        if (insertionIndex < size && frames[insertionIndex]!!.seqNum == frame.seqNum) {
            duplicateFrames++
            droppedFrames++
            releaseFrame(frame)
            return AddResult.DroppedDuplicate
        }

        observeArrival(frame.receiveTime, frame.timestampMs)
        if (size == CAPACITY_FRAMES) {
            droppedFrames++
            if (insertionIndex == 0) {
                releaseFrame(frame)
                return AddResult.DroppedOverflow
            }

            for (index in 1 until size) frames[index - 1] = frames[index]
            releaseFrame(frames[--size]!!)
            insertionIndex--
            insertAt(insertionIndex, frame)
            changed.signalAll()
            return AddResult.AcceptedAfterDroppingOldest
        }

        insertAt(insertionIndex, frame)
        changed.signalAll()
        AddResult.Accepted
    }

    /**
     * Reserves the next slot window, copies [ulawLength] bytes from the scratch
     * buffer into it, and inserts the result in sequence order. The scratch
     * buffer can be reused immediately after this call returns; the slot
     * window is reclaimed automatically in every non-accepted path. Returns
     * [AddResult.DroppedOverflow] when no slot window is available.
     */
    fun addFrameFromScratch(
        scratch: ByteArray,
        ulawOffset: Int,
        ulawLength: Int,
        seqNum: Int,
        timestampMs: Int,
        receiveTime: Long
    ): AddResult {
        require(ulawOffset >= 0 && ulawLength >= 0 && ulawOffset <= scratch.size - ulawLength)
        val window = freeSlots.poll() ?: return AddResult.DroppedOverflow
        window.inUse.set(true)
        scratch.copyInto(slotStorage, window.offset, ulawOffset, ulawOffset + ulawLength)
        return addFrame(
            DecodedFrame(seqNum, timestampMs, slotStorage, window.offset, ulawLength, receiveTime)
        )
    }

    /**
     * Returns a borrowed window after the consumer finished reading it. Must
     * be called once for every frame returned by [getFrame].
     */
    fun releaseFrame(frame: DecodedFrame) {
        if (frame.ulawData !== slotStorage) return
        if (frame.ulawOffset % MAX_FRAME_BYTES != 0) return
        val window = slotWindows[frame.ulawOffset / MAX_FRAME_BYTES]
        if (window.inUse.compareAndSet(true, false)) {
            check(freeSlots.offer(window))
        }
    }

    /**
     * Returns sequence-ordered audio. Playback waits for the adaptive target
     * initially and after an underrun; a timeout during playback starts pre-roll.
     */
    fun getFrame(timeoutMs: Long = AudioFrameTiming.FRAME_DURATION_MS.toLong()): DecodedFrame? {
        require(timeoutMs >= 0)
        return lock.withLock {
            var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (size == 0 || (preRollRequired && size < targetFrames)) {
                if (remainingNanos <= 0) {
                    if (playbackStarted) preRollRequired = true
                    return null
                }
                remainingNanos = changed.awaitNanos(remainingNanos)
            }

            val frame = frames[0]!!
            for (index in 1 until size) frames[index - 1] = frames[index]
            frames[--size] = null
            lastPlayedSequence = frame.seqNum
            playbackStarted = true
            preRollRequired = false
            frame
        }
    }

    fun isReady(): Boolean = lock.withLock { size >= targetFrames }

    fun hasPlaybackStarted(): Boolean = lock.withLock { playbackStarted }

    fun getBufferLevelMs(): Int = lock.withLock { size * AudioFrameTiming.FRAME_DURATION_MS }

    fun getTargetFrames(): Int = lock.withLock { targetFrames }

    fun getArrivalJitterMs(): Double = lock.withLock { arrivalJitterMs }

    fun getDroppedFrameCount(): Int = lock.withLock { droppedFrames }

    fun getStats(): Stats = lock.withLock {
        Stats(
            totalFrames,
            droppedFrames,
            duplicateFrames,
            lateFrames,
            size,
            targetFrames,
            arrivalJitterMs
        )
    }

    fun clear() {
        val borrowed = lock.withLock {
            val borrowed = frames.filterNotNull().filter { it.ulawData === slotStorage }
            frames.fill(null)
            size = 0
            lastPlayedSequence = -1
            playbackStarted = false
            preRollRequired = true
            totalFrames = 0
            droppedFrames = 0
            duplicateFrames = 0
            lateFrames = 0
            targetFrames = BASE_TARGET_FRAMES
            targetDecayObservations = 0
            jitterInitialized = false
            previousReceiveTime = 0L
            previousTimestamp = 0L
            arrivalJitterMs = 0.0
            changed.signalAll()
            borrowed
        }
        borrowed.forEach(::releaseFrame)
    }

    private fun insertAt(index: Int, frame: DecodedFrame) {
        for (position in size downTo index + 1) frames[position] = frames[position - 1]
        frames[index] = frame
        size++
    }

    private fun observeArrival(receiveTime: Long, timestampMs: Int) {
        val timestamp = timestampMs.toLong() and UINT_MASK
        if (!jitterInitialized) {
            jitterInitialized = true
            previousReceiveTime = receiveTime
            previousTimestamp = timestamp
            return
        }

        val senderDelta = (timestamp - previousTimestamp) and UINT_MASK
        val receiveDelta = receiveTime - previousReceiveTime
        if (senderDelta > MAX_FORWARD_DELTA || receiveDelta < 0) return

        val variation = abs(receiveDelta - senderDelta).toDouble()
        arrivalJitterMs += (variation - arrivalJitterMs) / JITTER_SMOOTHING_DIVISOR
        previousReceiveTime = receiveTime
        previousTimestamp = timestamp

        val desiredTarget = (
            BASE_TARGET_FRAMES +
                (arrivalJitterMs * JITTER_MARGIN / AudioFrameTiming.FRAME_DURATION_MS).toInt()
            ).coerceIn(BASE_TARGET_FRAMES, MAX_TARGET_FRAMES)
        when {
            desiredTarget > targetFrames -> {
                targetFrames = desiredTarget
                targetDecayObservations = 0
                changed.signalAll()
            }
            desiredTarget < targetFrames -> {
                targetDecayObservations++
                if (targetDecayObservations >= TARGET_DECAY_OBSERVATIONS) {
                    targetFrames--
                    targetDecayObservations = 0
                    changed.signalAll()
                }
            }
            else -> targetDecayObservations = 0
        }
    }

    companion object {
        const val BASE_TARGET_FRAMES = 3
        const val MAX_TARGET_FRAMES = 6
        const val CAPACITY_FRAMES = 6

        /**
         * One spare window beyond the buffer capacity serves a frame that is
         * borrowed for playback while the buffer is completely full; a second
         * spare keeps the receiver from starving when a borrowed frame is
         * still being decoded and written.
         */
        const val SLOT_COUNT = CAPACITY_FRAMES + 2
        const val MAX_FRAME_BYTES = FrameCodec.MAX_G711_AUDIO_SIZE
        private const val JITTER_SMOOTHING_DIVISOR = 16.0
        private const val JITTER_MARGIN = 4.0
        private const val TARGET_DECAY_OBSERVATIONS = 24
        private const val UINT_MASK = 0xffff_ffffL
        private const val MAX_FORWARD_DELTA = 0x7fff_ffffL
    }
}

internal fun JitterBuffer.AddResult.indicatesOverflow(): Boolean = when (this) {
    JitterBuffer.AddResult.AcceptedAfterDroppingOldest,
    JitterBuffer.AddResult.DroppedOverflow -> true
    JitterBuffer.AddResult.Accepted,
    JitterBuffer.AddResult.DroppedDuplicate,
    JitterBuffer.AddResult.DroppedLate -> false
}
