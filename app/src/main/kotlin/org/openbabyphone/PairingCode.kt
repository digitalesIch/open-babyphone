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

object PairingCode {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    private val PATTERN = Regex("[A-Za-z0-9]{$MIN_LENGTH,$MAX_LENGTH}")

    fun isValid(code: String): Boolean {
        val trimmed = code.trim()
        return PATTERN.matches(trimmed)
    }

    fun normalize(code: String): String {
        return code.trim()
    }
}
