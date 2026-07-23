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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CryptoHelperTest {

    @Test
    fun deriveKey_CurrentParameters_MatchesKnownAnswer() {
        val salt = ByteArray(CryptoHelper.SALT_SIZE) { 0x66 }

        val key = CryptoHelper.deriveKey("test123", salt)

        assertArrayEquals(
            Base64.getDecoder().decode("+3VJVjW8uwvi7cI+WVlEVgSU2UM3xm+1zIdfI9sN/zg="),
            key
        )
    }

    @Test
    fun aead_Rfc8439Vector_MatchesCiphertextAndTag() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("070000004041424344454647")
        val associatedData = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext = hex(
            "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f6620" +
                "2739393a204966204920636f756c64206f6666657220796f75206f6e6c79206f6e65207469" +
                "7020666f7220746865206675747572652c2073756e73637265656e20776f756c642062652069" +
                "742e"
        )
        val expected = hex(
            "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9" +
                "671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee3" +
                "28091b58fab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b6116" +
                "1ae10b594f09e26a7e902ecbd0600691"
        )

        val encrypted = CryptoHelper.encryptAead(plaintext, key, nonce, associatedData)

        assertArrayEquals(expected, encrypted)
        assertArrayEquals(plaintext, CryptoHelper.decryptAead(encrypted, key, nonce, associatedData))
    }

    @Test
    fun aeadInto_Rfc8439Vector_MatchesCiphertextAndTagAtOffsets() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("070000004041424344454647")
        val associatedData = hex("0050515253c0c1c2c3c4c5c6c700")
        val plaintext = hex(
            "004c616469657320616e642047656e746c656d656e206f662074686520636c617373206f6620" +
                "2739393a204966204920636f756c64206f6666657220796f75206f6e6c79206f6e65207469" +
                "7020666f7220746865206675747572652c2073756e73637265656e20776f756c642062652069" +
                "742e00"
        )
        val expected = hex(
            "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9" +
                "671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee3" +
                "28091b58fab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b6116" +
                "1ae10b594f09e26a7e902ecbd0600691"
        )
        val output = ByteArray(expected.size + 4)

        val written = CryptoHelper.encryptAeadInto(
            plaintext,
            1,
            plaintext.size - 2,
            key,
            nonce,
            associatedData,
            1,
            associatedData.size - 2,
            output,
            2
        )

        assertEquals(expected.size, written)
        assertArrayEquals(expected, output.copyOfRange(2, 2 + written))
        assertThrows(IllegalArgumentException::class.java) {
            CryptoHelper.encryptAeadInto(
                plaintext,
                1,
                plaintext.size - 2,
                key,
                nonce,
                associatedData,
                1,
                associatedData.size - 2,
                ByteArray(expected.size - 1),
                0
            )
        }
    }

    @Test
    fun decryptAead_ModifiedTag_ReturnsNull() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(CryptoHelper.NONCE_SIZE) { (it + 1).toByte() }
        val encrypted = CryptoHelper.encryptAead("audio".toByteArray(), key, nonce, byteArrayOf())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        assertNull(CryptoHelper.decryptAead(encrypted, key, nonce, byteArrayOf()))
    }

    @Test
    fun decryptAead_WrongKey_ReturnsNull() {
        val key = ByteArray(32) { it.toByte() }
        val wrongKey = key.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val nonce = ByteArray(CryptoHelper.NONCE_SIZE) { (it + 1).toByte() }
        val encrypted = CryptoHelper.encryptAead("audio".toByteArray(), key, nonce, byteArrayOf())

        assertNull(CryptoHelper.decryptAead(encrypted, wrongKey, nonce, byteArrayOf()))
    }

    @Test
    fun decryptAead_ShorterThanTag_ReturnsNull() {
        assertNull(
            CryptoHelper.decryptAead(
                ByteArray(CryptoHelper.AUTH_TAG_SIZE - 1),
                ByteArray(32),
                ByteArray(CryptoHelper.NONCE_SIZE),
                byteArrayOf()
            )
        )
    }

    @Test
    fun proof_TranscriptBoundConstruction_RoundTripsAndRejectsWrongInputs() {
        val key = ByteArray(32) { it.toByte() }
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        val challenge = ByteArray(CryptoHelper.CHALLENGE_SIZE) { (it * 3).toByte() }
        val nonce = ByteArray(CryptoHelper.NONCE_SIZE) { (it * 5).toByte() }

        val transcript = "OBP4 transcript".toByteArray()
        val encrypted = CryptoHelper.createProof(challenge, key, nonce, transcript)

        assertTrue(CryptoHelper.verifyProof(encrypted, challenge, key, nonce, transcript))
        assertFalse(CryptoHelper.verifyProof(encrypted, challenge, wrongKey, nonce, transcript))
        assertFalse(CryptoHelper.verifyProof(encrypted, challenge, key, nonce, "tampered".toByteArray()))
        assertFalse(CryptoHelper.verifyProof(ByteArray(15), challenge, key, nonce, transcript))
    }

    @Test
    fun labeledKeyDerivation_SeparatesAuthAndStreamKeys() {
        val baseKey = ByteArray(CryptoHelper.KEY_SIZE) { it.toByte() }
        val authKey = CryptoHelper.deriveAuthKey(baseKey)
        val streamKey = CryptoHelper.deriveStreamKey(baseKey, "session".toByteArray())
        val otherStreamKey = CryptoHelper.deriveStreamKey(baseKey, "other session".toByteArray())

        assertFalse(authKey.contentEquals(streamKey))
        assertFalse(streamKey.contentEquals(otherStreamKey))
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
