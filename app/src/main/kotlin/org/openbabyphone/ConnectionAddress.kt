package org.openbabyphone

object ConnectionAddress {
    fun normalize(input: String): String {
        var result = input.trim()
        val parenIndex = result.indexOf('(')
        if (parenIndex > 0) {
            result = result.substring(0, parenIndex).trim()
        }
        val spaceIndex = result.indexOf(' ')
        if (spaceIndex > 0) {
            result = result.substring(0, spaceIndex).trim()
        }
        return result
    }

    private val ipv4Pattern = Regex(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    )

    private val ipv6Pattern = Regex(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$"
    )

    private val hostnamePattern = Regex(
        "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"
    )

    fun isValidAddress(input: String): Boolean {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return false
        return ipv4Pattern.matches(normalized) ||
            ipv6Pattern.matches(normalized) ||
            (normalized.length <= 253 && hostnamePattern.matches(normalized))
    }
}