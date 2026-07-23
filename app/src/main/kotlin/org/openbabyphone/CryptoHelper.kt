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

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_MEMORY_KIB = 16 * 1024
    private const val ARGON2_PARALLELISM = 1
    const val KEY_SIZE = 32
    private const val CHACHA20_POLY1305 = "ChaCha20/Poly1305/NoPadding"
    private const val JVM_CHACHA20_POLY1305 = "ChaCha20-Poly1305"
    const val SALT_SIZE = 16
    private val EMPTY_BYTES = ByteArray(0)
    private val secureRandom = SecureRandom()

    const val SESSION_ID_SIZE = 8
    const val CHALLENGE_SIZE = 32
    const val AUTH_TAG_SIZE = 16
    const val NONCE_SIZE = 12
    const val PROOF_SIZE = CHALLENGE_SIZE + AUTH_TAG_SIZE

    private val AUTH_KEY_LABEL = "OBP4 authentication key".toByteArray(Charsets.US_ASCII)
    private val STREAM_KEY_LABEL = "OBP4 child-to-parent stream key".toByteArray(Charsets.US_ASCII)

    fun deriveKey(pairingCode: String, salt: ByteArray): ByteArray {
        require(salt.size == SALT_SIZE) { "salt must be $SALT_SIZE bytes, got ${salt.size}" }
        val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
        val key = ByteArray(KEY_SIZE)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ARGON2_ITERATIONS)
                .withMemoryAsKB(ARGON2_MEMORY_KIB)
                .withParallelism(ARGON2_PARALLELISM)
                .withSalt(salt)
                .build()
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(codeBytes, key)
            return key
        } catch (exception: Exception) {
            key.fill(0)
            throw exception
        } finally {
            codeBytes.fill(0)
        }
    }

    fun generateSalt(): ByteArray {
        return randomBytes(SALT_SIZE)
    }

    fun generateSessionId(): ByteArray {
        return randomBytes(SESSION_ID_SIZE)
    }

    fun generateChallenge(): ByteArray {
        return randomBytes(CHALLENGE_SIZE)
    }

    fun generateNonce(): ByteArray {
        return randomBytes(NONCE_SIZE)
    }

    fun deriveAuthKey(baseKey: ByteArray): ByteArray = deriveLabeledKey(baseKey, AUTH_KEY_LABEL, EMPTY_BYTES)

    fun deriveStreamKey(baseKey: ByteArray, context: ByteArray): ByteArray =
        deriveLabeledKey(baseKey, STREAM_KEY_LABEL, MessageDigest.getInstance("SHA-256").digest(context))

    fun encryptChunk(
        plaintext: ByteArray,
        key: ByteArray,
        sessionId: ByteArray,
        counter: Long,
        associatedData: ByteArray
    ): ByteArray {
        val encrypted = ByteArray(plaintext.size + AUTH_TAG_SIZE)
        val written = encryptChunkInto(
            plaintext,
            0,
            plaintext.size,
            key,
            sessionId,
            counter,
            associatedData,
            0,
            associatedData.size,
            encrypted,
            0
        )
        check(written == encrypted.size)
        return encrypted
    }

    fun encryptChunkInto(
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
        key: ByteArray,
        sessionId: ByteArray,
        counter: Long,
        associatedData: ByteArray,
        associatedDataOffset: Int,
        associatedDataLength: Int,
        output: ByteArray,
        outputOffset: Int
    ): Int {
        requireRange(plaintext.size, plaintextOffset, plaintextLength, "plaintext")
        requireRange(associatedData.size, associatedDataOffset, associatedDataLength, "associatedData")
        requireRange(output.size, outputOffset, plaintextLength + AUTH_TAG_SIZE, "output")
        require(sessionId.size == SESSION_ID_SIZE) { "sessionId must be $SESSION_ID_SIZE bytes, got ${sessionId.size}" }
        require(counter in 0..Int.MAX_VALUE.toLong()) { "counter is outside the supported stream range" }

        val nonce = buildStreamNonce(sessionId, counter)
        return try {
            encryptAeadInto(
                plaintext,
                plaintextOffset,
                plaintextLength,
                key,
                nonce,
                associatedData,
                associatedDataOffset,
                associatedDataLength,
                output,
                outputOffset
            )
        } finally {
            nonce.fill(0)
        }
    }

    fun decryptChunk(
        ciphertext: ByteArray,
        key: ByteArray,
        sessionId: ByteArray,
        counter: Long,
        associatedData: ByteArray
    ): ByteArray? = decryptChunk(
        ciphertext,
        0,
        ciphertext.size,
        key,
        sessionId,
        counter,
        associatedData
    )

    fun decryptChunk(
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        ciphertextLength: Int,
        key: ByteArray,
        sessionId: ByteArray,
        counter: Long,
        associatedData: ByteArray
    ): ByteArray? {
        requireRange(ciphertext.size, ciphertextOffset, ciphertextLength, "ciphertext")
        require(sessionId.size == SESSION_ID_SIZE) { "sessionId must be $SESSION_ID_SIZE bytes, got ${sessionId.size}" }
        if (counter !in 0..Int.MAX_VALUE.toLong()) return null
        val nonce = buildStreamNonce(sessionId, counter)
        return try {
            decryptAead(
                ciphertext,
                ciphertextOffset,
                ciphertextLength,
                key,
                nonce,
                associatedData
            )
        } finally {
            nonce.fill(0)
        }
    }

    fun createProof(challenge: ByteArray, authKey: ByteArray, nonce: ByteArray, transcript: ByteArray): ByteArray {
        require(challenge.size == CHALLENGE_SIZE)
        return encryptAead(challenge, authKey, nonce, transcript)
    }

    fun verifyProof(
        proof: ByteArray,
        expectedChallenge: ByteArray,
        authKey: ByteArray,
        nonce: ByteArray,
        transcript: ByteArray
    ): Boolean {
        if (proof.size != PROOF_SIZE || expectedChallenge.size != CHALLENGE_SIZE) return false
        val decrypted = decryptAead(proof, authKey, nonce, transcript) ?: return false
        return decrypted.contentEquals(expectedChallenge)
    }

    private fun deriveLabeledKey(baseKey: ByteArray, label: ByteArray, context: ByteArray): ByteArray {
        require(baseKey.size == KEY_SIZE) { "baseKey must be $KEY_SIZE bytes" }
        val info = ByteArray(label.size + 1 + context.size + 1)
        label.copyInto(info)
        context.copyInto(info, label.size + 1)
        info[info.lastIndex] = 1
        return try {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(baseKey, "HmacSHA256"))
                doFinal(info)
            }
        } finally {
            info.fill(0)
        }
    }

    internal fun encryptAead(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        val encrypted = ByteArray(plaintext.size + AUTH_TAG_SIZE)
        val written = encryptAeadInto(
            plaintext,
            0,
            plaintext.size,
            key,
            nonce,
            associatedData,
            0,
            associatedData.size,
            encrypted,
            0
        )
        check(written == encrypted.size)
        return encrypted
    }

    internal fun encryptAeadInto(
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        associatedDataOffset: Int,
        associatedDataLength: Int,
        output: ByteArray,
        outputOffset: Int
    ): Int {
        requireRange(plaintext.size, plaintextOffset, plaintextLength, "plaintext")
        requireRange(associatedData.size, associatedDataOffset, associatedDataLength, "associatedData")
        requireRange(output.size, outputOffset, plaintextLength + AUTH_TAG_SIZE, "output")
        validateAeadInputs(key, nonce)
        val cipher = createAeadCipher()
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        if (associatedDataLength > 0) {
            cipher.updateAAD(associatedData, associatedDataOffset, associatedDataLength)
        }
        return cipher.doFinal(plaintext, plaintextOffset, plaintextLength, output, outputOffset)
    }

    internal fun decryptAead(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray
    ): ByteArray? = decryptAead(ciphertext, 0, ciphertext.size, key, nonce, associatedData)

    internal fun decryptAead(
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        ciphertextLength: Int,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray
    ): ByteArray? {
        requireRange(ciphertext.size, ciphertextOffset, ciphertextLength, "ciphertext")
        if (ciphertextLength < AUTH_TAG_SIZE) return null
        validateAeadInputs(key, nonce)
        val cipher = createAeadCipher()
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return try {
            cipher.doFinal(ciphertext, ciphertextOffset, ciphertextLength)
        } catch (_: GeneralSecurityException) {
            null
        }
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

    private fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also(secureRandom::nextBytes)
    }

    private fun validateAeadInputs(key: ByteArray, nonce: ByteArray) {
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes, got ${key.size}" }
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes, got ${nonce.size}" }
    }

    private fun requireRange(size: Int, offset: Int, length: Int, name: String) {
        require(offset >= 0 && length >= 0 && offset <= size - length) {
            "$name range is outside the array"
        }
    }

    private fun createAeadCipher(): Cipher {
        return try {
            Cipher.getInstance(CHACHA20_POLY1305)
        } catch (_: GeneralSecurityException) {
            // The Android transformation name includes mode and padding; the JDK names the same RFC 8439 cipher directly.
            Cipher.getInstance(JVM_CHACHA20_POLY1305)
        }
    }
}
