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
package org.openbabyphone

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HandshakeTest {

    private val testSessionId = ByteArray(8) { 0x42 }
    private val testChallenge = ByteArray(32) { 0x55 }
    private val testAuthNonce = ByteArray(12) { 0x33 }

    @Test
    fun writeAndRead_OpenMode_RoundTrip() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, testSessionId, false, null, null)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val message = Handshake.readHandshake(inputStream)

        assertNotNull(message)
        assertArrayEquals(testSessionId, message!!.sessionId)
        assertFalse(message.authRequired)
        assertNull(message.challenge)
        assertNull(message.authNonce)
    }

    @Test
    fun writeAndRead_PairingCodeMode_RoundTrip() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, testSessionId, true, testChallenge, testAuthNonce)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val message = Handshake.readHandshake(inputStream)

        assertNotNull(message)
        assertArrayEquals(testSessionId, message!!.sessionId)
        assertTrue(message.authRequired)
        assertNotNull(message.challenge)
        assertArrayEquals(testChallenge, message.challenge)
        assertNotNull(message.authNonce)
        assertArrayEquals(testAuthNonce, message.authNonce)
    }

    @Test
    fun readHandshake_InvalidMagic_ReturnsNull() {
        val bytes = ByteArray(Handshake.HANDSHAKE_HEADER_SIZE)
        bytes[0] = 0x00
        bytes[1] = 0x00
        bytes[2] = 0x00
        bytes[3] = 0x00
        val inputStream = ByteArrayInputStream(bytes)

        val message = Handshake.readHandshake(inputStream)

        assertNull(message)
    }

    @Test
    fun readHandshake_PartialHeader_ReturnsNull() {
        val bytes = ByteArray(Handshake.HANDSHAKE_HEADER_SIZE - 1)
        val inputStream = ByteArrayInputStream(bytes)

        val message = Handshake.readHandshake(inputStream)

        assertNull(message)
    }

    @Test
    fun writeAndReadAuthResponse_RoundTrip() {
        val encryptedChallenge = ByteArray(Handshake.AUTH_RESPONSE_SIZE) { 0x77 }
        val outputStream = ByteArrayOutputStream()
        Handshake.writeAuthResponse(outputStream, encryptedChallenge)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val readResponse = Handshake.readAuthResponse(inputStream)

        assertNotNull(readResponse)
        assertArrayEquals(encryptedChallenge, readResponse)
    }

    @Test
    fun readAuthResponse_PartialData_ReturnsNull() {
        val bytes = ByteArray(Handshake.AUTH_RESPONSE_SIZE - 1)
        val inputStream = ByteArrayInputStream(bytes)

        val response = Handshake.readAuthResponse(inputStream)

        assertNull(response)
    }

    @Test
    fun magic_BytesCorrect() {
        assertArrayEquals("OBP1".toByteArray(Charsets.US_ASCII), Handshake.MAGIC_BYTES)
    }

    @Test
    fun handshakeHeaderSize_Correct() {
        assertEquals(4 + 8 + 1, Handshake.HANDSHAKE_HEADER_SIZE)
    }

    @Test
    fun authResponseSize_Correct() {
        assertEquals(32 + 16, Handshake.AUTH_RESPONSE_SIZE)
    }
}