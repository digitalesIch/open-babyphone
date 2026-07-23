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

import java.io.InputStream
import java.io.OutputStream

data class SessionInfo(
    val sessionId: ByteArray,
    val streamKey: ByteArray,
    val firstSequence: Int,
    val childId: String,
    val pairingId: String
)

data class ExpectedChildIdentity(val childId: String, val pairingId: String) {
    fun matches(hello: Handshake.ChildHello): Boolean =
        childId == hello.childId && pairingId == hello.pairingId
}

/** Fixed-size, mutually authenticated OBP4 handshake messages. */
object Handshake {
    const val MAGIC = "OBP4"
    val MAGIC_BYTES: ByteArray = MAGIC.toByteArray(Charsets.US_ASCII)
    const val PROTOCOL_VERSION = 4

    const val CAP_G711_ULAW = 1 shl 0
    const val CAP_OPUS = 1 shl 1
    const val CURRENT_CAPABILITIES = CAP_G711_ULAW
    const val AUTH_MODE_PAIRING_CODE: Byte = 1

    private const val ID_SIZE = 16
    private const val VERSION_SIZE = 2
    private const val CAPABILITIES_SIZE = 2
    private const val SEQUENCE_SIZE = 4
    const val CHILD_HELLO_SIZE = 4 + VERSION_SIZE + CAPABILITIES_SIZE + 1 +
        ID_SIZE + ID_SIZE + CryptoHelper.SESSION_ID_SIZE + CryptoHelper.SALT_SIZE + CryptoHelper.CHALLENGE_SIZE
    const val PARENT_RESPONSE_SIZE = VERSION_SIZE + CAPABILITIES_SIZE +
        CryptoHelper.CHALLENGE_SIZE + CryptoHelper.NONCE_SIZE + CryptoHelper.PROOF_SIZE
    const val CHILD_ACK_SIZE = SEQUENCE_SIZE + CryptoHelper.NONCE_SIZE + CryptoHelper.PROOF_SIZE

    private val PARENT_PROOF_LABEL = "OBP4 parent proof".toByteArray(Charsets.US_ASCII)
    private val CHILD_PROOF_LABEL = "OBP4 child proof".toByteArray(Charsets.US_ASCII)
    private val ID_PATTERN = Regex("[a-z0-9]{$ID_SIZE}")

    data class ChildHello(
        val protocolVersion: Int,
        val capabilities: Int,
        val authMode: Byte,
        val childId: String,
        val pairingId: String,
        val sessionId: ByteArray,
        val kdfSalt: ByteArray,
        val childChallenge: ByteArray
    ) {
        init {
            require(protocolVersion in 0..0xffff)
            require(capabilities in 0..0xffff)
            require(authMode == AUTH_MODE_PAIRING_CODE)
            require(ID_PATTERN.matches(childId))
            require(ID_PATTERN.matches(pairingId))
            require(sessionId.size == CryptoHelper.SESSION_ID_SIZE)
            require(kdfSalt.size == CryptoHelper.SALT_SIZE)
            require(childChallenge.size == CryptoHelper.CHALLENGE_SIZE)
        }

        fun toByteArray(): ByteArray {
            val bytes = ByteArray(CHILD_HELLO_SIZE)
            var offset = 0
            MAGIC_BYTES.copyInto(bytes, offset)
            offset += MAGIC_BYTES.size
            offset = putU16(bytes, offset, protocolVersion)
            offset = putU16(bytes, offset, capabilities)
            bytes[offset++] = authMode
            childId.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
            offset += ID_SIZE
            pairingId.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
            offset += ID_SIZE
            sessionId.copyInto(bytes, offset)
            offset += CryptoHelper.SESSION_ID_SIZE
            kdfSalt.copyInto(bytes, offset)
            offset += CryptoHelper.SALT_SIZE
            childChallenge.copyInto(bytes, offset)
            return bytes
        }
    }

