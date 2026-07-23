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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {
    private val sessionId = ByteArray(CryptoHelper.SESSION_ID_SIZE) { 0x42 }
    private val key = ByteArray(CryptoHelper.KEY_SIZE) { it.toByte() }

    @Test
    fun `audio frame round trips with authenticated complete header`() {
        val audio = byteArrayOf(10, 20, 30, 40, 50)
        val encoded = FrameCodec.encodeFrame(audio, 7, 1500, key, sessionId)
        val input = encoded.inputStream()
        val header = FrameHeader.readFrom(input)!!
        val payload = ByteArray(header.payloadLength).also { input.read(it) }

        assertEquals(FrameCodec.FLAG_AUDIO, header.flags)
        assertEquals(audio.size + CryptoHelper.AUTH_TAG_SIZE, header.payloadLength)
        assertArrayEquals(audio, FrameCodec.decodeFrame(header, payload, key, sessionId)!!.ulawData)

        val tamperedTimestamp = header.copy(timestampMs = header.timestampMs + 1)
        assertNull(FrameCodec.decodeFrame(tamperedTimestamp, payload, key, sessionId))
        val tamperedFlags = header.copy(flags = FrameCodec.FLAG_HEARTBEAT)
        assertNull(FrameCodec.decodeFrame(tamperedFlags, payload, key, sessionId))
        val tamperedLength = header.copy(payloadLength = header.payloadLength + 1)
        assertNull(FrameCodec.decodeFrame(tamperedLength, payload, key, sessionId))
    }

    @Test
    fun `heartbeat is an authenticated empty encrypted payload`() {
        val encoded = FrameCodec.encodeHeartbeat(5, 2000, key, sessionId)
        val input = encoded.inputStream()
        val header = FrameHeader.readFrom(input)!!
        val payload = ByteArray(header.payloadLength).also { input.read(it) }
        val decoded = FrameCodec.decodeFrame(header, payload, key, sessionId)

        assertEquals(FrameCodec.HEADER_SIZE + CryptoHelper.AUTH_TAG_SIZE, encoded.size)
        assertEquals(CryptoHelper.AUTH_TAG_SIZE, header.payloadLength)
        assertTrue(decoded!!.isHeartbeat)
        assertTrue(decoded.ulawData.isEmpty())

        payload[0] = (payload[0].toInt() xor 1).toByte()
        assertNull(FrameCodec.decodeFrame(header, payload, key, sessionId))
    }

    @Test
    fun `unknown flags malformed heartbeat and short tags are rejected before decryption`() {
        assertFalse(FrameCodec.isValidHeader(FrameHeader(0x03, 0, 0, CryptoHelper.AUTH_TAG_SIZE)))
        assertFalse(FrameCodec.isValidHeader(FrameHeader(FrameCodec.FLAG_HEARTBEAT, 0, 0, 0)))
        assertFalse(FrameCodec.isValidHeader(FrameHeader(FrameCodec.FLAG_HEARTBEAT, 0, 0, CryptoHelper.AUTH_TAG_SIZE + 1)))
        assertFalse(FrameCodec.isValidHeader(FrameHeader(FrameCodec.FLAG_AUDIO, 0, 0, CryptoHelper.AUTH_TAG_SIZE)))
        assertFalse(FrameCodec.isValidHeader(FrameHeader(FrameCodec.FLAG_AUDIO, 0, 0, FrameCodec.MAX_ENCRYPTED_AUDIO_SIZE + 1)))
        assertFalse(FrameCodec.isValidHeader(FrameHeader(FrameCodec.FLAG_AUDIO, -1, 0, CryptoHelper.AUTH_TAG_SIZE + 1)))
    }

    @Test
    fun `wrong stream key and session reject frame`() {
        val encoded = FrameCodec.encodeFrame(byteArrayOf(1), 0, 0, key, sessionId)
        val input = encoded.inputStream()
        val header = FrameHeader.readFrom(input)!!
        val payload = ByteArray(header.payloadLength).also { input.read(it) }

        assertNull(FrameCodec.decodeFrame(header, payload, key.copyOf().also { it[0] = 1 }, sessionId))
        assertNull(FrameCodec.decodeFrame(header, payload, key, sessionId.copyOf().also { it[0] = 1 }))
    }

    @Test
    fun `sequence requires exact first frame then permits authenticated forward gap`() {
        val sequence = FrameSequence(12)
        assertEquals(FrameSequenceDecision.InvalidFirst, sequence.classify(13))
        assertEquals(FrameSequenceDecision.Exact, sequence.acceptAuthenticated(12))

        assertEquals(FrameSequenceDecision.Replay, sequence.classify(12))
        assertEquals(FrameSequenceDecision.ForwardGap(2), sequence.classify(15))
        assertEquals(FrameSequenceDecision.ForwardGap(2), sequence.acceptAuthenticated(15))
        assertEquals(FrameSequenceDecision.Replay, sequence.classify(14))
        assertEquals(FrameSequenceDecision.Exact, sequence.classify(16))
    }

    @Test
    fun `encoded frame has fixed bounded wire size`() {
        val encoded = FrameCodec.encodeFrame(byteArrayOf(1, 2, 3), 0x01020304, 0x05060708, key, sessionId)
        val header = FrameHeader.readFrom(encoded.inputStream())

        assertNotNull(header)
        assertEquals(FrameCodec.HEADER_SIZE + 3 + CryptoHelper.AUTH_TAG_SIZE, encoded.size)
        assertArrayEquals(
            byteArrayOf(1, 1, 2, 3, 4, 5, 6, 7, 8, 0, 19),
            encoded.copyOfRange(0, FrameCodec.HEADER_SIZE)
        )
    }

    @Test
    fun `output buffer audio serialization exactly matches allocating API`() {
        val source = byteArrayOf(99, 10, 20, 30, 40, 88)
        val expected = FrameCodec.encodeFrame(source.copyOfRange(1, 5), 7, 1500, key, sessionId)
        val output = ByteArray(expected.size + 6) { 0x55 }

        val written = FrameCodec.encodeFrameInto(
            source,
            1,
            4,
            7,
            1500,
            key,
            sessionId,
            output,
            3
        )

        assertEquals(expected.size, written)
        assertArrayEquals(expected, output.copyOfRange(3, 3 + written))
        assertArrayEquals(ByteArray(3) { 0x55 }, output.copyOfRange(0, 3))
        assertArrayEquals(ByteArray(3) { 0x55 }, output.copyOfRange(3 + written, output.size))
    }

    @Test
    fun `output buffer heartbeat serialization exactly matches allocating API`() {
        val expected = FrameCodec.encodeHeartbeat(5, 2000, key, sessionId)
        val output = ByteArray(expected.size + 2)

        val written = FrameCodec.encodeHeartbeatInto(5, 2000, key, sessionId, output, 1)

        assertEquals(expected.size, written)
        assertArrayEquals(expected, output.copyOfRange(1, 1 + written))
    }

    @Test
    fun `decode accepts authenticated payload from bounded scratch range`() {
        val encoded = FrameCodec.encodeFrame(byteArrayOf(10, 20, 30), 7, 1500, key, sessionId)
        val header = FrameHeader.fromByteArray(encoded.copyOfRange(0, FrameCodec.HEADER_SIZE))!!
        val scratch = ByteArray(header.payloadLength + 4)
        encoded.copyInto(scratch, 2, FrameCodec.HEADER_SIZE)

        val decoded = FrameCodec.decodeFrame(
            header,
            scratch,
            2,
            header.payloadLength,
            key,
            sessionId
        )

        assertArrayEquals(byteArrayOf(10, 20, 30), decoded!!.ulawData)
    }

    @Test
    fun `output buffer APIs reject undersized destination`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.encodeFrameInto(
                byteArrayOf(1, 2, 3),
                0,
                3,
                0,
                0,
                key,
                sessionId,
                ByteArray(FrameCodec.HEADER_SIZE + 3 + FrameCodec.AUTH_TAG_SIZE - 1)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.encodeHeartbeatInto(
                0,
                0,
                key,
                sessionId,
                ByteArray(FrameCodec.HEADER_SIZE + FrameCodec.AUTH_TAG_SIZE - 1)
            )
        }
    }
}
