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

import java.security.SecureRandom

/**
 * Generates strong alphanumeric pairing codes for safe default pairing.
 *
 * The generated codes use an unambiguous character set (no 0/O/1/I/l)
 * to reduce transcription errors when parents copy the code manually.
 * The code length provides sufficient entropy for local-network pairing.
 */
object PairingCodeGenerator {
    private val UNAMBIGUOUS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    private val random = SecureRandom()

    const val DEFAULT_CODE_LENGTH = 8

    fun generate(length: Int = DEFAULT_CODE_LENGTH): String {
        require(length in PairingCode.MIN_LENGTH..PairingCode.MAX_LENGTH) {
            "Code length must be between ${PairingCode.MIN_LENGTH} and ${PairingCode.MAX_LENGTH}"
        }
        val buffer = CharArray(length)
        for (i in buffer.indices) {
            buffer[i] = UNAMBIGUOUS_CHARS[random.nextInt(UNAMBIGUOUS_CHARS.size)]
        }
        return String(buffer)
    }

    fun generateIfInvalid(existingCode: String): String {
        val trimmed = existingCode.trim()
        if (PairingCode.isValid(trimmed)) {
            return trimmed
        }
        return generate()
    }
}
