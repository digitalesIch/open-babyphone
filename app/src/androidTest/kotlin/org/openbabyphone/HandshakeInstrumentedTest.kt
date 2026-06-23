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
        val key = CryptoHelper.deriveKey(pairingCode)
        val sessionId = CryptoHelper.generateSessionId()
        val challenge = CryptoHelper.generateChallenge()
        val authNonce = CryptoHelper.generateNonce()

        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, sessionId, true, challenge, authNonce)

        val parentInputStream = ByteArrayInputStream(outputStream.toByteArray())
        val handshake = Handshake.readHandshake(parentInputStream)

        assertNotNull(handshake)
        assertTrue(handshake!!.authRequired)
        assertArrayEquals(sessionId, handshake.sessionId)
        assertNotNull(handshake.challenge)
        assertNotNull(handshake.authNonce)
        assertArrayEquals(authNonce, handshake.authNonce)

        val encryptedChallenge = CryptoHelper.encryptChallenge(handshake.challenge!!, key, handshake.authNonce!!)
        val authOutputStream = ByteArrayOutputStream()
        Handshake.writeAuthResponse(authOutputStream, encryptedChallenge)

        val childInputStream = ByteArrayInputStream(authOutputStream.toByteArray())
        val readResponse = Handshake.readAuthResponse(childInputStream)

        assertNotNull(readResponse)
        val verified = CryptoHelper.verifyChallenge(readResponse!!, challenge, key, authNonce)
        assertTrue(verified)
    }

    @Test
    fun handshake_WrongPairingCode_VerificationFails() {
        val correctCode = "test123"
        val wrongCode = "test456"
        val keyCorrect = CryptoHelper.deriveKey(correctCode)
        val keyWrong = CryptoHelper.deriveKey(wrongCode)
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
        Handshake.writeHandshake(outputStream, sessionId, false, null, null)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val handshake = Handshake.readHandshake(inputStream)

        assertNotNull(handshake)
        assertFalse(handshake!!.authRequired)
        assertNull(handshake.challenge)
        assertNull(handshake.authNonce)
        assertArrayEquals(sessionId, handshake.sessionId)
    }

    @Test
    fun encryptChallenge_DifferentNonces_ProduceDifferentCiphertexts() {
        val key = CryptoHelper.deriveKey("testcode123")
        val challenge = CryptoHelper.generateChallenge()
        val nonce1 = CryptoHelper.generateNonce()
        val nonce2 = CryptoHelper.generateNonce()

        val encrypted1 = CryptoHelper.encryptChallenge(challenge, key, nonce1)
        val encrypted2 = CryptoHelper.encryptChallenge(challenge, key, nonce2)

        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun verifyChallenge_WrongNonce_Fails() {
        val key = CryptoHelper.deriveKey("testcode123")
        val challenge = CryptoHelper.generateChallenge()
        val correctNonce = CryptoHelper.generateNonce()
        val wrongNonce = CryptoHelper.generateNonce()

        val encrypted = CryptoHelper.encryptChallenge(challenge, key, correctNonce)
        val verified = CryptoHelper.verifyChallenge(encrypted, challenge, key, wrongNonce)

        assertFalse(verified)
    }

    @Test
    fun encryptChallenge_TwoHandshakesSameSession_DifferentNonces() {
        val key = CryptoHelper.deriveKey("testcode123")
        val challenge1 = CryptoHelper.generateChallenge()
        val challenge2 = CryptoHelper.generateChallenge()
        val nonce1 = CryptoHelper.generateNonce()
        val nonce2 = CryptoHelper.generateNonce()

        val encrypted1 = CryptoHelper.encryptChallenge(challenge1, key, nonce1)
        val encrypted2 = CryptoHelper.encryptChallenge(challenge2, key, nonce2)

        assertFalse(nonce1.contentEquals(nonce2))
        assertFalse(encrypted1.contentEquals(encrypted2))
    }
}