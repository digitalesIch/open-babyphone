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

import java.net.URLEncoder
import java.net.URLDecoder

/**
 * Parses and builds the QR code payload used for trusted child pairing.
 *
 * Two payload formats are supported:
 *
 * 1. **Legacy** – raw pairing code text (e.g. `babyroom42`). This keeps
 *    existing child devices working with newer parent versions and vice
 *    versa. Scanning a legacy QR yields only the pairing code; no trusted
 *    child profile is created.
 *
 * 2. **Structured** – `openbabyphone://pair?childId=…&pairingId=…&name=…&code=…`
 *    The parent stores the child as a known device so future connections do
 *    not require re-scanning.
 */
object PairingQrCode {

    private const val SCHEME = "openbabyphone"
    private const val AUTHORITY = "pair"
    private const val PREFIX = "$SCHEME://$AUTHORITY"
    private const val PARAM_CHILD_ID = "childId"
    private const val PARAM_PAIRING_ID = "pairingId"
    private const val PARAM_NAME = "name"
    private const val PARAM_CODE = "code"

    /**
     * Result of parsing scanned QR content.
     */
    sealed interface ParsedQrCode {
        /**
         * Legacy raw pairing code. The parent can connect but cannot store a
         * trusted child profile.
         */
        data class Legacy(val pairingCode: String) : ParsedQrCode

        /**
         * Structured pairing payload carrying child identity and pairing code.
         */
        data class Structured(
            val childId: String,
            val pairingId: String,
            val name: String,
            val pairingCode: String
        ) : ParsedQrCode
    }

    /**
     * Builds the structured QR payload for the child side.
     */
    fun buildPayload(
        childId: String,
        pairingId: String,
        name: String,
        pairingCode: String
    ): String {
        val params = mutableListOf(
            "$PARAM_CHILD_ID=${encode(childId)}",
            "$PARAM_PAIRING_ID=${encode(pairingId)}",
            "$PARAM_NAME=${encode(name)}",
            "$PARAM_CODE=${encodeCode(pairingCode)}"
        )
        return "$PREFIX?${params.joinToString("&")}"
    }

    /**
     * Extracts pairing information from the given QR content.
     *
     * @return the parsed result, or `null` if the content is blank or cannot
     *     be parsed into a usable candidate.
     */
    fun parse(content: String?): ParsedQrCode? {
        if (content.isNullOrBlank()) return null
        val trimmed = content.trim()

        if (trimmed.startsWith("$SCHEME://")) {
            return parseStructured(trimmed)
        }
        return parseLegacy(trimmed)
    }

    /**
     * Extracts a candidate pairing code from the given QR content.
     *
     * Kept for backward compatibility with existing call sites that only need
     * the pairing code string.
     *
     * @return the trimmed candidate code, or `null` if the content is blank
     *     or cannot be parsed into a usable candidate.
     */
    fun parseScannedCode(content: String?): String? {
        return when (val parsed = parse(content)) {
            is ParsedQrCode.Legacy -> parsed.pairingCode
            is ParsedQrCode.Structured -> parsed.pairingCode
            null -> null
        }
    }

    private fun parseStructured(content: String): ParsedQrCode.Structured? {
        val queryPart = content.substringAfter("?", "")
        if (queryPart.isBlank()) return null

        val params = parseQueryParams(queryPart)
        val childId = params[PARAM_CHILD_ID]?.takeIf { it.isNotBlank() } ?: return null
        val pairingId = params[PARAM_PAIRING_ID]?.takeIf { it.isNotBlank() } ?: return null
        val name = params[PARAM_NAME].orEmpty()
        val code = params[PARAM_CODE]?.let(::decodeCode).orEmpty()
        return ParsedQrCode.Structured(childId, pairingId, name, code)
    }

    private fun parseLegacy(content: String): ParsedQrCode.Legacy {
        return ParsedQrCode.Legacy(PairingCode.normalize(content))
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&")
            .filter { it.isNotBlank() }
            .associate { pair ->
                val idx = pair.indexOf("=")
                if (idx < 0) {
                    decode(pair) to ""
                } else {
                    decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
                }
            }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, "UTF-8")
    }

    /**
     * Base64-encodes the pairing code so special characters in query strings
     * do not break parsing.
     */
    private fun encodeCode(code: String): String {
        return java.util.Base64.getEncoder().withoutPadding().encodeToString(
            code.toByteArray(Charsets.UTF_8)
        )
    }

    private fun decodeCode(encoded: String): String {
        return try {
            String(
                java.util.Base64.getDecoder().decode(encoded),
                Charsets.UTF_8
            )
        } catch (e: IllegalArgumentException) {
            encoded
        }
    }
}