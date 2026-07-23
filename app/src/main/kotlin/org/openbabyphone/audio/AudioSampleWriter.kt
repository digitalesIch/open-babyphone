/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

internal enum class AudioWriteResult {
    Complete,
    Failed,
    Stalled,
    Interrupted
}

internal inline fun writeAllAudioSamples(
    sampleCount: Int,
    write: (offset: Int, sampleCount: Int) -> Int,
    elapsedRealtime: () -> Long,
    pauseAfterNoProgress: () -> Unit,
    stallTimeoutMs: Long = 5_000L,
    isInterrupted: () -> Boolean = { Thread.currentThread().isInterrupted }
): AudioWriteResult {
    var offset = 0
    var lastProgressAt = elapsedRealtime()
    while (offset < sampleCount) {
        if (isInterrupted()) return AudioWriteResult.Interrupted

        val remaining = sampleCount - offset
        val written = write(offset, remaining)
        if (written < 0 || written > remaining) return AudioWriteResult.Failed
        if (written == 0) {
            if (elapsedRealtime() - lastProgressAt >= stallTimeoutMs) {
                return AudioWriteResult.Stalled
            }
            pauseAfterNoProgress()
            continue
        }

        offset += written
        lastProgressAt = elapsedRealtime()
    }
    return AudioWriteResult.Complete
}
