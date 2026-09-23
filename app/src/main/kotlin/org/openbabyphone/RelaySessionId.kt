package org.openbabyphone

import java.security.MessageDigest

object RelaySessionId {
    private const val PREFIX = "open-babyphone-relay-v1:"
    private const val HEX = "0123456789abcdef"

    fun derive(childId: String, pairingId: String): String {
        require(childId.isNotBlank() && pairingId.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$PREFIX$childId:$pairingId".toByteArray(Charsets.UTF_8))
        val out = CharArray(digest.size * 2)
        digest.forEachIndexed { index, value ->
            val unsigned = value.toInt() and 0xff
            out[index * 2] = HEX[unsigned ushr 4]
            out[index * 2 + 1] = HEX[unsigned and 0x0f]
        }
        return String(out)
    }
}
