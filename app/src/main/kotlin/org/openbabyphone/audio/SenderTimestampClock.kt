/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

/** Maps wrapping unsigned 32-bit sender milliseconds onto elapsed realtime. */
internal class SenderTimestampClock {
    private var initialized = false
    private var previousTimestamp = 0L
    private var senderElapsed = 0L
    private var receiverOrigin = 0L

    fun frameAgeMillis(receiveTime: Long, timestampMs: Int): Long {
        val timestamp = timestampMs.toLong() and UINT_MASK
        if (!initialized) {
            initialized = true
            previousTimestamp = timestamp
            receiverOrigin = receiveTime
            return 0L
        }

        val delta = (timestamp - previousTimestamp) and UINT_MASK
        if (delta <= MAX_FORWARD_DELTA) {
            senderElapsed += delta
            previousTimestamp = timestamp
        }
        return receiveTime - (receiverOrigin + senderElapsed)
    }

    private companion object {
        const val UINT_MASK = 0xffff_ffffL
        const val MAX_FORWARD_DELTA = 0x7fff_ffffL
    }
}
