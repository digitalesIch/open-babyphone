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

class CryptoHelperInstrumentedTest {

    private val testSessionId = ByteArray(8) { 0x42 }
    private val testSalt = ByteArray(CryptoHelper.SALT_SIZE) { 0x66 }

    @Test
    fun deriveKey_SameInputSameSalt_SameOutput() {
        val pairingCode = "test123"
        val key1 = CryptoHelper.deriveKey(pairingCode, testSalt)
        val key2 = CryptoHelper.deriveKey(pairingCode, testSalt)

        assertArrayEquals(key1, key2)
    }

    @Test
    fun deriveKey_DifferentInput_DifferentOutput() {
        val key1 = CryptoHelper.deriveKey("test123", testSalt)
        val key2 = CryptoHelper.deriveKey("test456", testSalt)

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun deriveKey_DifferentSalt_DifferentOutput() {
        val salt1 = CryptoHelper.generateSalt()
        val salt2 = CryptoHelper.generateSalt()

        val key1 = CryptoHelper.deriveKey("test123", salt1)
        val key2 = CryptoHelper.deriveKey("test123", salt2)

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun generateSalt_DifferentCalls_DifferentValues() {
        val salt1 = CryptoHelper.generateSalt()
        val salt2 = CryptoHelper.generateSalt()

        assertEquals(CryptoHelper.SALT_SIZE, salt1.size)
        assertEquals(CryptoHelper.SALT_SIZE, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun encryptDecrypt_RoundTrip() {
        val key = CryptoHelper.deriveKey("test123", testSalt)
        val plaintext = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val counter = 0L

        val encrypted = CryptoHelper.encryptChunk(plaintext, key, testSessionId, counter, byteArrayOf())
        val decrypted = CryptoHelper.decryptChunk(encrypted, key, testSessionId, counter, byteArrayOf())

        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_WrongKey_ReturnsNull() {
        val key1 = CryptoHelper.deriveKey("test123", testSalt)
        val key2 = CryptoHelper.deriveKey("test456", testSalt)
        val plaintext = byteArrayOf(1, 2, 3, 4, 5)
        val counter = 0L

        val encrypted = CryptoHelper.encryptChunk(plaintext, key1, testSessionId, counter, byteArrayOf())
        val decrypted = CryptoHelper.decryptChunk(encrypted, key2, testSessionId, counter, byteArrayOf())

        assertNull(decrypted)
    }

    @Test
    fun decrypt_WrongCounter_ReturnsNull() {
        val key = CryptoHelper.deriveKey("test123", testSalt)
        val plaintext = byteArrayOf(1, 2, 3, 4, 5)

        val encrypted = CryptoHelper.encryptChunk(plaintext, key, testSessionId, 0L, byteArrayOf())
        val decrypted = CryptoHelper.decryptChunk(encrypted, key, testSessionId, 1L, byteArrayOf())

        assertNull(decrypted)
    }

    @Test
    fun encryptChunk_IncreasesSize() {
        val key = CryptoHelper.deriveKey("test", testSalt)
        val plaintext = ByteArray(100)
        val encrypted = CryptoHelper.encryptChunk(plaintext, key, testSessionId, 0L, byteArrayOf())

        assertEquals(plaintext.size + 16, encrypted.size)
    }

    @Test
    fun nonce_differentSessions_differentNonces() {
        val key = CryptoHelper.deriveKey("test123", testSalt)
        val plaintext = byteArrayOf(1, 2, 3)
        val sessionId1 = CryptoHelper.generateSessionId()
        val sessionId2 = CryptoHelper.generateSessionId()

        val encrypted1 = CryptoHelper.encryptChunk(plaintext, key, sessionId1, 0L, byteArrayOf())
        val encrypted2 = CryptoHelper.encryptChunk(plaintext, key, sessionId2, 0L, byteArrayOf())

        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun nonce_differentCounters_sameSession_differentNonces() {
        val key = CryptoHelper.deriveKey("test123", testSalt)
        val plaintext = byteArrayOf(1, 2, 3)
        val sessionId = CryptoHelper.generateSessionId()

        val encrypted0 = CryptoHelper.encryptChunk(plaintext, key, sessionId, 0L, byteArrayOf())
        val encrypted1 = CryptoHelper.encryptChunk(plaintext, key, sessionId, 1L, byteArrayOf())

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
    fun createProof_VerifyProof_RoundTrip() {
        val key = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("test123", testSalt))
        val challenge = CryptoHelper.generateChallenge()
        val authNonce = CryptoHelper.generateNonce()

        val transcript = "OBP4 transcript".toByteArray()
        val encrypted = CryptoHelper.createProof(challenge, key, authNonce, transcript)
        val verified = CryptoHelper.verifyProof(encrypted, challenge, key, authNonce, transcript)

        assertTrue(verified)
    }

    @Test
    fun verifyProof_WrongCode_ReturnsFalse() {
        val key1 = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("test123", testSalt))
        val key2 = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("test456", testSalt))
        val challenge = CryptoHelper.generateChallenge()
        val authNonce = CryptoHelper.generateNonce()

        val encrypted = CryptoHelper.createProof(challenge, key1, authNonce, byteArrayOf())
        val verified = CryptoHelper.verifyProof(encrypted, challenge, key2, authNonce, byteArrayOf())

        assertFalse(verified)
    }

    @Test
    fun verifyProof_WrongNonce_ReturnsFalse() {
        val key = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("test123", testSalt))
        val challenge = CryptoHelper.generateChallenge()
        val nonce1 = CryptoHelper.generateNonce()
        val nonce2 = CryptoHelper.generateNonce()

        val encrypted = CryptoHelper.createProof(challenge, key, nonce1, byteArrayOf())
        val verified = CryptoHelper.verifyProof(encrypted, challenge, key, nonce2, byteArrayOf())

        assertFalse(verified)
    }
}
