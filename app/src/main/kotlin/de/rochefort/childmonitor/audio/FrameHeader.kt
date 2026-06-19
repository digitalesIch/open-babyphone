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
package de.rochefort.childmonitor.audio

import java.io.IOException
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

        fun readFrom(input: InputStream): FrameHeader? {
            val buffer = ByteArray(SIZE)
            var bytesRead = 0
            while (bytesRead < SIZE) {
                val read = input.read(buffer, bytesRead, SIZE - bytesRead)
                if (read < 0) {
                    return null
                }
                bytesRead += read
            }
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

        fun writeTo(header: FrameHeader, output: OutputStream) {
            val buffer = ByteArray(SIZE)
            buffer[0] = header.flags
            buffer[1] = ((header.seqNum ushr 24) and 0xFF).toByte()
            buffer[2] = ((header.seqNum ushr 16) and 0xFF).toByte()
            buffer[3] = ((header.seqNum ushr 8) and 0xFF).toByte()
            buffer[4] = (header.seqNum and 0xFF).toByte()
            buffer[5] = ((header.timestampMs ushr 24) and 0xFF).toByte()
            buffer[6] = ((header.timestampMs ushr 16) and 0xFF).toByte()
            buffer[7] = ((header.timestampMs ushr 8) and 0xFF).toByte()
            buffer[8] = (header.timestampMs and 0xFF).toByte()
            buffer[9] = ((header.payloadLength ushr 8) and 0xFF).toByte()
            buffer[10] = (header.payloadLength and 0xFF).toByte()
            output.write(buffer)
        }
    }
}
