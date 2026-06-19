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
package de.rochefort.childmonitor

import org.junit.Assert.*
import org.junit.Test

class CryptoHelperInstrumentedTest {

    private val testSessionId = ByteArray(8) { 0x42 }

    @Test
    fun deriveKey_SameInput_SameOutput() {
        val pairingCode = "test123"
        val key1 = CryptoHelper.deriveKey(pairingCode)
        val key2 = CryptoHelper.deriveKey(pairingCode)

        assertArrayEquals(key1, key2)
    }

    @Test
    fun deriveKey_DifferentInput_DifferentOutput() {
        val key1 = CryptoHelper.deriveKey("test123")
        val key2 = CryptoHelper.deriveKey("test456")

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun encryptDecrypt_RoundTrip() {
        val key = CryptoHelper.deriveKey("test123")
        val plaintext = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val counter = 0L

        val encrypted = CryptoHelper.encryptChunk(plaintext, key, testSessionId, counter)
        val decrypted = CryptoHelper.decryptChunk(encrypted, key, testSessionId, counter)

        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_WrongKey_ReturnsNull() {
        val key1 = CryptoHelper.deriveKey("test123")
        val key2 = CryptoHelper.deriveKey("test456")
        val plaintext = byteArrayOf(1, 2, 3, 4, 5)
        val counter = 0L

        val encrypted = CryptoHelper.encryptChunk(plaintext, key1, testSessionId, counter)
        val decrypted = CryptoHelper.decryptChunk(encrypted, key2, testSessionId, counter)

        assertNull(decrypted)
    }

    @Test
    fun decrypt_WrongCounter_ReturnsNull() {
        val key = CryptoHelper.deriveKey("test123")
        val plaintext = byteArrayOf(1, 2, 3, 4, 5)

        val encrypted = CryptoHelper.encryptChunk(plaintext, key, testSessionId, 0L)
        val decrypted = CryptoHelper.decryptChunk(encrypted, key, testSessionId, 1L)

        assertNull(decrypted)
    }

    @Test
    fun encryptChunk_IncreasesSize() {
        val key = CryptoHelper.deriveKey("test")
        val plaintext = ByteArray(100)
        val encrypted = CryptoHelper.encryptChunk(plaintext, key, testSessionId, 0L)

        assertEquals(plaintext.size + 16, encrypted.size)
    }

    @Test
    fun nonce_differentSessions_differentNonces() {
        val key = CryptoHelper.deriveKey("test123")
        val plaintext = byteArrayOf(1, 2, 3)
        val sessionId1 = CryptoHelper.generateSessionId()
        val sessionId2 = CryptoHelper.generateSessionId()

        val encrypted1 = CryptoHelper.encryptChunk(plaintext, key, sessionId1, 0L)
        val encrypted2 = CryptoHelper.encryptChunk(plaintext, key, sessionId2, 0L)

        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun nonce_differentCounters_sameSession_differentNonces() {
        val key = CryptoHelper.deriveKey("test123")
        val plaintext = byteArrayOf(1, 2, 3)
        val sessionId = CryptoHelper.generateSessionId()

        val encrypted0 = CryptoHelper.encryptChunk(plaintext, key, sessionId, 0L)
        val encrypted1 = CryptoHelper.encryptChunk(plaintext, key, sessionId, 1L)

        assertFalse(encrypted0.contentEquals(encrypted1))
    }

    @Test
    fun generateSessionId_DifferentCalls_DifferentValues() {
        val sessionId1 = CryptoHelper.generateSessionId()
        val sessionId2 = CryptoHelper.generateSessionId()

        assertEquals(8, sessionId1.size)
        assertEquals(8, sessionId2.size)
        assertFalse(sessionId1.contentEquals(sessionId2))
    }

    @Test
    fun encryptChallenge_VerifyChallenge_RoundTrip() {
        val key = CryptoHelper.deriveKey("test123")
        val sessionId = CryptoHelper.generateSessionId()
        val challenge = CryptoHelper.generateChallenge()

        val encrypted = CryptoHelper.encryptChallenge(challenge, key, sessionId)
        val verified = CryptoHelper.verifyChallenge(encrypted, challenge, key, sessionId)

        assertTrue(verified)
    }

    @Test
    fun verifyChallenge_WrongCode_ReturnsFalse() {
        val key1 = CryptoHelper.deriveKey("test123")
        val key2 = CryptoHelper.deriveKey("test456")
        val sessionId = CryptoHelper.generateSessionId()
        val challenge = CryptoHelper.generateChallenge()

        val encrypted = CryptoHelper.encryptChallenge(challenge, key1, sessionId)
        val verified = CryptoHelper.verifyChallenge(encrypted, challenge, key2, sessionId)

        assertFalse(verified)
    }

    @Test
    fun verifyChallenge_WrongSessionId_ReturnsFalse() {
        val key = CryptoHelper.deriveKey("test123")
        val sessionId1 = CryptoHelper.generateSessionId()
        val sessionId2 = CryptoHelper.generateSessionId()
        val challenge = CryptoHelper.generateChallenge()

        val encrypted = CryptoHelper.encryptChallenge(challenge, key, sessionId1)
        val verified = CryptoHelper.verifyChallenge(encrypted, challenge, key, sessionId2)

        assertFalse(verified)
    }
}
