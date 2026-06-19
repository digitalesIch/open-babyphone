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

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FrameHeaderTest {

    @Test
    fun readFrom_WritesCorrectly() {
        val header = FrameHeader(0x01.toByte(), 42, 1000, 100)
        val outputStream = ByteArrayOutputStream()
        FrameHeader.writeTo(header, outputStream)
        
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val readHeader = FrameHeader.readFrom(inputStream)

        assertNotNull(readHeader)
        assertEquals(header.flags, readHeader!!.flags)
        assertEquals(header.seqNum, readHeader.seqNum)
        assertEquals(header.timestampMs, readHeader.timestampMs)
        assertEquals(header.payloadLength, readHeader.payloadLength)
    }

    @Test
    fun writeTo_BigEndian() {
        val header = FrameHeader(0x01.toByte(), 0x12345678, 0x12345678, 256)
        val outputStream = ByteArrayOutputStream()
        FrameHeader.writeTo(header, outputStream)
        val bytes = outputStream.toByteArray()

        assertEquals(FrameHeader.SIZE, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0x12.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x56.toByte(), bytes[3])
        assertEquals(0x78.toByte(), bytes[4])
        assertEquals(0x12.toByte(), bytes[5])
        assertEquals(0x34.toByte(), bytes[6])
        assertEquals(0x56.toByte(), bytes[7])
        assertEquals(0x78.toByte(), bytes[8])
        assertEquals(0x01.toByte(), bytes[9])
        assertEquals(0x00.toByte(), bytes[10])
    }

    @Test
    fun readFrom_InsufficientBytes_ReturnsNull() {
        val bytes = ByteArray(FrameHeader.SIZE - 1)
        val inputStream = ByteArrayInputStream(bytes)
        val header = FrameHeader.readFrom(inputStream)

        assertNull(header)
    }

    @Test
    fun readFrom_ZeroValues() {
        val header = FrameHeader(0x00.toByte(), 0, 0, 0)
        val outputStream = ByteArrayOutputStream()
        FrameHeader.writeTo(header, outputStream)
        
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val readHeader = FrameHeader.readFrom(inputStream)

        assertNotNull(readHeader)
        assertEquals(0, readHeader!!.seqNum)
        assertEquals(0, readHeader.timestampMs)
        assertEquals(0, readHeader.payloadLength)
    }

    @Test
    fun readFrom_MaxValues() {
        val header = FrameHeader(0xFF.toByte(), Int.MAX_VALUE, Int.MAX_VALUE, 65535)
        val outputStream = ByteArrayOutputStream()
        FrameHeader.writeTo(header, outputStream)
        
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val readHeader = FrameHeader.readFrom(inputStream)

        assertNotNull(readHeader)
        assertEquals(Int.MAX_VALUE, readHeader!!.seqNum)
        assertEquals(Int.MAX_VALUE, readHeader.timestampMs)
        assertEquals(65535, readHeader.payloadLength)
    }
}
