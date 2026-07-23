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

        internal fun isValidId(value: String?): Boolean =
            value?.length == ID_LENGTH && value.all(ALPHABET::contains)
    }
}

/**
 * Loads and persists the [ChildDeviceIdentity] in SharedPreferences.
 *
 * The [childId] is generated once and never changes. The [pairingId] is
 * stored atomically with the pairing code and rotates when that code changes.
 */
class ChildDeviceIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the current [ChildDeviceIdentity], reading the persisted values
     * each time. The [childId] is generated once on first access; the
     * [pairingId] may change after the pairing settings are reset.
     */
    val identity: ChildDeviceIdentity
        get() {
            val childId = synchronized(IDENTITY_LOCK) {
                prefs.getString(KEY_CHILD_ID, null)
                    ?.takeIf(ChildDeviceIdentity::isValidId)
                    ?: ChildDeviceIdentity.generateId().also {
                        check(prefs.edit().putString(KEY_CHILD_ID, it).commit()) {
                            "Failed to persist child identity"
                        }
                    }
            }
            return ChildDeviceIdentity(childId, PairingSettings.load(appContext).pairingId)
        }

    companion object {
        internal const val PREFS_NAME = "child_identity"
        private const val KEY_CHILD_ID = "childId"
        internal const val LEGACY_KEY_PAIRING_ID = "pairingId"
        private val IDENTITY_LOCK = Any()
    }
}