    data class ParentResponse(
        val protocolVersion: Int,
        val capabilities: Int,
        val parentChallenge: ByteArray,
        val nonce: ByteArray,
        val proof: ByteArray
    ) {
        init {
            require(protocolVersion in 0..0xffff)
            require(capabilities in 0..0xffff)
            require(parentChallenge.size == CryptoHelper.CHALLENGE_SIZE)
            require(nonce.size == CryptoHelper.NONCE_SIZE)
            require(proof.size == CryptoHelper.PROOF_SIZE)
        }

        fun prefixBytes(): ByteArray {
            val bytes = ByteArray(VERSION_SIZE + CAPABILITIES_SIZE + CryptoHelper.CHALLENGE_SIZE + CryptoHelper.NONCE_SIZE)
            var offset = putU16(bytes, 0, protocolVersion)
            offset = putU16(bytes, offset, capabilities)
            parentChallenge.copyInto(bytes, offset)
            offset += CryptoHelper.CHALLENGE_SIZE
            nonce.copyInto(bytes, offset)
            return bytes
        }

        fun toByteArray(): ByteArray = concat(prefixBytes(), proof)
    }

    data class ChildAck(
        val firstSequence: Int,
        val nonce: ByteArray,
        val proof: ByteArray
    ) {
        init {
            require(firstSequence >= 0)
            require(nonce.size == CryptoHelper.NONCE_SIZE)
            require(proof.size == CryptoHelper.PROOF_SIZE)
        }

        fun prefixBytes(): ByteArray {
            val bytes = ByteArray(SEQUENCE_SIZE + CryptoHelper.NONCE_SIZE)
            putU32(bytes, 0, firstSequence)
            nonce.copyInto(bytes, SEQUENCE_SIZE)
            return bytes
        }

        fun toByteArray(): ByteArray = concat(prefixBytes(), proof)
    }

    fun createChildHello(
        identity: ChildDeviceIdentity,
        sessionId: ByteArray,
        kdfSalt: ByteArray,
        challenge: ByteArray = CryptoHelper.generateChallenge()
    ): ChildHello = ChildHello(
        PROTOCOL_VERSION,
        CURRENT_CAPABILITIES,
        AUTH_MODE_PAIRING_CODE,
        identity.childId,
        identity.pairingId,
        sessionId,
        kdfSalt,
        challenge
    )

    fun writeChildHello(output: OutputStream, hello: ChildHello) {
        output.write(hello.toByteArray())
        output.flush()
    }

    fun readChildHello(input: InputStream): ChildHello? {
        val bytes = readFixed(input, CHILD_HELLO_SIZE) ?: return null
        if (!bytes.copyOfRange(0, MAGIC_BYTES.size).contentEquals(MAGIC_BYTES)) return null
        var offset = MAGIC_BYTES.size
        val version = readU16(bytes, offset)
        offset += VERSION_SIZE
        val capabilities = readU16(bytes, offset)
        offset += CAPABILITIES_SIZE
        val mode = bytes[offset++]
        if (mode != AUTH_MODE_PAIRING_CODE) return null
        val childId = readId(bytes, offset) ?: return null
        offset += ID_SIZE
        val pairingId = readId(bytes, offset) ?: return null
        offset += ID_SIZE
        val sessionId = bytes.copyOfRange(offset, offset + CryptoHelper.SESSION_ID_SIZE)
        offset += CryptoHelper.SESSION_ID_SIZE
        val salt = bytes.copyOfRange(offset, offset + CryptoHelper.SALT_SIZE)
        offset += CryptoHelper.SALT_SIZE
        val challenge = bytes.copyOfRange(offset, offset + CryptoHelper.CHALLENGE_SIZE)
        return ChildHello(version, capabilities, mode, childId, pairingId, sessionId, salt, challenge)
    }

    fun createParentResponse(
        hello: ChildHello,
        authKey: ByteArray,
        challenge: ByteArray = CryptoHelper.generateChallenge(),
        nonce: ByteArray = CryptoHelper.generateNonce()
    ): ParentResponse {
        val unsigned = ParentResponse(PROTOCOL_VERSION, CURRENT_CAPABILITIES, challenge, nonce, ByteArray(CryptoHelper.PROOF_SIZE))
        val aad = concat(PARENT_PROOF_LABEL, hello.toByteArray(), unsigned.prefixBytes())
        val proof = CryptoHelper.createProof(hello.childChallenge, authKey, nonce, aad)
        return unsigned.copy(proof = proof)
    }

    fun writeParentResponse(output: OutputStream, response: ParentResponse) {
        output.write(response.toByteArray())
        output.flush()
    }

