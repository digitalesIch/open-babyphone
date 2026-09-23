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

import java.io.InputStream
import java.io.OutputStream

data class FrameHeader(
    val flags: Byte,
    val seqNum: Int,
    val timestampMs: Int,
    val payloadLength: Int
) {
    companion object {
        const val SIZE = 11
        const val FLAG_AUDIO = 0x01.toByte()
        const val FLAG_HEARTBEAT = 0x02.toByte()

        fun fromByteArray(buffer: ByteArray): FrameHeader? {
            if (buffer.size != SIZE) return null
            return fromByteArray(buffer, 0)
        }

        fun fromByteArray(buffer: ByteArray, offset: Int): FrameHeader? {
            if (offset < 0 || offset > buffer.size - SIZE) return null
            val flags = buffer[offset]
            val seqNum = ((buffer[offset + 1].toInt() and 0xFF) shl 24) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 3].toInt() and 0xFF) shl 8) or
                (buffer[offset + 4].toInt() and 0xFF)
            val timestampMs = ((buffer[offset + 5].toInt() and 0xFF) shl 24) or
                ((buffer[offset + 6].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 7].toInt() and 0xFF) shl 8) or
                (buffer[offset + 8].toInt() and 0xFF)
            val payloadLength = ((buffer[offset + 9].toInt() and 0xFF) shl 8) or
                (buffer[offset + 10].toInt() and 0xFF)
            return FrameHeader(flags, seqNum, timestampMs, payloadLength)
        }

        fun readFrom(input: InputStream): FrameHeader? {
            val buffer = ByteArray(SIZE)
            return try {
                readInto(input, buffer)
            } finally {
                buffer.fill(0)
            }
        }

        /**
         * Reads a header into the caller-provided buffer without allocating.
         * Returns null on EOF or when [buffer] is too small.
         */
        fun readInto(input: InputStream, buffer: ByteArray): FrameHeader? {
            if (buffer.size < SIZE) return null
            var bytesRead = 0
            while (bytesRead < SIZE) {
                val read = input.read(buffer, bytesRead, SIZE - bytesRead)
                if (read < 0) {
                    return null
                }
                if (read == 0) continue
                bytesRead += read
            }
            return fromByteArray(buffer, 0)
        }

        fun writeTo(header: FrameHeader, output: OutputStream) {
            output.write(header.toByteArray())
        }
    }

    /** Serializes this header into [buffer] at [offset] without allocating. */
    fun writeTo(buffer: ByteArray, offset: Int = 0) {
        require(seqNum >= 0) { "Sequence number must be non-negative" }
        require(payloadLength in 0..0xffff) { "Payload length is outside the wire range" }
        require(offset in 0..buffer.size - SIZE) { "Header range is outside the array" }
        buffer[offset] = flags
        buffer[offset + 1] = ((seqNum ushr 24) and 0xFF).toByte()
        buffer[offset + 2] = ((seqNum ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((seqNum ushr 8) and 0xFF).toByte()
        buffer[offset + 4] = (seqNum and 0xFF).toByte()
        buffer[offset + 5] = ((timestampMs ushr 24) and 0xFF).toByte()
        buffer[offset + 6] = ((timestampMs ushr 16) and 0xFF).toByte()
        buffer[offset + 7] = ((timestampMs ushr 8) and 0xFF).toByte()
        buffer[offset + 8] = (timestampMs and 0xFF).toByte()
        buffer[offset + 9] = ((payloadLength ushr 8) and 0xFF).toByte()
        buffer[offset + 10] = (payloadLength and 0xFF).toByte()
    }

    fun toByteArray(): ByteArray {
        return ByteArray(SIZE).also { writeTo(it, 0) }
    }
}
