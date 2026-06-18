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

import de.rochefort.childmonitor.CryptoHelper
import org.junit.Assert.*
import org.junit.Test

class FrameCodecCryptoInstrumentedTest {

    @Test
    fun encodeFrame_WithEncryption() {
        val key = CryptoHelper.deriveKey("test123")
        val ulawData = byteArrayOf(1, 2, 3, 4, 5)
        val frame = FrameCodec.encodeFrame(ulawData, 0, 1000, key)

        assertEquals(FrameCodec.HEADER_SIZE + ulawData.size + 16, frame.size)
        assertEquals(FrameCodec.FLAG_AUDIO, frame[0])
    }

    @Test
    fun decodeFrame_WithEncryption_RoundTrip() {
        val key = CryptoHelper.deriveKey("test123")
        val ulawData = byteArrayOf(10, 20, 30, 40, 50)
        val frame = FrameCodec.encodeFrame(ulawData, 7, 1500, key)
        val inputStream = frame.inputStream()
        val header = FrameHeader.readFrom(inputStream)

        assertNotNull(header)
        val payload = ByteArray(header!!.payloadLength)
        inputStream.read(payload)

        val decodedFrame = FrameCodec.decodeFrame(header, payload, key)

        assertNotNull(decodedFrame)
        assertFalse(decodedFrame!!.isHeartbeat)
        assertEquals(7, decodedFrame.seqNum)
        assertEquals(1500, decodedFrame.timestampMs)
        assertArrayEquals(ulawData, decodedFrame.ulawData)
    }
}
