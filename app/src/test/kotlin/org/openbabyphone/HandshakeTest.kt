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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

class HandshakeTest {
    private val identity = ChildDeviceIdentity("abcdefghijklmnop", "1234567890abcdef")
    private val sessionId = ByteArray(CryptoHelper.SESSION_ID_SIZE) { (it + 1).toByte() }
    private val salt = ByteArray(CryptoHelper.SALT_SIZE) { (it + 0x10).toByte() }
    private val childChallenge = ByteArray(CryptoHelper.CHALLENGE_SIZE) { (it + 0x20).toByte() }
    private val parentChallenge = ByteArray(CryptoHelper.CHALLENGE_SIZE) { (it + 0x40).toByte() }
    private val parentNonce = ByteArray(CryptoHelper.NONCE_SIZE) { (it + 0x60).toByte() }
    private val childNonce = ByteArray(CryptoHelper.NONCE_SIZE) { (it + 0x70).toByte() }
    private val baseKey = ByteArray(CryptoHelper.KEY_SIZE) { it.toByte() }
    private val authKey = CryptoHelper.deriveAuthKey(baseKey)

    @Test
    fun `child hello has fixed OBP4 serialization`() {
        val hello = hello()

        assertArrayEquals(
            hex(
                "4f4250340004000101" +
                    "6162636465666768696a6b6c6d6e6f70" +
                    "31323334353637383930616263646566" +
                    "0102030405060708" +
                    "101112131415161718191a1b1c1d1e1f" +
                    "202122232425262728292a2b2c2d2e2f" +
                    "303132333435363738393a3b3c3d3e3f"
            ),
            hello.toByteArray()
        )
        assertArrayEquals(hello.toByteArray(), roundTripHello(hello).toByteArray())
    }

