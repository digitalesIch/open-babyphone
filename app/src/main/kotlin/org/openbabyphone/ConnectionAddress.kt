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
}