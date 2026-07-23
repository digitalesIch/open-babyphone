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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class TrustedCredentialCryptoTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val crypto = AesGcmTrustedCredentialCrypto({ key })

    @Test
    fun `encrypt uses twelve byte nonce and decrypts with matching aad`() {
        val plaintext = "code1234".toByteArray()
        val aad = ProtectedTrustedCredentialStore.aad("child1", "pair1")

        val protected = crypto.encrypt(plaintext, aad)

        assertEquals(12, protected.nonce.size)
        assertFalse(plaintext.contentEquals(protected.ciphertext))
        assertArrayEquals(plaintext, crypto.decrypt(protected, aad))
    }

    @Test(expected = AEADBadTagException::class)
    fun `aad mismatch fails authentication`() {
        val protected = crypto.encrypt(
            "code1234".toByteArray(),
            ProtectedTrustedCredentialStore.aad("child1", "pair1")
        )

        crypto.decrypt(protected, ProtectedTrustedCredentialStore.aad("child1", "pair2"))
    }

    @Test(expected = AEADBadTagException::class)
    fun `ciphertext corruption fails authentication`() {
        val aad = ProtectedTrustedCredentialStore.aad("child1", "pair1")
        val protected = crypto.encrypt("code1234".toByteArray(), aad)
        protected.ciphertext[0] = (protected.ciphertext[0].toInt() xor 1).toByte()

        crypto.decrypt(protected, aad)
    }

    @Test(expected = IllegalStateException::class)
    fun `unavailable key fails closed`() {
        AesGcmTrustedCredentialCrypto(keyProvider = { error("Key unavailable") })
            .encrypt("code1234".toByteArray(), byteArrayOf(1))
    }
}
