/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

internal object AudioFrameTiming {
    const val SAMPLE_RATE_HZ = 8_000
    const val FRAME_DURATION_MS = 20
    const val FRAME_SAMPLES = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1_000
}
