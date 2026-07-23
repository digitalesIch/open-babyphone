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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProtectedCredential(
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

interface TrustedCredentialCrypto {
    fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential
    fun decrypt(protectedCredential: ProtectedCredential, aad: ByteArray): ByteArray
}

class CredentialCryptoUnavailableException(cause: Throwable? = null) : Exception(cause)
class CredentialCryptoInvalidException(cause: Throwable? = null) : Exception(cause)

internal class AesGcmTrustedCredentialCrypto(
    private val keyProvider: () -> SecretKey,
    private val secureRandom: SecureRandom = SecureRandom()
) : TrustedCredentialCrypto {
    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential {
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        cipher.updateAAD(aad)
        return ProtectedCredential(nonce, cipher.doFinal(plaintext))
    }

    override fun decrypt(protectedCredential: ProtectedCredential, aad: ByteArray): ByteArray {
        require(protectedCredential.nonce.size == NONCE_SIZE_BYTES) { "Invalid credential nonce" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_SIZE_BITS, protectedCredential.nonce)
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(protectedCredential.ciphertext)
    }

    companion object {
        const val NONCE_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class AndroidKeystoreTrustedCredentialCrypto(
    keyAlias: String = KEY_ALIAS
) : TrustedCredentialCrypto {
    private val alias = keyAlias

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val nonce = cipher.iv
        check(nonce.size == AesGcmTrustedCredentialCrypto.NONCE_SIZE_BYTES) {
            "Android Keystore returned an invalid GCM nonce"
        }
        cipher.updateAAD(aad)
        ProtectedCredential(nonce.copyOf(), cipher.doFinal(plaintext))
    } catch (exception: GeneralSecurityException) {
        throw CredentialCryptoUnavailableException(exception)
    }

    override fun decrypt(protectedCredential: ProtectedCredential, aad: ByteArray): ByteArray {
        require(protectedCredential.nonce.size == AesGcmTrustedCredentialCrypto.NONCE_SIZE_BYTES) {
            "Invalid credential nonce"
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey(alias) ?: throw CredentialCryptoInvalidException(),
                GCMParameterSpec(TAG_SIZE_BITS, protectedCredential.nonce)
            )
            cipher.updateAAD(aad)
            cipher.doFinal(protectedCredential.ciphertext)
        } catch (exception: javax.crypto.AEADBadTagException) {
            throw exception
        } catch (exception: CredentialCryptoInvalidException) {
            throw exception
        } catch (exception: GeneralSecurityException) {
            throw CredentialCryptoUnavailableException(exception)
        }
    }

    companion object {
        internal const val KEY_ALIAS = "open_babyphone_trusted_child_credentials_v1"
        private const val TAG_SIZE_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val KEY_CREATION_LOCK = Any()

        private fun getOrCreateKey(alias: String): SecretKey = synchronized(KEY_CREATION_LOCK) {
            val keyStore = try {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            } catch (exception: Exception) {
                throw CredentialCryptoUnavailableException(exception)
            }
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return@synchronized it }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }

        private fun getExistingKey(alias: String): SecretKey? = synchronized(KEY_CREATION_LOCK) {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.getKey(alias, null) as? SecretKey
            } catch (exception: Exception) {
                throw CredentialCryptoUnavailableException(exception)
            }
        }
    }
}
