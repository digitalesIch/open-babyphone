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

class PairingCodeGeneratorTest {

    @Test
    fun generate_DefaultLength_Is8() {
        val code = PairingCodeGenerator.generate()
        assertEquals(PairingCodeGenerator.DEFAULT_CODE_LENGTH, code.length)
    }

    @Test
    fun generate_CustomLength() {
        val code = PairingCodeGenerator.generate(12)
        assertEquals(12, code.length)
    }

    @Test
    fun generate_IsValidPairingCode() {
        for (i in 0 until 100) {
            val code = PairingCodeGenerator.generate()
            assertTrue("Generated code should be valid: $code", PairingCode.isValid(code))
        }
    }

    @Test
    fun generate_OnlyUsesUnambiguousChars() {
        val ambiguous = setOf('0', 'O', '1', 'I', 'l')
        for (i in 0 until 100) {
            val code = PairingCodeGenerator.generate()
            for (c in code) {
                assertFalse("Code contains ambiguous char '$c': $code", c in ambiguous)
            }
        }
    }

    @Test
    fun generate_DifferentCalls_ProduceDifferentCodes() {
        val codes = mutableSetOf<String>()
        for (i in 0 until 100) {
            codes.add(PairingCodeGenerator.generate())
        }
        assertTrue("Generated codes should be unique", codes.size > 95)
    }

    @Test
    fun generate_EmptyString_NotProduced() {
        for (i in 0 until 100) {
            val code = PairingCodeGenerator.generate()
            assertTrue(code.isNotEmpty())
        }
    }

    @Test
    fun generate_OnlyAlphanumeric() {
        for (i in 0 until 100) {
            val code = PairingCodeGenerator.generate()
            assertTrue("Code should be alphanumeric: $code", code.all { it.isLetterOrDigit() })
        }
    }

    @Test
    fun generateIfEmpty_EmptyInput_GeneratesCode() {
        val result = PairingCodeGenerator.generateIfEmpty("")
        assertTrue(result.isNotEmpty())
        assertTrue(PairingCode.isValid(result))
    }

    @Test
    fun generateIfEmpty_WhitespaceInput_GeneratesCode() {
        val result = PairingCodeGenerator.generateIfEmpty("   ")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun generateIfEmpty_NonEmptyInput_ReturnsTrimmed() {
        val result = PairingCodeGenerator.generateIfEmpty("  existingCode  ")
        assertEquals("existingCode", result)
    }

    @Test
    fun generateIfEmpty_NonEmptyInput_DoesNotGenerate() {
        val existing = "mypairingcode"
        val result = PairingCodeGenerator.generateIfEmpty(existing)
        assertEquals(existing, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_TooShort_Throws() {
        PairingCodeGenerator.generate(3)
    }

    @Test
    fun generate_MinimumLength4_IsValid() {
        val code = PairingCodeGenerator.generate(4)
        assertEquals(4, code.length)
        assertTrue(PairingCode.isValid(code))
    }
}