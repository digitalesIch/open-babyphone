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
            val flags = buffer[0]
            val seqNum = ((buffer[1].toInt() and 0xFF) shl 24) or
                ((buffer[2].toInt() and 0xFF) shl 16) or
                ((buffer[3].toInt() and 0xFF) shl 8) or
                (buffer[4].toInt() and 0xFF)
            val timestampMs = ((buffer[5].toInt() and 0xFF) shl 24) or
                ((buffer[6].toInt() and 0xFF) shl 16) or
                ((buffer[7].toInt() and 0xFF) shl 8) or
                (buffer[8].toInt() and 0xFF)
            val payloadLength = ((buffer[9].toInt() and 0xFF) shl 8) or
                (buffer[10].toInt() and 0xFF)
            return FrameHeader(flags, seqNum, timestampMs, payloadLength)
        }

        fun readFrom(input: InputStream): FrameHeader? {
            val buffer = ByteArray(SIZE)
            var bytesRead = 0
            while (bytesRead < SIZE) {
                val read = input.read(buffer, bytesRead, SIZE - bytesRead)
                if (read < 0) {
                    return null
                }
                if (read == 0) continue
                bytesRead += read
            }
            return fromByteArray(buffer)
        }

        fun writeTo(header: FrameHeader, output: OutputStream) {
            output.write(header.toByteArray())
        }
    }

    fun toByteArray(): ByteArray {
        require(seqNum >= 0) { "Sequence number must be non-negative" }
        require(payloadLength in 0..0xffff) { "Payload length is outside the wire range" }
        val buffer = ByteArray(SIZE)
        buffer[0] = flags
        buffer[1] = ((seqNum ushr 24) and 0xFF).toByte()
        buffer[2] = ((seqNum ushr 16) and 0xFF).toByte()
        buffer[3] = ((seqNum ushr 8) and 0xFF).toByte()
        buffer[4] = (seqNum and 0xFF).toByte()
        buffer[5] = ((timestampMs ushr 24) and 0xFF).toByte()
        buffer[6] = ((timestampMs ushr 16) and 0xFF).toByte()
        buffer[7] = ((timestampMs ushr 8) and 0xFF).toByte()
        buffer[8] = (timestampMs and 0xFF).toByte()
        buffer[9] = ((payloadLength ushr 8) and 0xFF).toByte()
        buffer[10] = (payloadLength and 0xFF).toByte()
        return buffer
    }
}
