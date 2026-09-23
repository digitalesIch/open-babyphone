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
    fun createProof_VerifyProof_RoundTrip() {
        val key = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("test123", testSalt))
        val challenge = CryptoHelper.generateChallenge()
        val authNonce = CryptoHelper.generateNonce()

        val transcript = "OBP4 transcript".toByteArray()
        val encrypted = CryptoHelper.createProof(challenge, key, authNonce, transcript)
        val verified = CryptoHelper.verifyProof(encrypted, challenge, key, authNonce, transcript)

        assertTrue(verified)
    }
}
