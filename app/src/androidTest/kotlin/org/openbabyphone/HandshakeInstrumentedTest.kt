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
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeInstrumentedTest {
    @Test
    fun mutualAuthentication_RoundTripOnAndroidJca() {
        val hello = Handshake.createChildHello(
            ChildDeviceIdentity("abcdefghijklmnop", "1234567890abcdef"),
            ByteArray(CryptoHelper.SESSION_ID_SIZE) { it.toByte() },
            ByteArray(CryptoHelper.SALT_SIZE) { (it + 1).toByte() },
            ByteArray(CryptoHelper.CHALLENGE_SIZE) { (it + 2).toByte() }
        )
        val baseKey = CryptoHelper.deriveKey("ABCDEF12", hello.kdfSalt)
        val authKey = CryptoHelper.deriveAuthKey(baseKey)
        val response = Handshake.createParentResponse(hello, authKey)
        val ack = Handshake.createChildAck(hello, response, 42, authKey)

        assertTrue(Handshake.verifyParentResponse(hello, response, authKey))
        assertTrue(Handshake.verifyChildAck(hello, response, ack, authKey))
        assertArrayEquals(
            CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(hello)),
            CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(hello.copy()))
        )
    }

    @Test
    fun mutualAuthentication_RejectsWrongCodeTamperAndDowngrade() {
        val hello = Handshake.createChildHello(
            ChildDeviceIdentity("abcdefghijklmnop", "1234567890abcdef"),
            ByteArray(CryptoHelper.SESSION_ID_SIZE),
            ByteArray(CryptoHelper.SALT_SIZE) { 1 },
            ByteArray(CryptoHelper.CHALLENGE_SIZE) { 2 }
        )
        val correct = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("ABCDEF12", hello.kdfSalt))
        val wrong = CryptoHelper.deriveAuthKey(CryptoHelper.deriveKey("WRONG999", hello.kdfSalt))
        val response = Handshake.createParentResponse(hello, wrong)

        assertFalse(Handshake.verifyParentResponse(hello, response, correct))

        val validResponse = Handshake.createParentResponse(hello, correct)
        val ack = Handshake.createChildAck(hello, validResponse, 0, correct)
        assertFalse(Handshake.verifyParentResponse(hello, validResponse.copy(protocolVersion = 3), correct))
        assertFalse(Handshake.verifyChildAck(hello, validResponse, ack.copy(firstSequence = 1), correct))
    }
}
