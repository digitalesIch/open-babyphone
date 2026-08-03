/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder

/** Minimal platform boundary for deterministic service audio-worker tests. */
internal interface AudioCaptureSource {
    fun start()
    fun read(samples: ShortArray, offset: Int, count: Int): Int
    fun stop()
    fun release()
}

internal interface AudioPlaybackSink {
    fun start()
    fun write(samples: ShortArray, offset: Int, count: Int): Int
    fun stop()
    fun release()
}

internal fun createAudioCaptureSource(
    frequency: Int,
    channelConfiguration: Int,
    audioEncoding: Int
): AudioCaptureSource? {
    val bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
    if (bufferSize <= 0) return null
    val record = try {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            frequency,
            channelConfiguration,
            audioEncoding,
            bufferSize
        )
    } catch (_: SecurityException) {
        return null
    }
    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        return null
    }
    return object : AudioCaptureSource {
        override fun start() = record.startRecording()

        override fun read(samples: ShortArray, offset: Int, count: Int): Int =
            record.read(samples, offset, count)

        override fun stop() = record.stop()

        override fun release() = record.release()
    }
}

internal fun createAudioPlaybackSink(
    frequency: Int,
    channelConfiguration: Int,
    audioEncoding: Int,
    attributes: AudioAttributes,
    maximumBufferSize: Int
): AudioPlaybackSink? {
    val bufferSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
    if (bufferSize <= 0 || bufferSize > maximumBufferSize) return null
    val track = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(frequency)
                .setChannelMask(channelConfiguration)
                .setEncoding(audioEncoding)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
        track.release()
        return null
    }
    return object : AudioPlaybackSink {
        override fun start() = track.play()

        override fun write(samples: ShortArray, offset: Int, count: Int): Int =
            track.write(samples, offset, count, AudioTrack.WRITE_NON_BLOCKING)

        override fun stop() = track.stop()

        override fun release() = track.release()
    }
}
