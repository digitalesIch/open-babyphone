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

import org.junit.Assert.*
import org.junit.Test

class DeviceNameTest {

    @Test
    fun `empty string is invalid`() {
        assertFalse(DeviceName.isValid(""))
    }

    @Test
    fun `whitespace only is invalid`() {
        assertFalse(DeviceName.isValid("   "))
    }

    @Test
    fun `single character is valid`() {
        assertTrue(DeviceName.isValid("A"))
    }

    @Test
    fun `63 characters are valid`() {
        assertTrue(DeviceName.isValid("a".repeat(63)))
    }

    @Test
    fun `64 characters are invalid`() {
        assertFalse(DeviceName.isValid("a".repeat(64)))
    }

    @Test
    fun `name with newline is invalid`() {
        assertFalse(DeviceName.isValid("Nursery\nName"))
    }

    @Test
    fun `name with carriage return is invalid`() {
        assertFalse(DeviceName.isValid("Nursery\rName"))
    }

    @Test
    fun `normal name is valid`() {
        assertTrue(DeviceName.isValid("Nursery"))
        assertTrue(DeviceName.isValid("Living Room"))
        assertTrue(DeviceName.isValid("Kinderzimmer"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("Nursery", DeviceName.normalize("  Nursery  "))
    }

    @Test
    fun `normalize empty returns empty`() {
        assertEquals("", DeviceName.normalize(""))
        assertEquals("", DeviceName.normalize("   "))
    }

    @Test
    fun `isDefaultName returns true for blank string`() {
        assertTrue(DeviceName.isDefaultName(""))
        assertTrue(DeviceName.isDefaultName("   "))
    }

    @Test
    fun `isDefaultName returns false for non-blank string`() {
        assertFalse(DeviceName.isDefaultName("Nursery"))
    }

    @Test
    fun `max length constant is 63`() {
        assertEquals(63, DeviceName.MAX_LENGTH)
    }

    @Test
    fun `min length constant is 1`() {
        assertEquals(1, DeviceName.MIN_LENGTH)
    }
}