    @Test
    fun `unknown authentication mode is rejected`() {
        val bytes = hello().toByteArray()
        bytes[8] = 0x7f

        assertNull(Handshake.readChildHello(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `old magic is rejected`() {
        val bytes = hello().toByteArray()
        "OBP3".toByteArray(Charsets.US_ASCII).copyInto(bytes)

        assertNull(Handshake.readChildHello(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `partial fixed messages are rejected`() {
        assertNull(Handshake.readChildHello(ByteArrayInputStream(ByteArray(Handshake.CHILD_HELLO_SIZE - 1))))
        assertNull(Handshake.readParentResponse(ByteArrayInputStream(ByteArray(Handshake.PARENT_RESPONSE_SIZE - 1))))
        assertNull(Handshake.readChildAck(ByteArrayInputStream(ByteArray(Handshake.CHILD_ACK_SIZE - 1))))
    }

    @Test
    fun `mutual authentication succeeds and derives same stream key`() {
        val hello = hello()
        val response = Handshake.createParentResponse(hello, authKey, parentChallenge, parentNonce)
        assertTrue(Handshake.verifyParentResponse(hello, response, authKey))

        val ack = Handshake.createChildAck(hello, response, 23, authKey, childNonce)
        assertTrue(Handshake.verifyChildAck(hello, response, ack, authKey))
        assertArrayEquals(
            Base64.getDecoder().decode("L0UX9DFwkSSAwqC0Go+2CHvJb2fzM+ajGMR0avUCHxU="),
            authKey
        )
        assertArrayEquals(
            Base64.getDecoder().decode(
                "AAQAAUBBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiY2RlZmdoaWpr" +
                    "setR7Uy0iJJwtUR91a7YlyxaLS+1cbl4JIEp76C1aJoB9u9TSnbLcji4RN1z1I8Q"
            ),
            response.toByteArray()
        )
        assertArrayEquals(
            Base64.getDecoder().decode(
                "AAAAF3BxcnN0dXZ3eHl6e3jNugN0jaTVSeePiArfjcl5z3gZZb36UCGYrxQq5SOg" +
                    "6dNMhPxF5D4NOhsv9b3xUg=="
            ),
            ack.toByteArray()
        )

        val childStreamKey = CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(hello))
        val parentStreamKey = CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(roundTripHello(hello)))
        assertArrayEquals(childStreamKey, parentStreamKey)
        assertArrayEquals(response.toByteArray(), roundTripParent(response).toByteArray())
        assertArrayEquals(ack.toByteArray(), roundTripAck(ack).toByteArray())
    }

    @Test
    fun `wrong pairing key cannot authenticate either proof`() {
        val hello = hello()
        val wrongAuthKey = CryptoHelper.deriveAuthKey(ByteArray(CryptoHelper.KEY_SIZE) { (it + 1).toByte() })
        val response = Handshake.createParentResponse(hello, wrongAuthKey, parentChallenge, parentNonce)

        assertFalse(Handshake.verifyParentResponse(hello, response, authKey))

        val validResponse = Handshake.createParentResponse(hello, authKey, parentChallenge, parentNonce)
        val wrongAck = Handshake.createChildAck(hello, validResponse, 0, wrongAuthKey, childNonce)
        assertFalse(Handshake.verifyChildAck(hello, validResponse, wrongAck, authKey))
    }

    @Test
    fun `transcript tampering and downgrade are rejected`() {
        val hello = hello()
        val response = Handshake.createParentResponse(hello, authKey, parentChallenge, parentNonce)
        val tamperedHello = hello.copy(pairingId = "1234567890abcdee")
        assertFalse(Handshake.verifyParentResponse(tamperedHello, response, authKey))

        val downgraded = response.copy(protocolVersion = 3)
        assertFalse(Handshake.verifyParentResponse(hello, downgraded, authKey))

        val ack = Handshake.createChildAck(hello, response, 7, authKey, childNonce)
        val tamperedResponse = response.copy(proof = response.proof.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        assertFalse(Handshake.verifyChildAck(hello, tamperedResponse, ack, authKey))
        assertFalse(Handshake.verifyChildAck(hello, response, ack.copy(firstSequence = 8), authKey))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `directional authentication nonces must differ`() {
        val hello = hello()
        val response = Handshake.createParentResponse(hello, authKey, parentChallenge, parentNonce)
        Handshake.createChildAck(hello, response, 0, authKey, parentNonce)
    }

    @Test
    fun `expected trusted identity rejects either identity mismatch`() {
        val hello = hello()
        assertTrue(ExpectedChildIdentity(identity.childId, identity.pairingId).matches(hello))
        assertFalse(ExpectedChildIdentity("ponmlkjihgfedcba", identity.pairingId).matches(hello))
        assertFalse(ExpectedChildIdentity(identity.childId, "fedcba0987654321").matches(hello))
    }

    @Test
    fun `child prederived stream key matches every per-parent hello in the session`() {
        val template = Handshake.createChildHello(
            identity,
            sessionId,
            salt,
            ByteArray(CryptoHelper.CHALLENGE_SIZE)
        )
        val actual = hello()

        assertArrayEquals(
            CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(template)),
            CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(actual))
        )
    }

    private fun hello() = Handshake.createChildHello(identity, sessionId, salt, childChallenge)

    private fun roundTripHello(hello: Handshake.ChildHello): Handshake.ChildHello {
        val output = ByteArrayOutputStream()
        Handshake.writeChildHello(output, hello)
        return Handshake.readChildHello(ByteArrayInputStream(output.toByteArray()))!!
    }

    private fun roundTripParent(response: Handshake.ParentResponse): Handshake.ParentResponse {
        val output = ByteArrayOutputStream()
        Handshake.writeParentResponse(output, response)
        return Handshake.readParentResponse(ByteArrayInputStream(output.toByteArray()))!!
    }

    private fun roundTripAck(ack: Handshake.ChildAck): Handshake.ChildAck {
        val output = ByteArrayOutputStream()
        Handshake.writeChildAck(output, ack)
        return Handshake.readChildAck(ByteArrayInputStream(output.toByteArray()))!!
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
