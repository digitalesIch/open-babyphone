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

        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, sessionId, true, challenge)

        val parentInputStream = ByteArrayInputStream(outputStream.toByteArray())
        val handshake = Handshake.readHandshake(parentInputStream)

        assertNotNull(handshake)
        assertTrue(handshake!!.authRequired)
        assertArrayEquals(sessionId, handshake.sessionId)
        assertNotNull(handshake.challenge)

        val encryptedChallenge = CryptoHelper.encryptChallenge(handshake.challenge!!, key, handshake.sessionId)
        val authOutputStream = ByteArrayOutputStream()
        Handshake.writeAuthResponse(authOutputStream, encryptedChallenge)

        val childInputStream = ByteArrayInputStream(authOutputStream.toByteArray())
        val readResponse = Handshake.readAuthResponse(childInputStream)

        assertNotNull(readResponse)
        val verified = CryptoHelper.verifyChallenge(readResponse!!, challenge, key, sessionId)
        assertTrue(verified)
    }

    @Test
    fun handshake_WrongPairingCode_VerificationFails() {
        val correctCode = "test123"
        val wrongCode = "test456"
        val keyCorrect = CryptoHelper.deriveKey(correctCode)
        val keyWrong = CryptoHelper.deriveKey(wrongCode)
        val sessionId = CryptoHelper.generateSessionId()
        val challenge = CryptoHelper.generateChallenge()

        val encryptedWithWrongKey = CryptoHelper.encryptChallenge(challenge, keyWrong, sessionId)

        val verifiedWithCorrectKey = CryptoHelper.verifyChallenge(encryptedWithWrongKey, challenge, keyCorrect, sessionId)
        assertFalse(verifiedWithCorrectKey)
    }

    @Test
    fun handshake_OpenMode_NoChallengeNeeded() {
        val sessionId = CryptoHelper.generateSessionId()

        val outputStream = ByteArrayOutputStream()
        Handshake.writeHandshake(outputStream, sessionId, false, null)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val handshake = Handshake.readHandshake(inputStream)

        assertNotNull(handshake)
        assertFalse(handshake!!.authRequired)
        assertNull(handshake.challenge)
        assertArrayEquals(sessionId, handshake.sessionId)
    }
}