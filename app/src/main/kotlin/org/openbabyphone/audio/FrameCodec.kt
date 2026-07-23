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
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone.audio

import org.openbabyphone.CryptoHelper

internal sealed interface FrameSequenceDecision {
    data object Exact : FrameSequenceDecision
    data class ForwardGap(val missingFrames: Int) : FrameSequenceDecision
    data object Replay : FrameSequenceDecision
    data object InvalidFirst : FrameSequenceDecision
}

internal class FrameSequence(firstSequence: Int) {
    private var expected = firstSequence
    private var firstPending = true

    init {
        require(firstSequence >= 0)
    }

    fun classify(sequence: Int): FrameSequenceDecision = when {
        sequence < expected -> FrameSequenceDecision.Replay
        firstPending && sequence != expected -> FrameSequenceDecision.InvalidFirst
        sequence == expected -> FrameSequenceDecision.Exact
        else -> FrameSequenceDecision.ForwardGap(sequence - expected)
    }

    fun acceptAuthenticated(sequence: Int): FrameSequenceDecision {
        val decision = classify(sequence)
        if (decision is FrameSequenceDecision.Exact || decision is FrameSequenceDecision.ForwardGap) {
            if (sequence == Int.MAX_VALUE) return FrameSequenceDecision.InvalidFirst
            expected = sequence + 1
            firstPending = false
        }
        return decision
    }
}

object FrameCodec {
    const val HEADER_SIZE = FrameHeader.SIZE
    const val AUTH_TAG_SIZE = CryptoHelper.AUTH_TAG_SIZE
    const val FLAG_AUDIO = FrameHeader.FLAG_AUDIO
    const val FLAG_HEARTBEAT = FrameHeader.FLAG_HEARTBEAT

    // Four seconds of 8 kHz G.711 audio is a generous upper bound for one frame.
    const val MAX_G711_AUDIO_SIZE = 32_000
    const val MAX_ENCRYPTED_AUDIO_SIZE = MAX_G711_AUDIO_SIZE + AUTH_TAG_SIZE
    const val MAX_FRAME_SIZE = HEADER_SIZE + MAX_ENCRYPTED_AUDIO_SIZE

    private val EMPTY_PAYLOAD = ByteArray(0)

    data class DecodedFrame(
        val seqNum: Int,
        val timestampMs: Int,
        val ulawData: ByteArray,
        val isHeartbeat: Boolean
    )

    fun encodeFrame(
        ulawData: ByteArray,
        seqNum: Int,
        timestampMs: Int,
        key: ByteArray,
        sessionId: ByteArray
    ): ByteArray {
        require(seqNum >= 0) { "Sequence number must be non-negative" }
        require(ulawData.size in 1..MAX_G711_AUDIO_SIZE) { "Invalid G.711 audio payload size" }
        return ByteArray(HEADER_SIZE + ulawData.size + AUTH_TAG_SIZE).also { output ->
            encodeFrameInto(ulawData, seqNum, timestampMs, key, sessionId, output)
        }
    }

    fun encodeFrameInto(
        ulawData: ByteArray,
        seqNum: Int,
        timestampMs: Int,
        key: ByteArray,
        sessionId: ByteArray,
        output: ByteArray,
        outputOffset: Int = 0
    ): Int = encodeFrameInto(
        ulawData,
        0,
        ulawData.size,
        seqNum,
        timestampMs,
        key,
        sessionId,
        output,
        outputOffset
    )

    fun encodeFrameInto(
        ulawData: ByteArray,
        ulawOffset: Int,
        ulawLength: Int,
        seqNum: Int,
        timestampMs: Int,
        key: ByteArray,
        sessionId: ByteArray,
        output: ByteArray,
        outputOffset: Int = 0
    ): Int {
        requireRange(ulawData.size, ulawOffset, ulawLength, "G.711 audio")
        require(ulawLength in 1..MAX_G711_AUDIO_SIZE) { "Invalid G.711 audio payload size" }
        return encodeInto(
            FLAG_AUDIO,
            ulawData,
            ulawOffset,
            ulawLength,
            seqNum,
            timestampMs,
            key,
            sessionId,
            output,
            outputOffset
        )
    }

    fun encodeHeartbeat(
        seqNum: Int,
        timestampMs: Int,
        key: ByteArray,
        sessionId: ByteArray
    ): ByteArray {
        require(seqNum >= 0) { "Sequence number must be non-negative" }
        return ByteArray(HEADER_SIZE + AUTH_TAG_SIZE).also { output ->
            encodeHeartbeatInto(seqNum, timestampMs, key, sessionId, output)
        }
    }

