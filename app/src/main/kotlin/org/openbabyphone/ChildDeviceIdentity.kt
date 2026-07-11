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
import java.security.SecureRandom

/**
 * Persistent identity for the child device.
 *
 * - [childId] is a stable, random identifier that survives pairing resets.
 *   It allows the parent to recognise the same physical device across
 *   sessions.
 * - [pairingId] identifies the current pairing generation. It is rotated
 *   whenever the pairing code is reset or changed, so the parent can detect
 *   that a previously known child must be re-paired.
 */
data class ChildDeviceIdentity(
    val childId: String,
    val pairingId: String
) {
    companion object {
        private const val ID_LENGTH = 16
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        private val random = SecureRandom()

        fun generateId(): String {
            val sb = StringBuilder(ID_LENGTH)
            repeat(ID_LENGTH) {
                sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
            return sb.toString()
        }

        fun generate(): ChildDeviceIdentity {
            return ChildDeviceIdentity(generateId(), generateId())
        }
    }
}

/**
 * Loads and persists the [ChildDeviceIdentity] in SharedPreferences.
 *
 * The [childId] is generated once and never changes. The [pairingId] is
 * rotated via [rotatePairingId] when the pairing code is reset or changed.
 */
class ChildDeviceIdentityStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the current [ChildDeviceIdentity], reading the persisted values
     * each time. The [childId] is generated once on first access; the
     * [pairingId] may change after [rotatePairingId] is called.
     */
    val identity: ChildDeviceIdentity
        get() {
            var childId = prefs.getString(KEY_CHILD_ID, null)
            if (childId == null) {
                childId = ChildDeviceIdentity.generateId()
                prefs.edit().putString(KEY_CHILD_ID, childId).apply()
            }
            var pairingId = prefs.getString(KEY_PAIRING_ID, null)
            if (pairingId == null) {
                pairingId = ChildDeviceIdentity.generateId()
                prefs.edit().putString(KEY_PAIRING_ID, pairingId).apply()
            }
            return ChildDeviceIdentity(childId, pairingId)
        }

    /**
     * Rotates the [pairingId], keeping the [childId] stable. Called when the
     * pairing code is reset or changed on the child side.
     */
    fun rotatePairingId() {
        val newPairingId = ChildDeviceIdentity.generateId()
        prefs.edit().putString(KEY_PAIRING_ID, newPairingId).apply()
    }

    companion object {
        private const val PREFS_NAME = "child_identity"
        private const val KEY_CHILD_ID = "childId"
        private const val KEY_PAIRING_ID = "pairingId"
    }
}