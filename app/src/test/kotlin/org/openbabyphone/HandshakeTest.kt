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
    private val testKdfSalt = ByteArray(16) { 0x77 }

    @Test
    fun writeAndRead_OpenMode_RoundTrip() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, testSessionId, false, null, null, null)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val message = Handshake.readHandshake(inputStream)

        assertNotNull(message)
        assertArrayEquals(testSessionId, message!!.sessionId)
        assertFalse(message.authRequired)
        assertNull(message.challenge)
        assertNull(message.authNonce)
        assertNull(message.kdfSalt)
        assertEquals(Handshake.PROTOCOL_VERSION, message.protocolVersion)
        assertEquals(Handshake.CURRENT_CAPABILITIES, message.capabilities)
    }

    @Test
    fun writeAndRead_PairingCodeMode_RoundTrip() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, testSessionId, true, testChallenge, testAuthNonce, testKdfSalt)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val message = Handshake.readHandshake(inputStream)

        assertNotNull(message)
        assertArrayEquals(testSessionId, message!!.sessionId)
        assertTrue(message.authRequired)
        assertNotNull(message.challenge)
        assertArrayEquals(testChallenge, message.challenge)
        assertNotNull(message.authNonce)
        assertArrayEquals(testAuthNonce, message.authNonce)
        assertNotNull(message.kdfSalt)
        assertArrayEquals(testKdfSalt, message.kdfSalt)
        assertEquals(Handshake.PROTOCOL_VERSION, message.protocolVersion)
        assertEquals(Handshake.CURRENT_CAPABILITIES, message.capabilities)
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
    fun readHandshake_OldMagic_ReturnsNull() {
        val oldMagic = "OBP2".toByteArray(Charsets.US_ASCII)
        val bytes = ByteArray(Handshake.HANDSHAKE_HEADER_SIZE)
        System.arraycopy(oldMagic, 0, bytes, 0, oldMagic.size)
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
        assertArrayEquals("OBP3".toByteArray(Charsets.US_ASCII), Handshake.MAGIC_BYTES)
    }

    @Test
    fun handshakeHeaderSize_Correct() {
        assertEquals(4 + 2 + 2 + 8 + 1, Handshake.HANDSHAKE_HEADER_SIZE)
    }

    @Test
    fun authResponseSize_Correct() {
        assertEquals(32 + 16, Handshake.AUTH_RESPONSE_SIZE)
    }

    @Test
    fun capabilityResponseSize_Correct() {
        assertEquals(4, Handshake.CAPABILITY_RESPONSE_SIZE)
    }

    @Test
    fun writeAndReadCapabilityResponse_RoundTrip() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeCapabilityResponse(outputStream, Handshake.PROTOCOL_VERSION, Handshake.CAP_G711_ULAW)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val response = Handshake.readCapabilityResponse(inputStream)

        assertNotNull(response)
        assertEquals(Handshake.PROTOCOL_VERSION, response!!.protocolVersion)
        assertEquals(Handshake.CAP_G711_ULAW, response.capabilities)
    }

    @Test
    fun readCapabilityResponse_PartialData_ReturnsNull() {
        val bytes = ByteArray(Handshake.CAPABILITY_RESPONSE_SIZE - 1)
        val inputStream = ByteArrayInputStream(bytes)

        val response = Handshake.readCapabilityResponse(inputStream)

        assertNull(response)
    }

    @Test
    fun isVersionSupported_CurrentVersion_ReturnsTrue() {
        assertTrue(Handshake.isVersionSupported(Handshake.PROTOCOL_VERSION))
    }

    @Test
    fun isVersionSupported_OldVersion_ReturnsFalse() {
        assertFalse(Handshake.isVersionSupported(2))
    }

    @Test
    fun isVersionSupported_FutureVersion_ReturnsFalse() {
        assertFalse(Handshake.isVersionSupported(99))
    }

    @Test
    fun negotiateCapabilities_SharedG711_ReturnsG711() {
        val result = Handshake.negotiateCapabilities(Handshake.CAP_G711_ULAW, Handshake.CAP_G711_ULAW)
        assertEquals(Handshake.CAP_G711_ULAW, result)
    }

    @Test
    fun negotiateCapabilities_ChildHasOpus_ParentHasG711_ReturnsG711() {
        val childCaps = Handshake.CAP_G711_ULAW or Handshake.CAP_OPUS
        val parentCaps = Handshake.CAP_G711_ULAW
        val result = Handshake.negotiateCapabilities(childCaps, parentCaps)
        assertEquals(Handshake.CAP_G711_ULAW, result)
    }

    @Test(expected = IllegalStateException::class)
    fun negotiateCapabilities_NoOverlap_Throws() {
        Handshake.negotiateCapabilities(Handshake.CAP_OPUS, Handshake.CAP_G711_ULAW)
    }

    @Test(expected = IllegalStateException::class)
    fun negotiateCapabilities_EmptyChild_Throws() {
        Handshake.negotiateCapabilities(0, Handshake.CAP_G711_ULAW)
    }

    @Test
    fun capabilityResponse_SupportsCodec_G711() {
        val response = Handshake.CapabilityResponse(Handshake.PROTOCOL_VERSION, Handshake.CAP_G711_ULAW)
        assertTrue(response.supportsCodec(Handshake.CAP_G711_ULAW))
        assertFalse(response.supportsCodec(Handshake.CAP_OPUS))
    }

    @Test
    fun capabilityResponse_IsCompatibleWith_SameVersionSameCaps() {
        val a = Handshake.CapabilityResponse(Handshake.PROTOCOL_VERSION, Handshake.CAP_G711_ULAW)
        val b = Handshake.CapabilityResponse(Handshake.PROTOCOL_VERSION, Handshake.CAP_G711_ULAW)
        assertTrue(a.isCompatibleWith(b))
    }

    @Test
    fun capabilityResponse_IsCompatibleWith_DifferentVersion_ReturnsFalse() {
        val a = Handshake.CapabilityResponse(3, Handshake.CAP_G711_ULAW)
        val b = Handshake.CapabilityResponse(4, Handshake.CAP_G711_ULAW)
        assertFalse(a.isCompatibleWith(b))
    }

    @Test
    fun capabilityResponse_IsCompatibleWith_NoSharedCodec_ReturnsFalse() {
        val a = Handshake.CapabilityResponse(Handshake.PROTOCOL_VERSION, Handshake.CAP_G711_ULAW)
        val b = Handshake.CapabilityResponse(Handshake.PROTOCOL_VERSION, Handshake.CAP_OPUS)
        assertFalse(a.isCompatibleWith(b))
    }

    @Test
    fun writeAndRead_FullHandshakeWithVersionAndCaps() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(
            outputStream,
            testSessionId,
            true,
            testChallenge,
            testAuthNonce,
            testKdfSalt,
            protocolVersion = 3,
            capabilities = Handshake.CAP_G711_ULAW or Handshake.CAP_OPUS
        )

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val message = Handshake.readHandshake(inputStream)

        assertNotNull(message)
        assertEquals(3, message!!.protocolVersion)
        assertEquals(Handshake.CAP_G711_ULAW or Handshake.CAP_OPUS, message.capabilities)
        assertArrayEquals(testKdfSalt, message.kdfSalt)
    }

    @Test
    fun currentCapabilities_IncludesG711() {
        assertTrue((Handshake.CURRENT_CAPABILITIES and Handshake.CAP_G711_ULAW) != 0)
    }

    @Test
    fun currentCapabilities_OpusNotInCurrentRelease() {
        assertFalse((Handshake.CURRENT_CAPABILITIES and Handshake.CAP_OPUS) != 0)
    }

    @Test
    fun writeHandshake_BigEndianVersionAndCaps() {
        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(
            outputStream,
            testSessionId,
            false,
            null,
            null,
            null,
            protocolVersion = 0x0301,
            capabilities = 0x0304
        )

        val bytes = outputStream.toByteArray()
        assertEquals(0x03.toByte(), bytes[4])
        assertEquals(0x01.toByte(), bytes[5])
        assertEquals(0x03.toByte(), bytes[6])
        assertEquals(0x04.toByte(), bytes[7])
    }
}