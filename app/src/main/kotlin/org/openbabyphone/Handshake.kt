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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Binary handshake protocol for Open Babyphone.
 *
 * Wire format (child → parent):
 *   magic[4]              = "OBP2"
 *   protocolVersion[2]    = big-endian protocol version (currently 2)
 *   capabilities[2]       = big-endian capability bitmap
 *   sessionId[8]          = random per stream session
 *   authRequired[1]      = 0 (open) | 1 (pairing code required)
 *   challenge[32]         = random, only present when authRequired == 1
 *   authNonce[12]         = random per-handshake nonce, only present when authRequired == 1
 *
 * Parent capability response (parent → child, after auth response if any):
 *   protocolVersion[2]    = parent protocol version
 *   capabilities[2]       = parent capability bitmap
 *
 * Auth response (only when authRequired == 1, sent before the capability response):
 *   encryptedChallenge[48] = ChaCha20-Poly1305(challenge, key, nonce=authNonce)
 *     = 32 bytes challenge + 16 bytes auth tag
 *
 * Capability bits:
 *   bit 0: G.711 u-law codec (8000 Hz)
 *   bit 1: Opus codec (reserved for future migration, see #69)
 */
object Handshake {
    const val MAGIC = "OBP2"
    val MAGIC_BYTES: ByteArray = MAGIC.toByteArray(Charsets.US_ASCII)

    const val PROTOCOL_VERSION: Int = 2

    const val CAP_G711_ULAW: Int = 1 shl 0
    const val CAP_OPUS: Int = 1 shl 1

    const val CURRENT_CAPABILITIES: Int = CAP_G711_ULAW

    const val AUTH_MODE_OPEN: Byte = 0
    const val AUTH_MODE_PAIRING_CODE: Byte = 1

    const val MAGIC_SIZE = 4
    const val VERSION_SIZE = 2
    const val CAPABILITIES_SIZE = 2
    const val HANDSHAKE_HEADER_SIZE = MAGIC_SIZE + VERSION_SIZE + CAPABILITIES_SIZE + CryptoHelper.SESSION_ID_SIZE + 1
    const val AUTH_RESPONSE_SIZE = CryptoHelper.CHALLENGE_SIZE + CryptoHelper.AUTH_TAG_SIZE
    const val CAPABILITY_RESPONSE_SIZE = VERSION_SIZE + CAPABILITIES_SIZE

    data class HandshakeMessage(
        val protocolVersion: Int,
        val capabilities: Int,
        val sessionId: ByteArray,
        val authRequired: Boolean,
        val challenge: ByteArray?,
        val authNonce: ByteArray?
    ) {
        init {
            require(sessionId.size == CryptoHelper.SESSION_ID_SIZE) { "sessionId must be ${CryptoHelper.SESSION_ID_SIZE} bytes" }
            if (authRequired) {
                require(challenge != null && challenge.size == CryptoHelper.CHALLENGE_SIZE) {
                    "challenge must be ${CryptoHelper.CHALLENGE_SIZE} bytes when authRequired"
                }
                require(authNonce != null && authNonce.size == CryptoHelper.NONCE_SIZE) {
                    "authNonce must be ${CryptoHelper.NONCE_SIZE} bytes when authRequired"
                }
            } else {
                require(challenge == null) { "challenge must be null when auth not required" }
                require(authNonce == null) { "authNonce must be null when auth not required" }
            }
        }
    }

    data class CapabilityResponse(
        val protocolVersion: Int,
        val capabilities: Int
    ) {
        fun supportsCodec(capability: Int): Boolean = (capabilities and capability) != 0

        fun isCompatibleWith(other: CapabilityResponse): Boolean {
            return protocolVersion == other.protocolVersion && (capabilities and other.capabilities) != 0
        }
    }

    fun writeHandshake(
        output: OutputStream,
        sessionId: ByteArray,
        authRequired: Boolean,
        challenge: ByteArray?,
        authNonce: ByteArray?,
        protocolVersion: Int = PROTOCOL_VERSION,
        capabilities: Int = CURRENT_CAPABILITIES
    ) {
        HandshakeMessage(protocolVersion, capabilities, sessionId, authRequired, challenge, authNonce)
        val size = if (authRequired) {
            HANDSHAKE_HEADER_SIZE + CryptoHelper.CHALLENGE_SIZE + CryptoHelper.NONCE_SIZE
        } else {
            HANDSHAKE_HEADER_SIZE
        }
        val buffer = ByteArray(size)
        var offset = 0
        System.arraycopy(MAGIC_BYTES, 0, buffer, offset, MAGIC_BYTES.size)
        offset += MAGIC_BYTES.size
        buffer[offset++] = ((protocolVersion ushr 8) and 0xFF).toByte()
        buffer[offset++] = (protocolVersion and 0xFF).toByte()
        buffer[offset++] = ((capabilities ushr 8) and 0xFF).toByte()
        buffer[offset++] = (capabilities and 0xFF).toByte()
        System.arraycopy(sessionId, 0, buffer, offset, CryptoHelper.SESSION_ID_SIZE)
        offset += CryptoHelper.SESSION_ID_SIZE
        buffer[offset++] = if (authRequired) AUTH_MODE_PAIRING_CODE else AUTH_MODE_OPEN
        if (authRequired && challenge != null && authNonce != null) {
            System.arraycopy(challenge, 0, buffer, offset, CryptoHelper.CHALLENGE_SIZE)
            offset += CryptoHelper.CHALLENGE_SIZE
            System.arraycopy(authNonce, 0, buffer, offset, CryptoHelper.NONCE_SIZE)
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
        var offset = MAGIC_BYTES.size
        val protocolVersion = ((header[offset].toInt() and 0xFF) shl 8) or (header[offset + 1].toInt() and 0xFF)
        offset += VERSION_SIZE
        val capabilities = ((header[offset].toInt() and 0xFF) shl 8) or (header[offset + 1].toInt() and 0xFF)
        offset += CAPABILITIES_SIZE
        val sessionId = header.copyOfRange(offset, offset + CryptoHelper.SESSION_ID_SIZE)
        offset += CryptoHelper.SESSION_ID_SIZE
        val authRequired = header[offset] == AUTH_MODE_PAIRING_CODE
        val challenge: ByteArray? = if (authRequired) {
            val challengeBuf = ByteArray(CryptoHelper.CHALLENGE_SIZE)
            if (!readFully(input, challengeBuf, 0, CryptoHelper.CHALLENGE_SIZE)) {
                return null
            }
            challengeBuf
        } else {
            null
        }
        val authNonce: ByteArray? = if (authRequired) {
            val nonceBuf = ByteArray(CryptoHelper.NONCE_SIZE)
            if (!readFully(input, nonceBuf, 0, CryptoHelper.NONCE_SIZE)) {
                return null
            }
            nonceBuf
        } else {
            null
        }
        return HandshakeMessage(protocolVersion, capabilities, sessionId, authRequired, challenge, authNonce)
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

    fun writeCapabilityResponse(
        output: OutputStream,
        protocolVersion: Int = PROTOCOL_VERSION,
        capabilities: Int = CURRENT_CAPABILITIES
    ) {
        val buffer = ByteArray(CAPABILITY_RESPONSE_SIZE)
        buffer[0] = ((protocolVersion ushr 8) and 0xFF).toByte()
        buffer[1] = (protocolVersion and 0xFF).toByte()
        buffer[2] = ((capabilities ushr 8) and 0xFF).toByte()
        buffer[3] = (capabilities and 0xFF).toByte()
        output.write(buffer)
        output.flush()
    }

    fun readCapabilityResponse(input: InputStream): CapabilityResponse? {
        val buffer = ByteArray(CAPABILITY_RESPONSE_SIZE)
        if (!readFully(input, buffer, 0, CAPABILITY_RESPONSE_SIZE)) {
            return null
        }
        val protocolVersion = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
        val capabilities = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
        return CapabilityResponse(protocolVersion, capabilities)
    }

    fun isVersionSupported(version: Int): Boolean = version == PROTOCOL_VERSION

    fun negotiateCapabilities(childCaps: Int, parentCaps: Int): Int {
        val shared = childCaps and parentCaps
        if (shared == 0) {
            throw IllegalStateException("No shared codec capability")
        }
        return shared
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