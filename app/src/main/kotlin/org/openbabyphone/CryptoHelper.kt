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

import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium

object CryptoHelper {

    private const val ARGON2_OPS_LIMIT = 3
    private const val ARGON2_MEM_LIMIT = 16 * 1024 * 1024
    private val ARGON2_SALT = "openbabyphone.argon2id.salt.v1".toByteArray(Charsets.UTF_8)

    const val SESSION_ID_SIZE = 8
    const val CHALLENGE_SIZE = 32
    const val AUTH_TAG_SIZE = 16
    const val NONCE_SIZE = 12

    init {
        NaCl.sodium()
    }

    fun deriveKey(pairingCode: String): ByteArray {
        val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
        val key = ByteArray(32)
        val result = Sodium.crypto_pwhash(
            key,
            key.size,
            codeBytes,
            codeBytes.size,
            ARGON2_SALT,
            ARGON2_OPS_LIMIT,
            ARGON2_MEM_LIMIT,
            Sodium.crypto_pwhash_alg_argon2i13()
        )
        if (result != 0) {
            throw IllegalStateException("crypto_pwhash failed with code $result")
        }
        return key
    }

    fun generateSessionId(): ByteArray {
        val sessionId = ByteArray(SESSION_ID_SIZE)
        Sodium.randombytes_buf(sessionId, SESSION_ID_SIZE)
        return sessionId
    }

    fun generateChallenge(): ByteArray {
        val challenge = ByteArray(CHALLENGE_SIZE)
        Sodium.randombytes_buf(challenge, CHALLENGE_SIZE)
        return challenge
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        Sodium.randombytes_buf(nonce, NONCE_SIZE)
        return nonce
    }

    fun encryptChunk(plaintext: ByteArray, key: ByteArray, sessionId: ByteArray, counter: Long): ByteArray {
        require(key.size == 32) { "key must be 32 bytes, got ${key.size}" }
        require(sessionId.size == SESSION_ID_SIZE) { "sessionId must be $SESSION_ID_SIZE bytes, got ${sessionId.size}" }
        val nonce = buildStreamNonce(sessionId, counter)
        val ciphertext = ByteArray(plaintext.size + AUTH_TAG_SIZE)
        val clen = intArrayOf(ciphertext.size)
        val result = Sodium.crypto_aead_chacha20poly1305_ietf_encrypt(
            ciphertext,
            clen,
            plaintext,
            plaintext.size,
            null,
            0,
            null,
            nonce,
            key
        )
        if (result != 0) {
            throw IllegalStateException("crypto_aead_chacha20poly1305_ietf_encrypt failed with code $result")
        }
        return ciphertext
    }

    fun decryptChunk(ciphertext: ByteArray, key: ByteArray, sessionId: ByteArray, counter: Long): ByteArray? {
        val nonce = buildStreamNonce(sessionId, counter)
        val plaintext = ByteArray(ciphertext.size - AUTH_TAG_SIZE)
        val mlen = intArrayOf(plaintext.size)
        val result = Sodium.crypto_aead_chacha20poly1305_ietf_decrypt(
            plaintext,
            mlen,
            null,
            ciphertext,
            ciphertext.size,
            null,
            0,
            nonce,
            key
        )
        return if (result == 0) plaintext else null
    }

    fun encryptChallenge(challenge: ByteArray, key: ByteArray, authNonce: ByteArray): ByteArray {
        val ciphertext = ByteArray(challenge.size + AUTH_TAG_SIZE)
        val clen = intArrayOf(ciphertext.size)
        Sodium.crypto_aead_chacha20poly1305_ietf_encrypt(
            ciphertext,
            clen,
            challenge,
            challenge.size,
            null,
            0,
            null,
            authNonce,
            key
        )
        return ciphertext
    }

    fun verifyChallenge(encryptedChallenge: ByteArray, expectedChallenge: ByteArray, key: ByteArray, authNonce: ByteArray): Boolean {
        val decrypted = ByteArray(encryptedChallenge.size - AUTH_TAG_SIZE)
        val mlen = intArrayOf(decrypted.size)
        val result = Sodium.crypto_aead_chacha20poly1305_ietf_decrypt(
            decrypted,
            mlen,
            null,
            encryptedChallenge,
            encryptedChallenge.size,
            null,
            0,
            authNonce,
            key
        )
        return result == 0 && decrypted.contentEquals(expectedChallenge)
    }

    private fun buildStreamNonce(sessionId: ByteArray, counter: Long): ByteArray {
        require(sessionId.size == SESSION_ID_SIZE) { "sessionId must be $SESSION_ID_SIZE bytes" }
        val nonce = ByteArray(NONCE_SIZE)
        System.arraycopy(sessionId, 0, nonce, 0, SESSION_ID_SIZE)
        for (i in 0 until 4) {
            nonce[11 - i] = ((counter shr (i * 8)) and 0xFF).toByte()
        }
        return nonce
    }
}