    fun readParentResponse(input: InputStream): ParentResponse? {
        val bytes = readFixed(input, PARENT_RESPONSE_SIZE) ?: return null
        var offset = 0
        val version = readU16(bytes, offset)
        offset += VERSION_SIZE
        val capabilities = readU16(bytes, offset)
        offset += CAPABILITIES_SIZE
        val challenge = bytes.copyOfRange(offset, offset + CryptoHelper.CHALLENGE_SIZE)
        offset += CryptoHelper.CHALLENGE_SIZE
        val nonce = bytes.copyOfRange(offset, offset + CryptoHelper.NONCE_SIZE)
        offset += CryptoHelper.NONCE_SIZE
        val proof = bytes.copyOfRange(offset, offset + CryptoHelper.PROOF_SIZE)
        return ParentResponse(version, capabilities, challenge, nonce, proof)
    }

    fun verifyParentResponse(hello: ChildHello, response: ParentResponse, authKey: ByteArray): Boolean {
        if (!isCompatible(response.protocolVersion, response.capabilities)) return false
        val aad = concat(PARENT_PROOF_LABEL, hello.toByteArray(), response.prefixBytes())
        return CryptoHelper.verifyProof(response.proof, hello.childChallenge, authKey, response.nonce, aad)
    }

    fun createChildAck(
        hello: ChildHello,
        response: ParentResponse,
        firstSequence: Int,
        authKey: ByteArray,
        nonce: ByteArray = generateDistinctNonce(response.nonce)
    ): ChildAck {
        require(!nonce.contentEquals(response.nonce)) { "Directional authentication nonces must differ" }
        val unsigned = ChildAck(firstSequence, nonce, ByteArray(CryptoHelper.PROOF_SIZE))
        val aad = concat(CHILD_PROOF_LABEL, hello.toByteArray(), response.toByteArray(), unsigned.prefixBytes())
        val proof = CryptoHelper.createProof(response.parentChallenge, authKey, nonce, aad)
        return unsigned.copy(proof = proof)
    }

    fun writeChildAck(output: OutputStream, ack: ChildAck) {
        output.write(ack.toByteArray())
        output.flush()
    }

    fun readChildAck(input: InputStream): ChildAck? {
        val bytes = readFixed(input, CHILD_ACK_SIZE) ?: return null
        val sequence = readU32(bytes, 0) ?: return null
        val nonce = bytes.copyOfRange(SEQUENCE_SIZE, SEQUENCE_SIZE + CryptoHelper.NONCE_SIZE)
        val proof = bytes.copyOfRange(SEQUENCE_SIZE + CryptoHelper.NONCE_SIZE, bytes.size)
        return ChildAck(sequence, nonce, proof)
    }

    fun verifyChildAck(hello: ChildHello, response: ParentResponse, ack: ChildAck, authKey: ByteArray): Boolean {
        if (ack.nonce.contentEquals(response.nonce)) return false
        val aad = concat(CHILD_PROOF_LABEL, hello.toByteArray(), response.toByteArray(), ack.prefixBytes())
        return CryptoHelper.verifyProof(ack.proof, response.parentChallenge, authKey, ack.nonce, aad)
    }

    fun streamKeyContext(hello: ChildHello): ByteArray {
        val fixed = hello.toByteArray()
        return fixed.copyOfRange(0, CHILD_HELLO_SIZE - CryptoHelper.CHALLENGE_SIZE)
    }

    fun isVersionSupported(version: Int): Boolean = version == PROTOCOL_VERSION

    fun isCompatible(version: Int, capabilities: Int): Boolean =
        version == PROTOCOL_VERSION && capabilities == CURRENT_CAPABILITIES

    private fun generateDistinctNonce(other: ByteArray): ByteArray {
        var nonce: ByteArray
        do {
            nonce = CryptoHelper.generateNonce()
        } while (nonce.contentEquals(other))
        return nonce
    }

    private fun readFixed(input: InputStream, size: Int): ByteArray? {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(bytes, offset, size - offset)
            if (count < 0) return null
            if (count == 0) continue
            offset += count
        }
        return bytes
    }

    private fun readId(bytes: ByteArray, offset: Int): String? {
        val value = String(bytes, offset, ID_SIZE, Charsets.US_ASCII)
        return value.takeIf(ID_PATTERN::matches)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int): Int {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
        return offset + VERSION_SIZE
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int? {
        val value = ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
        return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun concat(vararg values: ByteArray): ByteArray {
        val total = values.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        values.forEach {
            it.copyInto(result, offset)
            offset += it.size
        }
        return result
    }
}
