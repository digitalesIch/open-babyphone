/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorFrameSequenceTest {
    @Test
    fun `sequence does not advance without clients and remains valid for late parent`() {
        val sequence = MonitorFrameSequence()

        assertNull(sequence.take(0))
        assertNull(sequence.take(0))
        assertEquals(0, sequence.current())
        assertEquals(0, sequence.take(1))
        assertEquals(1, sequence.current())
    }

    @Test
    fun `takeInto matches take semantics without boxing`() {
        val sequence = MonitorFrameSequence()
        val buffer = IntArray(1)

        // No clients: never takes, never advances.
        assertEquals(false, sequence.takeInto(0, buffer))
        assertEquals(false, sequence.takeInto(0, buffer))
        assertEquals(0, sequence.current())

        // With clients: writes the taken value and advances identically.
        assertEquals(true, sequence.takeInto(1, buffer))
        assertEquals(0, buffer[0])
        assertEquals(1, sequence.current())
        assertEquals(true, sequence.takeInto(2, buffer))
        assertEquals(1, buffer[0])
        assertEquals(2, sequence.current())

        // reset() returns to the start for a fresh session.
        sequence.reset()
        assertEquals(0, sequence.current())
    }
}
