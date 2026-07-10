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

class HandshakeInstrumentedTest {

    @Test
    fun handshake_PairingCodeChallengeResponse_RoundTrip() {
        val pairingCode = "test123"
        val kdfSalt = CryptoHelper.generateSalt()
        val key = CryptoHelper.deriveKey(pairingCode, kdfSalt)
        val sessionId = CryptoHelper.generateSessionId()
        val challenge = CryptoHelper.generateChallenge()
        val authNonce = CryptoHelper.generateNonce()

        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, sessionId, true, challenge, authNonce, kdfSalt)

        val parentInputStream = ByteArrayInputStream(outputStream.toByteArray())
        val handshake = Handshake.readHandshake(parentInputStream)

        assertNotNull(handshake)
        assertTrue(handshake!!.authRequired)
        assertArrayEquals(sessionId, handshake.sessionId)
        assertNotNull(handshake.challenge)
        assertNotNull(handshake.authNonce)
        assertNotNull(handshake.kdfSalt)
        assertArrayEquals(authNonce, handshake.authNonce)
        assertArrayEquals(kdfSalt, handshake.kdfSalt)
        assertEquals(Handshake.PROTOCOL_VERSION, handshake.protocolVersion)
        assertEquals(Handshake.CURRENT_CAPABILITIES, handshake.capabilities)

        val parentKey = CryptoHelper.deriveKey(pairingCode, handshake.kdfSalt!!)
        val encryptedChallenge = CryptoHelper.encryptChallenge(handshake.challenge!!, parentKey, handshake.authNonce!!)
        val authOutputStream = ByteArrayOutputStream()
        Handshake.writeAuthResponse(authOutputStream, encryptedChallenge)
        Handshake.writeCapabilityResponse(authOutputStream)

        val childInputStream = ByteArrayInputStream(authOutputStream.toByteArray())
        val readResponse = Handshake.readAuthResponse(childInputStream)
        val capResponse = Handshake.readCapabilityResponse(childInputStream)

        assertNotNull(readResponse)
        assertNotNull(capResponse)
        val verified = CryptoHelper.verifyChallenge(readResponse!!, challenge, key, authNonce)
        assertTrue(verified)
        assertEquals(Handshake.PROTOCOL_VERSION, capResponse!!.protocolVersion)
    }

    @Test
    fun handshake_WrongPairingCode_VerificationFails() {
        val kdfSalt = CryptoHelper.generateSalt()
        val keyCorrect = CryptoHelper.deriveKey("test123", kdfSalt)
        val keyWrong = CryptoHelper.deriveKey("test456", kdfSalt)
        val challenge = CryptoHelper.generateChallenge()
        val authNonce = CryptoHelper.generateNonce()

        val encryptedWithWrongKey = CryptoHelper.encryptChallenge(challenge, keyWrong, authNonce)

        val verifiedWithCorrectKey = CryptoHelper.verifyChallenge(encryptedWithWrongKey, challenge, keyCorrect, authNonce)
        assertFalse(verifiedWithCorrectKey)
    }

    @Test
    fun handshake_OpenMode_NoChallengeNeeded() {
        val sessionId = CryptoHelper.generateSessionId()

        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, sessionId, false, null, null, null)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val handshake = Handshake.readHandshake(inputStream)

        assertNotNull(handshake)
        assertFalse(handshake!!.authRequired)
        assertNull(handshake.challenge)
        assertNull(handshake.authNonce)
        assertNull(handshake.kdfSalt)
        assertArrayEquals(sessionId, handshake.sessionId)
    }

    @Test
    fun encryptChallenge_DifferentNonces_ProduceDifferentCiphertexts() {
        val kdfSalt = CryptoHelper.generateSalt()
        val key = CryptoHelper.deriveKey("testcode123", kdfSalt)
        val challenge = CryptoHelper.generateChallenge()
        val nonce1 = CryptoHelper.generateNonce()
        val nonce2 = CryptoHelper.generateNonce()

        val encrypted1 = CryptoHelper.encryptChallenge(challenge, key, nonce1)
        val encrypted2 = CryptoHelper.encryptChallenge(challenge, key, nonce2)

        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun verifyChallenge_WrongNonce_Fails() {
        val kdfSalt = CryptoHelper.generateSalt()
        val key = CryptoHelper.deriveKey("testcode123", kdfSalt)
        val challenge = CryptoHelper.generateChallenge()
        val correctNonce = CryptoHelper.generateNonce()
        val wrongNonce = CryptoHelper.generateNonce()

        val encrypted = CryptoHelper.encryptChallenge(challenge, key, correctNonce)
        val verified = CryptoHelper.verifyChallenge(encrypted, challenge, key, wrongNonce)

        assertFalse(verified)
    }

    @Test
    fun encryptChallenge_TwoHandshakesSameSession_DifferentNonces() {
        val kdfSalt = CryptoHelper.generateSalt()
        val key = CryptoHelper.deriveKey("testcode123", kdfSalt)
        val challenge1 = CryptoHelper.generateChallenge()
        val challenge2 = CryptoHelper.generateChallenge()
        val nonce1 = CryptoHelper.generateNonce()
        val nonce2 = CryptoHelper.generateNonce()

        val encrypted1 = CryptoHelper.encryptChallenge(challenge1, key, nonce1)
        val encrypted2 = CryptoHelper.encryptChallenge(challenge2, key, nonce2)

        assertFalse(nonce1.contentEquals(nonce2))
        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun capabilityResponse_FullNegotiationRoundTrip() {
        val childCaps = Handshake.CAP_G711_ULAW
        val parentCaps = Handshake.CAP_G711_ULAW

        val outputStream = ByteArrayOutputStream()
        Handshake.writeCapabilityResponse(outputStream, Handshake.PROTOCOL_VERSION, parentCaps)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val response = Handshake.readCapabilityResponse(inputStream)

        assertNotNull(response)
        val negotiated = Handshake.negotiateCapabilities(childCaps, response!!.capabilities)
        assertEquals(Handshake.CAP_G711_ULAW, negotiated)
    }

    @Test
    fun handshake_DifferentSalts_DeriveDifferentKeys() {
        val salt1 = CryptoHelper.generateSalt()
        val salt2 = CryptoHelper.generateSalt()

        val key1 = CryptoHelper.deriveKey("sameCode", salt1)
        val key2 = CryptoHelper.deriveKey("sameCode", salt2)

        assertFalse("Same pairing code with different salts must derive different keys",
            key1.contentEquals(key2))
    }
}