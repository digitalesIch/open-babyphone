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
package de.rochefort.childmonitor.audio

import de.rochefort.childmonitor.audio.G711UCodec
import org.junit.Assert.*
import org.junit.Test

class G711UCodecTest {

    private val codec = G711UCodec()

    @Test
    fun encodeDecode_RoundTrip() {
        val pcmBuffer = shortArrayOf(0, 1000, -1000, 500, -500, 4000, -4000)
        val ulawBuffer = ByteArray(pcmBuffer.size)
        val decodedBuffer = shortArrayOf(0, 0, 0, 0, 0, 0, 0)

        val encodedSize = codec.encode(pcmBuffer, pcmBuffer.size, ulawBuffer, 0)
        val decodedSize = codec.decode(decodedBuffer, ulawBuffer, encodedSize, 0)

        assertEquals(pcmBuffer.size, encodedSize)
        assertEquals(pcmBuffer.size, decodedSize)
        
        for (i in pcmBuffer.indices) {
            val original = pcmBuffer[i].toInt()
            val decoded = decodedBuffer[i].toInt()
            val diff = kotlin.math.abs(original - decoded)
            assertTrue("Sample $i: expected $original but got $decoded (diff=$diff)", diff < 5000)
        }
    }

    @Test
    fun encode_ZeroInput() {
        val pcmBuffer = shortArrayOf(0, 0, 0, 0)
        val ulawBuffer = ByteArray(pcmBuffer.size)

        val encodedSize = codec.encode(pcmBuffer, pcmBuffer.size, ulawBuffer, 0)

        assertEquals(pcmBuffer.size, encodedSize)
        for (byte in ulawBuffer) {
            assertEquals(0x7F.toByte(), byte)
        }
    }

    @Test
    fun decode_EmptyBuffer() {
        val ulawBuffer = ByteArray(0)
        val decodedBuffer = shortArrayOf()

        val decodedSize = codec.decode(decodedBuffer, ulawBuffer, 0, 0)

        assertEquals(0, decodedSize)
    }

    @Test
    fun encodeDecode_MaxValues() {
        val pcmBuffer = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE)
        val ulawBuffer = ByteArray(pcmBuffer.size)
        val decodedBuffer = shortArrayOf(0, 0)

        val encodedSize = codec.encode(pcmBuffer, pcmBuffer.size, ulawBuffer, 0)
        val decodedSize = codec.decode(decodedBuffer, ulawBuffer, encodedSize, 0)

        assertEquals(2, encodedSize)
        assertEquals(2, decodedSize)
    }
}
