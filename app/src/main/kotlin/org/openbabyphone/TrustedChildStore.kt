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

/**
 * A child device the parent has paired with at least once and should remember
 * for quick reconnection without re-scanning the QR code.
 */
data class TrustedChild(
    val childId: String,
    val pairingId: String,
    val displayName: String,
    val pairingCode: String,
    val lastKnownAddress: String? = null,
    val lastKnownPort: Int? = null,
    val lastSeenAt: Long = 0L
) {
    /**
     * Returns `true` when the stored [pairingId] matches the given id,
     * meaning the pairing is still valid.
     */
    fun matchesPairing(otherPairingId: String): Boolean {
        return pairingId == otherPairingId
    }
}

/**
 * Persists [TrustedChild] profiles on the parent device using SharedPreferences
 * and JSON serialisation.
 *
 * All data stays local; no cloud sync or relay is involved.
 */
class TrustedChildStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cached: MutableList<TrustedChild> = mutableListOf()

    init {
        load()
    }

    /**
     * Returns the current list of trusted children.
     */
    fun getAll(): List<TrustedChild> {
        synchronized(cached) {
            return cached.toList()
        }
    }

    /**
     * Returns the trusted child with the given [childId], or `null`.
     */
    fun findById(childId: String): TrustedChild? {
        synchronized(cached) {
            return cached.firstOrNull { it.childId == childId }
        }
    }

    /**
     * Adds or replaces a trusted child profile. If a profile with the same
     * [TrustedChild.childId] already exists, it is overwritten.
     */
    fun upsert(child: TrustedChild) {
        synchronized(cached) {
            cached.removeAll { it.childId == child.childId }
            cached.add(child)
            persist()
        }
    }

    /**
     * Updates [lastKnownAddress], [lastKnownPort] and [lastSeenAt] for the
     * trusted child identified by [childId]. No-op if the child is not known.
     */
    fun updateLastKnown(childId: String, address: String, port: Int) {
        synchronized(cached) {
            val idx = cached.indexOfFirst { it.childId == childId }
            if (idx >= 0) {
                val existing = cached[idx]
                cached[idx] = existing.copy(
                    lastKnownAddress = address,
                    lastKnownPort = port,
                    lastSeenAt = System.currentTimeMillis()
                )
                persist()
            }
        }
    }

    /**
     * Removes the trusted child with the given [childId]. No-op if not found.
     */
    fun forget(childId: String) {
        synchronized(cached) {
            cached.removeAll { it.childId == childId }
            persist()
        }
    }

    /**
     * Removes all trusted children.
     */
    fun clear() {
        synchronized(cached) {
            cached.clear()
            persist()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (child in cached) {
            arr.put(child.toJson())
        }
        prefs.edit().putString(KEY_TRUSTED_CHILDREN, arr.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY_TRUSTED_CHILDREN, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                cached.add(trustedChildFromJson(obj))
            }
        } catch (e: Exception) {
            // Corrupt JSON – start fresh
            prefs.edit().remove(KEY_TRUSTED_CHILDREN).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "trusted_children"
        private const val KEY_TRUSTED_CHILDREN = "trustedChildren"
    }
}

private fun TrustedChild.toJson(): JSONObject {
    return JSONObject().apply {
        put("childId", childId)
        put("pairingId", pairingId)
        put("displayName", displayName)
        put("pairingCode", pairingCode)
        put("lastKnownAddress", lastKnownAddress ?: JSONObject.NULL)
        put("lastKnownPort", lastKnownPort ?: JSONObject.NULL)
        put("lastSeenAt", lastSeenAt)
    }
}

private fun trustedChildFromJson(obj: JSONObject): TrustedChild {
    return TrustedChild(
        childId = obj.getString("childId"),
        pairingId = obj.getString("pairingId"),
        displayName = obj.optString("displayName", ""),
        pairingCode = obj.optString("pairingCode", ""),
        lastKnownAddress = if (obj.isNull("lastKnownAddress")) null else obj.optString("lastKnownAddress"),
        lastKnownPort = if (obj.isNull("lastKnownPort")) null else obj.optInt("lastKnownPort", -1).takeIf { it > 0 },
        lastSeenAt = obj.optLong("lastSeenAt", 0L)
    )
}