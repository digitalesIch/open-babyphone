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

object DeviceName {
    const val MIN_LENGTH = 1
    const val MAX_LENGTH = 63

    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) {
            return false
        }
        if (trimmed.contains('\n') || trimmed.contains('\r')) {
            return false
        }
        return true
    }

    fun normalize(name: String): String {
        return name.trim()
    }

    fun isDefaultName(name: String): Boolean {
        return name.isBlank()
    }
}