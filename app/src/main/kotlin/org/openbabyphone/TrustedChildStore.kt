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

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrustedChild(
    val childId: String,
    val pairingId: String,
    val displayName: String,
    val lastKnownAddress: String? = null,
    val lastKnownPort: Int? = null,
    val lastSeenAt: Long = 0L
) {
    fun matchesPairing(otherPairingId: String): Boolean = pairingId == otherPairingId
}

sealed interface TrustedConnectionResult {
    data class Available(val child: TrustedChild, val pairingCode: CharArray) : TrustedConnectionResult
    data object Missing : TrustedConnectionResult
    data object Unavailable : TrustedConnectionResult
}

enum class CredentialStorageResult {
    Success,
    Unavailable,
    Failed
}

/** Authoritative parent-side store for trusted metadata and protected credentials. */
class TrustedChildStore(
    context: Context,
    crypto: TrustedCredentialCrypto = AndroidKeystoreTrustedCredentialCrypto()
) {
    private val appContext = context.applicationContext
    private val metadataPrefs = appContext.getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE)
    private val credentials = ProtectedTrustedCredentialStore(
        appContext.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, Context.MODE_PRIVATE),
        crypto
    )

    init {
        synchronized(STORE_LOCK) { reconcileLocked() }
    }

    fun getAll(): List<TrustedChild> = synchronized(STORE_LOCK) { reconcileLocked().toList() }

    fun findById(childId: String): TrustedChild? = synchronized(STORE_LOCK) {
        reconcileLocked().firstOrNull { it.childId == childId }
    }

    fun resolveConnection(childId: String, pairingId: String): TrustedConnectionResult =
        synchronized(STORE_LOCK) {
            val profiles = reconcileLocked().toMutableList()
            val child = profiles.firstOrNull { it.childId == childId && it.pairingId == pairingId }
                ?: return@synchronized TrustedConnectionResult.Missing
            when (val result = credentials.get(childId, pairingId)) {
                is CredentialReadResult.Available -> TrustedConnectionResult.Available(child, result.value)
                CredentialReadResult.Unavailable -> TrustedConnectionResult.Unavailable
                CredentialReadResult.Missing,
                CredentialReadResult.Corrupt -> {
                    profiles.removeAll { it.childId == childId && it.pairingId == pairingId }
                    if (persistMetadataLocked(profiles)) credentials.remove(childId, pairingId)
                    TrustedConnectionResult.Missing
                }
            }
        }

    /** Stores or rotates trust only after the caller has completed mutual authentication. */
    fun trustAuthenticated(
        childId: String,
        pairingId: String,
        displayName: String,
        pairingCode: CharArray,
        address: String,
        port: Int
    ): CredentialStorageResult {
        if (childId.isBlank() || pairingId.isBlank() || address.isBlank() || port !in 1..65535) {
            return CredentialStorageResult.Failed
        }
        return synchronized(STORE_LOCK) {
            val profiles = reconcileLocked().toMutableList()
            val old = profiles.firstOrNull { it.childId == childId }
            when (credentials.put(childId, pairingId, pairingCode)) {
                CredentialWriteResult.Unavailable -> return@synchronized CredentialStorageResult.Unavailable
                CredentialWriteResult.Failed -> return@synchronized CredentialStorageResult.Failed
                CredentialWriteResult.Success -> Unit
            }
            val replacement = TrustedChild(
                childId = childId,
                pairingId = pairingId,
                displayName = displayName,
                lastKnownAddress = address,
                lastKnownPort = port,
                lastSeenAt = System.currentTimeMillis()
            )
            profiles.removeAll { it.childId == childId }
            profiles.add(replacement)
            if (!persistMetadataLocked(profiles)) {
                if (old?.pairingId != pairingId) credentials.remove(childId, pairingId)
                return@synchronized CredentialStorageResult.Failed
            }
            if (old != null && old.pairingId != pairingId) credentials.remove(childId, old.pairingId)
            CredentialStorageResult.Success
        }
    }

    fun updateLastKnownAuthenticated(childId: String, pairingId: String, address: String, port: Int): Boolean {
        if (address.isBlank() || port !in 1..65535) return false
        return synchronized(STORE_LOCK) {
            val profiles = reconcileLocked().toMutableList()
            val index = profiles.indexOfFirst { it.childId == childId && it.pairingId == pairingId }
            if (index < 0) return@synchronized false
            profiles[index] = profiles[index].copy(
                lastKnownAddress = address,
                lastKnownPort = port,
                lastSeenAt = System.currentTimeMillis()
            )
            persistMetadataLocked(profiles)
        }
    }

    fun forget(childId: String): Boolean {
        val deleted = synchronized(STORE_LOCK) {
            val profiles = reconcileLocked().toMutableList()
            val matching = profiles.filter { it.childId == childId }
            if (matching.isEmpty()) return@synchronized true
            profiles.removeAll(matching)
            if (!persistMetadataLocked(profiles)) {
                false
            } else {
                var credentialsDeleted = true
                matching.forEach {
                    credentialsDeleted = credentials.remove(it.childId, it.pairingId) && credentialsDeleted
                }
                if (!credentialsDeleted) credentialsDeleted = credentials.clear()
                credentialsDeleted
            }
        }
        PendingConnections.store.removeForChild(childId)
        ActiveListenSessionRegistry.revoke(appContext, childId)
        return deleted
    }

    fun clear(): Boolean {
        val credentialsDeleted = synchronized(STORE_LOCK) {
            metadataPrefs.edit().remove(KEY_TRUSTED_CHILDREN).commit() && credentials.clear()
        }
        PendingConnections.store.clear()
        ActiveListenSessionRegistry.revokeAll(appContext)
        return credentialsDeleted
    }

    private fun reconcileLocked(): List<TrustedChild> {
        val raw = metadataPrefs.getString(KEY_TRUSTED_CHILDREN, null) ?: run {
            credentials.clear()
            return emptyList()
        }
        val loaded = mutableListOf<TrustedChild>()
        var changed = false
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index)
                if (value == null) {
                    changed = true
                    continue
                }
                val child = trustedChildFromJson(value)
                if (child == null) {
                    changed = true
                    continue
                }
                val plaintext = value.optString(LEGACY_PAIRING_CODE_KEY, "")
                val usable = if (plaintext.isNotEmpty()) {
                    changed = true
                    val chars = plaintext.toCharArray()
                    try {
                        credentials.put(child.childId, child.pairingId, chars) == CredentialWriteResult.Success
                    } finally {
                        chars.fill('\u0000')
                    }
                } else {
                    when (val result = credentials.get(child.childId, child.pairingId)) {
                        is CredentialReadResult.Available -> {
                            result.value.fill('\u0000')
                            true
                        }
                        CredentialReadResult.Unavailable -> true
                        CredentialReadResult.Missing,
                        CredentialReadResult.Corrupt -> {
                            changed = true
                            credentials.remove(child.childId, child.pairingId)
                            false
                        }
                    }
                }
                if (usable) {
                    if (loaded.removeAll { it.childId == child.childId }) changed = true
                    loaded.add(child)
                }
            }
        } catch (_: Exception) {
            credentials.clear()
            metadataPrefs.edit().remove(KEY_TRUSTED_CHILDREN).commit()
            return emptyList()
        }
        credentials.retain(loaded.map { it.childId to it.pairingId })
        if (changed && !persistMetadataLocked(loaded)) {
            credentials.clear()
            metadataPrefs.edit().remove(KEY_TRUSTED_CHILDREN).commit()
            return emptyList()
        }
        return loaded
    }

    private fun persistMetadataLocked(children: List<TrustedChild>): Boolean {
        val array = JSONArray()
        children.forEach { array.put(it.toJson()) }
        return metadataPrefs.edit().putString(KEY_TRUSTED_CHILDREN, array.toString()).commit()
    }

    companion object {
        internal const val METADATA_PREFS_NAME = "trusted_children"
        internal const val KEY_TRUSTED_CHILDREN = "trustedChildren"
        internal const val LEGACY_PAIRING_CODE_KEY = "pairingCode"
        private val STORE_LOCK = Any()
    }
}

private fun TrustedChild.toJson(): JSONObject = JSONObject().apply {
    put("childId", childId)
    put("pairingId", pairingId)
    put("displayName", displayName)
    put("lastKnownAddress", lastKnownAddress ?: JSONObject.NULL)
    put("lastKnownPort", lastKnownPort ?: JSONObject.NULL)
    put("lastSeenAt", lastSeenAt)
}

private fun trustedChildFromJson(value: JSONObject): TrustedChild? = try {
    val childId = value.getString("childId").takeIf { it.isNotBlank() } ?: return null
    val pairingId = value.getString("pairingId").takeIf { it.isNotBlank() } ?: return null
    TrustedChild(
        childId = childId,
        pairingId = pairingId,
        displayName = value.optString("displayName", ""),
        lastKnownAddress = if (value.isNull("lastKnownAddress")) null else value.optString("lastKnownAddress"),
        lastKnownPort = if (value.isNull("lastKnownPort")) null else value.optInt("lastKnownPort", -1)
            .takeIf { it in 1..65535 },
        lastSeenAt = value.optLong("lastSeenAt", 0L)
    )
} catch (_: Exception) {
    null
}
