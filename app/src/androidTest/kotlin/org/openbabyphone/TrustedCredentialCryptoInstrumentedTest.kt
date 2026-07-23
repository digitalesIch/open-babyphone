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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TrustedCredentialCryptoInstrumentedTest {
    @Test
    fun androidKeystoreEncryptsAndAuthenticatesAad() {
        val alias = "obp-test-${UUID.randomUUID()}"
        try {
            val crypto = AndroidKeystoreTrustedCredentialCrypto(alias)
            val plaintext = "code1234".toByteArray()
            val aad = ProtectedTrustedCredentialStore.aad("child1", "pair1")

            val protected = crypto.encrypt(plaintext, aad)
            val second = crypto.encrypt(plaintext, aad)

            assertEquals(12, protected.nonce.size)
            assertFalse(protected.nonce.contentEquals(second.nonce))
            assertArrayEquals(plaintext, crypto.decrypt(protected, aad))
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(alias)
            }
        }
    }

    @Test
    fun concurrentFirstUseKeepsOneDecryptableAliasKey() {
        val alias = "obp-test-${UUID.randomUUID()}"
        try {
            val aad = ProtectedTrustedCredentialStore.aad("child1", "pair1")
            val encrypted = java.util.Collections.synchronizedList(mutableListOf<ProtectedCredential>())
            val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
            val threads = List(8) {
                Thread {
                    try {
                        encrypted += AndroidKeystoreTrustedCredentialCrypto(alias)
                            .encrypt("code1234".toByteArray(), aad)
                    } catch (failure: Throwable) {
                        failures += failure
                    }
                }
            }
            threads.forEach(Thread::start)
            threads.forEach(Thread::join)

            assertTrue(failures.isEmpty())
            val crypto = AndroidKeystoreTrustedCredentialCrypto(alias)
            encrypted.forEach { protected ->
                assertArrayEquals("code1234".toByteArray(), crypto.decrypt(protected, aad))
                assertEquals(12, protected.nonce.size)
            }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(alias)
            }
        }
    }
}
