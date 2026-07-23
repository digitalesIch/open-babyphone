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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException

class HandshakeDeadlineTest {
    @Test
    fun `read timeouts shrink against one absolute deadline`() {
        var now = 1_000L
        val timeouts = mutableListOf<Int>()
        val deadline = HandshakeDeadline(10_000L) { now }
        val input = deadline.input(ByteArrayInputStream(byteArrayOf(1, 2))) { timeouts.add(it) }

        assertEquals(1, input.read())
        now = 7_000L
        assertEquals(2, input.read())

        assertEquals(listOf(10_000, 4_000), timeouts)
    }

    @Test
    fun `deadline expires across computation without granting another read window`() {
        var now = 1_000L
        val deadline = HandshakeDeadline(10_000L) { now }
        val input = deadline.input(ByteArrayInputStream(byteArrayOf(1))) { }

        now = 11_000L

        assertThrows(SocketTimeoutException::class.java) { input.read() }
        assertThrows(SocketTimeoutException::class.java) { deadline.check() }
    }
}
