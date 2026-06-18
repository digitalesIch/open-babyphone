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

class FrameCodecTest {

    @Test
    fun encodeFrame_WithoutEncryption() {
        val ulawData = byteArrayOf(1, 2, 3, 4, 5)
        val frame = FrameCodec.encodeFrame(ulawData, 0, 1000, null)

        assertEquals(FrameCodec.HEADER_SIZE + ulawData.size, frame.size)
        assertEquals(FrameCodec.FLAG_AUDIO, frame[0])
    }

    @Test
    fun decodeFrame_Heartbeat() {
        val frame = FrameCodec.encodeHeartbeat(5, 2000)
        val header = FrameHeader.readFrom(frame.inputStream())
        
        assertNotNull(header)
        assertEquals(FrameCodec.FLAG_HEARTBEAT, header!!.flags)
        assertEquals(5, header.seqNum)
        assertEquals(2000, header.timestampMs)
        assertEquals(0, header.payloadLength)
    }

    @Test
    fun encodeFrame_CorrectSeqNum() {
        val ulawData = ByteArray(10)
        val frame = FrameCodec.encodeFrame(ulawData, 42, 5000, null)
        val header = FrameHeader.readFrom(frame.inputStream())

        assertNotNull(header)
        assertEquals(42, header!!.seqNum)
        assertEquals(5000, header.timestampMs)
    }

    @Test
    fun decodeFrame_RoundTrip() {
        val ulawData = byteArrayOf(10, 20, 30, 40, 50)
        val frame = FrameCodec.encodeFrame(ulawData, 1, 1500, null)
        val header = FrameHeader.readFrom(frame.inputStream())
        
        assertNotNull(header)
        val payload = ByteArray(header!!.payloadLength)
        val inputStream = frame.inputStream()
        inputStream.read(ByteArray(FrameHeader.SIZE))
        inputStream.read(payload)
        
        val decodedFrame = FrameCodec.decodeFrame(header, payload, null)
        assertNotNull(decodedFrame)
        assertArrayEquals(ulawData, decodedFrame!!.ulawData)
    }

    @Test
    fun encodeHeartbeat_CorrectFormat() {
        val frame = FrameCodec.encodeHeartbeat(10, 3000)

        assertEquals(FrameCodec.HEADER_SIZE, frame.size)
        assertEquals(FrameCodec.FLAG_HEARTBEAT, frame[0])
    }
}
