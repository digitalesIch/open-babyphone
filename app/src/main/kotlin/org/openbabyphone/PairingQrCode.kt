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

/**
 * Parses the raw text decoded from a scanned QR code into a pairing code
 * suitable for [PairingCode] validation.
 *
 * The current child-side QR encodes the pairing code as plain text.
 * Future formats (e.g. `openbabyphone://pair?code=…`) can be supported here
 * without changing call sites.
 */
object PairingQrCode {

    /**
     * Extracts a candidate pairing code from the given QR content.
     *
     * @return the trimmed candidate code, or `null` if the content is blank
     *     or cannot be parsed into a usable candidate.
     */
    fun parseScannedCode(content: String?): String? {
        if (content.isNullOrBlank()) return null
        return PairingCode.normalize(content)
    }
}