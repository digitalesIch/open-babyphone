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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Binary handshake protocol for Open Babyphone v1.
 *
 * Wire format:
 *   magic[4]          = "OBP1"
 *   sessionId[8]      = random per stream session
 *   authRequired[1]   = 0 (open) | 1 (pairing code required)
 *   challenge[32]     = random, only present when authRequired == 1
 *
 * Auth response (only when authRequired == 1):
 *   encryptedChallenge[48] = ChaCha20-Poly1305(challenge, key, nonce=sessionId||0xFFFFFFFF)
 *     = 32 bytes challenge + 16 bytes auth tag
 */
object Handshake {
    const val MAGIC = "OBP1"
    val MAGIC_BYTES: ByteArray = MAGIC.toByteArray(Charsets.US_ASCII)

    const val AUTH_MODE_OPEN: Byte = 0
    const val AUTH_MODE_PAIRING_CODE: Byte = 1

    const val HANDSHAKE_HEADER_SIZE = 4 + CryptoHelper.SESSION_ID_SIZE + 1
    const val AUTH_RESPONSE_SIZE = CryptoHelper.CHALLENGE_SIZE + CryptoHelper.AUTH_TAG_SIZE

    data class HandshakeMessage(
        val sessionId: ByteArray,
        val authRequired: Boolean,
        val challenge: ByteArray?
    ) {
        init {
            require(sessionId.size == CryptoHelper.SESSION_ID_SIZE) { "sessionId must be ${CryptoHelper.SESSION_ID_SIZE} bytes" }
            if (authRequired) {
                require(challenge != null && challenge.size == CryptoHelper.CHALLENGE_SIZE) {
                    "challenge must be ${CryptoHelper.CHALLENGE_SIZE} bytes when authRequired"
                }
            } else {
                require(challenge == null) { "challenge must be null when auth not required" }
            }
        }
    }

    fun writeHandshake(output: OutputStream, sessionId: ByteArray, authRequired: Boolean, challenge: ByteArray?) {
        HandshakeMessage(sessionId, authRequired, challenge)
        val size = if (authRequired) HANDSHAKE_HEADER_SIZE + CryptoHelper.CHALLENGE_SIZE else HANDSHAKE_HEADER_SIZE
        val buffer = ByteArray(size)
        var offset = 0
        System.arraycopy(MAGIC_BYTES, 0, buffer, offset, MAGIC_BYTES.size)
        offset += MAGIC_BYTES.size
        System.arraycopy(sessionId, 0, buffer, offset, CryptoHelper.SESSION_ID_SIZE)
        offset += CryptoHelper.SESSION_ID_SIZE
        buffer[offset++] = if (authRequired) AUTH_MODE_PAIRING_CODE else AUTH_MODE_OPEN
        if (authRequired && challenge != null) {
            System.arraycopy(challenge, 0, buffer, offset, CryptoHelper.CHALLENGE_SIZE)
        }
        output.write(buffer)
        output.flush()
    }

    fun readHandshake(input: InputStream): HandshakeMessage? {
        val header = ByteArray(HANDSHAKE_HEADER_SIZE)
        if (!readFully(input, header, 0, HANDSHAKE_HEADER_SIZE)) {
            return null
        }
        for (i in MAGIC_BYTES.indices) {
            if (header[i] != MAGIC_BYTES[i]) {
                return null
            }
        }
        val sessionId = header.copyOfRange(MAGIC_BYTES.size, MAGIC_BYTES.size + CryptoHelper.SESSION_ID_SIZE)
        val authRequired = header[MAGIC_BYTES.size + CryptoHelper.SESSION_ID_SIZE] == AUTH_MODE_PAIRING_CODE
        val challenge: ByteArray? = if (authRequired) {
            val challengeBuf = ByteArray(CryptoHelper.CHALLENGE_SIZE)
            if (!readFully(input, challengeBuf, 0, CryptoHelper.CHALLENGE_SIZE)) {
                return null
            }
            challengeBuf
        } else {
            null
        }
        return HandshakeMessage(sessionId, authRequired, challenge)
    }

    fun writeAuthResponse(output: OutputStream, encryptedChallenge: ByteArray) {
        require(encryptedChallenge.size == AUTH_RESPONSE_SIZE) { "encryptedChallenge must be $AUTH_RESPONSE_SIZE bytes" }
        output.write(encryptedChallenge)
        output.flush()
    }

    fun readAuthResponse(input: InputStream): ByteArray? {
        val response = ByteArray(AUTH_RESPONSE_SIZE)
        if (!readFully(input, response, 0, AUTH_RESPONSE_SIZE)) {
            return null
        }
        return response
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Boolean {
        var bytesRead = 0
        while (bytesRead < length) {
            val read = input.read(buffer, offset + bytesRead, length - bytesRead)
            if (read < 0) return false
            bytesRead += read
        }
        return true
    }
}