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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {

    @Test
    fun `empty string is valid`() {
        assertTrue(PairingCode.isValid(""))
    }

    @Test
    fun `blank string is valid after trim`() {
        assertTrue(PairingCode.isValid("   "))
    }

    @Test
    fun `single alphanumeric character is valid`() {
        assertTrue(PairingCode.isValid("A"))
        assertTrue(PairingCode.isValid("1"))
        assertTrue(PairingCode.isValid("z"))
    }

    @Test
    fun `64 alphanumeric characters are valid`() {
        val code = "a".repeat(64)
        assertTrue(PairingCode.isValid(code))
    }

    @Test
    fun `65 characters are invalid`() {
        val code = "a".repeat(65)
        assertFalse(PairingCode.isValid(code))
    }

    @Test
    fun `mixed alphanumeric code is valid`() {
        assertTrue(PairingCode.isValid("AbCd123XyZ"))
    }

    @Test
    fun `spaces in code are invalid`() {
        assertFalse(PairingCode.isValid("abc def"))
    }

    @Test
    fun `special characters are invalid`() {
        assertFalse(PairingCode.isValid("abc!"))
        assertFalse(PairingCode.isValid("abc-def"))
        assertFalse(PairingCode.isValid("abc_def"))
        assertFalse(PairingCode.isValid("a.b"))
        assertFalse(PairingCode.isValid("a@b"))
    }

    @Test
    fun `unicode characters are invalid`() {
        assertFalse(PairingCode.isValid("über123"))
        assertFalse(PairingCode.isValid("café"))
    }

    @Test
    fun `newline in the middle is invalid`() {
        assertFalse(PairingCode.isValid("ab\nc"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("ABC123", PairingCode.normalize("  ABC123  "))
    }

    @Test
    fun `normalize empty string returns empty`() {
        assertEquals("", PairingCode.normalize(""))
        assertEquals("", PairingCode.normalize("   "))
    }
}