    fun encodeHeartbeatInto(
        seqNum: Int,
        timestampMs: Int,
        key: ByteArray,
        sessionId: ByteArray,
        output: ByteArray,
        outputOffset: Int = 0
    ): Int = encodeInto(
        FLAG_HEARTBEAT,
        EMPTY_PAYLOAD,
        0,
        0,
        seqNum,
        timestampMs,
        key,
        sessionId,
        output,
        outputOffset
    )

    fun isValidHeader(header: FrameHeader): Boolean = when (header.flags) {
        FLAG_AUDIO -> header.payloadLength in (AUTH_TAG_SIZE + 1)..MAX_ENCRYPTED_AUDIO_SIZE
        FLAG_HEARTBEAT -> header.payloadLength == AUTH_TAG_SIZE
        else -> false
    } && header.seqNum >= 0

    fun decodeFrame(
        header: FrameHeader,
        payload: ByteArray,
        key: ByteArray,
        sessionId: ByteArray
    ): DecodedFrame? = decodeFrame(header, payload, 0, payload.size, key, sessionId)

    fun decodeFrame(
        header: FrameHeader,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLength: Int,
        key: ByteArray,
        sessionId: ByteArray
    ): DecodedFrame? {
        if (payloadOffset < 0 || payloadLength < 0 || payloadOffset > payload.size - payloadLength) return null
        if (!isValidHeader(header) || payloadLength != header.payloadLength) return null
        val plaintext = CryptoHelper.decryptChunk(
            payload,
            payloadOffset,
            payloadLength,
            key,
            sessionId,
            header.seqNum.toLong(),
            header.toByteArray()
        ) ?: return null
        return when (header.flags) {
            FLAG_HEARTBEAT -> plaintext.takeIf { it.isEmpty() }?.let {
                DecodedFrame(header.seqNum, header.timestampMs, it, true)
            }
            FLAG_AUDIO -> plaintext.takeIf { it.size in 1..MAX_G711_AUDIO_SIZE }?.let {
                DecodedFrame(header.seqNum, header.timestampMs, it, false)
            }
            else -> null
        }
    }

    private fun encodeInto(
        flags: Byte,
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
        seqNum: Int,
        timestampMs: Int,
        key: ByteArray,
        sessionId: ByteArray,
        output: ByteArray,
        outputOffset: Int
    ): Int {
        require(seqNum >= 0) { "Sequence number must be non-negative" }
        val encryptedLength = plaintextLength + AUTH_TAG_SIZE
        val frameLength = HEADER_SIZE + encryptedLength
        requireRange(output.size, outputOffset, frameLength, "output")
        writeHeader(output, outputOffset, flags, seqNum, timestampMs, encryptedLength)
        val encryptedWritten = CryptoHelper.encryptChunkInto(
            plaintext,
            plaintextOffset,
            plaintextLength,
            key,
            sessionId,
            seqNum.toLong(),
            output,
            outputOffset,
            HEADER_SIZE,
            output,
            outputOffset + HEADER_SIZE
        )
        check(encryptedWritten == encryptedLength)
        return frameLength
    }

    private fun writeHeader(
        output: ByteArray,
        offset: Int,
        flags: Byte,
        seqNum: Int,
        timestampMs: Int,
        payloadLength: Int
    ) {
        output[offset] = flags
        output[offset + 1] = ((seqNum ushr 24) and 0xff).toByte()
        output[offset + 2] = ((seqNum ushr 16) and 0xff).toByte()
        output[offset + 3] = ((seqNum ushr 8) and 0xff).toByte()
        output[offset + 4] = (seqNum and 0xff).toByte()
        output[offset + 5] = ((timestampMs ushr 24) and 0xff).toByte()
        output[offset + 6] = ((timestampMs ushr 16) and 0xff).toByte()
        output[offset + 7] = ((timestampMs ushr 8) and 0xff).toByte()
        output[offset + 8] = (timestampMs and 0xff).toByte()
        output[offset + 9] = ((payloadLength ushr 8) and 0xff).toByte()
        output[offset + 10] = (payloadLength and 0xff).toByte()
    }

    private fun requireRange(size: Int, offset: Int, length: Int, name: String) {
        require(offset >= 0 && length >= 0 && offset <= size - length) {
            "$name range is outside the array"
        }
    }
}
