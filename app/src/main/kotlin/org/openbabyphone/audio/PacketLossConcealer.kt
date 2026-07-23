/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

/** Repeats the last decoded frame with a short fade, then emits silence. */
internal class PacketLossConcealer(
    private val frameSamples: Int = AudioFrameTiming.FRAME_SAMPLES
) {
    private val lastGoodPcm = ShortArray(frameSamples)
    private var lastGoodSampleCount = 0
    private var consecutiveLosses = 0

    fun onRealFrame(samples: ShortArray, sampleCount: Int, offset: Int = 0) {
        require(sampleCount in 1..frameSamples)
        require(offset >= 0 && offset <= samples.size - sampleCount)
        samples.copyInto(lastGoodPcm, startIndex = offset, endIndex = offset + sampleCount)
        lastGoodSampleCount = sampleCount
        consecutiveLosses = 0
    }

    /** Fills [output] without allocating and returns the number of samples to write. */
    fun concealInto(output: ShortArray): Int {
        require(output.size >= frameSamples)
        val sampleCount = lastGoodSampleCount.takeIf { it > 0 } ?: frameSamples
        if (consecutiveLosses < FADE_FRAMES && lastGoodSampleCount > 0) {
            val numerator = FADE_FRAMES - consecutiveLosses
            val denominator = FADE_FRAMES + 1
            for (index in 0 until sampleCount) {
                output[index] = (lastGoodPcm[index].toInt() * numerator / denominator).toShort()
            }
        } else {
            output.fill(0, 0, sampleCount)
        }
        if (consecutiveLosses < FADE_FRAMES) consecutiveLosses++
        return sampleCount
    }

    fun consecutiveLosses(): Int = consecutiveLosses

    fun clear() {
        lastGoodPcm.fill(0)
        lastGoodSampleCount = 0
        consecutiveLosses = 0
    }

    companion object {
        const val FADE_FRAMES = 3
    }
}
