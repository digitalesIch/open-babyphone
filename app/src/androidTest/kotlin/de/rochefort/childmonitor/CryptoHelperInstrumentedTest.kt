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

        val encrypted = CryptoHelper.encryptChunk(plaintext, key, counter)
        val decrypted = CryptoHelper.decryptChunk(encrypted, key, counter)

        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_WrongKey_ReturnsNull() {
        val key1 = CryptoHelper.deriveKey("test123")
        val key2 = CryptoHelper.deriveKey("test456")
        val plaintext = byteArrayOf(1, 2, 3, 4, 5)
        val counter = 0L

        val encrypted = CryptoHelper.encryptChunk(plaintext, key1, counter)
        val decrypted = CryptoHelper.decryptChunk(encrypted, key2, counter)

        assertNull(decrypted)
    }

    @Test
    fun decrypt_WrongCounter_ReturnsNull() {
        val key = CryptoHelper.deriveKey("test123")
        val plaintext = byteArrayOf(1, 2, 3, 4, 5)

        val encrypted = CryptoHelper.encryptChunk(plaintext, key, 0L)
        val decrypted = CryptoHelper.decryptChunk(encrypted, key, 1L)

        assertNull(decrypted)
    }

    @Test
    fun encryptChunk_IncreasesSize() {
        val key = CryptoHelper.deriveKey("test")
        val plaintext = ByteArray(100)
        val encrypted = CryptoHelper.encryptChunk(plaintext, key, 0L)

        assertEquals(plaintext.size + 16, encrypted.size)
    }
}
