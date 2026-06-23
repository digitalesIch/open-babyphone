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
package org.openbabyphone.audio

import org.openbabyphone.audio.JitterBuffer
import org.junit.Assert.*
import org.junit.Test

class JitterBufferTest {

    @Test
    fun addFrame_FullQueue_ReturnsFalse() {
        val buffer = JitterBuffer()
        val frame = JitterBuffer.DecodedFrame(0, 0, ByteArray(10))
        
        for (i in 0 until 10) {
            buffer.addFrame(frame)
        }
        
        val result = buffer.addFrame(frame)
        
        assertFalse(result)
    }

    @Test
    fun getFrame_EmptyQueue_WaitsAndReturns() {
        val buffer = JitterBuffer()
        
        val frame = buffer.getFrame(100)
        
        assertNull(frame)
    }

    @Test
    fun isReady_BelowThreshold_ReturnsFalse() {
        val buffer = JitterBuffer()
        
        for (i in 0 until 4) {
            buffer.addFrame(JitterBuffer.DecodedFrame(i, i * 20, ByteArray(10)))
        }
        
        assertFalse(buffer.isReady())
    }

    @Test
    fun isReady_AtThreshold_ReturnsTrue() {
        val buffer = JitterBuffer()
        
        for (i in 0 until 6) {
            buffer.addFrame(JitterBuffer.DecodedFrame(i, i * 20, ByteArray(10)))
        }
        
        assertTrue(buffer.isReady())
    }

    @Test
    fun getBufferLevelMs_EmptyBuffer_ReturnsZero() {
        val buffer = JitterBuffer()
        
        val level = buffer.getBufferLevelMs()
        
        assertEquals(0, level)
    }

    @Test
    fun getBufferLevelMs_FilledBuffer_ReturnsCorrectLevel() {
        val buffer = JitterBuffer()
        
        for (i in 0 until 5) {
            buffer.addFrame(JitterBuffer.DecodedFrame(i, i * 20, ByteArray(10)))
        }
        
        val level = buffer.getBufferLevelMs()
        
        assertEquals(100, level)
    }

    @Test
    fun getStats_ReturnsCorrectStats() {
        val buffer = JitterBuffer()
        
        for (i in 0 until 3) {
            buffer.addFrame(JitterBuffer.DecodedFrame(i, i * 20, ByteArray(10)))
        }
        
        val stats = buffer.getStats()
        
        assertTrue(stats.contains("Total: 3"))
        assertTrue(stats.contains("Level:"))
    }

    @Test
    fun clear_ResetsBuffer() {
        val buffer = JitterBuffer()
        
        for (i in 0 until 5) {
            buffer.addFrame(JitterBuffer.DecodedFrame(i, i * 20, ByteArray(10)))
        }
        
        buffer.clear()
        
        assertEquals(0, buffer.getBufferLevelMs())
        assertFalse(buffer.isReady())
    }

    @Test
    fun getFrame_ReturnsFramesInOrder() {
        val buffer = JitterBuffer()
        
        for (i in 0 until 5) {
            buffer.addFrame(JitterBuffer.DecodedFrame(i, i * 20, ByteArray(10)))
        }
        
        for (i in 0 until 5) {
            val frame = buffer.getFrame(100)
            assertNotNull(frame)
            assertEquals(i, frame!!.seqNum)
        }
    }
}
