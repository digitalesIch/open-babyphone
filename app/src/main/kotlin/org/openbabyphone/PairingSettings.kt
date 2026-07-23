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
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.service.isAuthoritativelyActive

enum class PairingResetAvailability {
    Allowed,
    StopMonitoringFirst
}

data class ChildPairingState(val pairingCode: String, val pairingId: String)

fun pairingResetAvailability(state: MonitorSessionState): PairingResetAvailability =
    if (state.isAuthoritativelyActive()) PairingResetAvailability.StopMonitoringFirst
    else PairingResetAvailability.Allowed

object PairingSettings {
    private val lock = Any()

    fun load(
        context: Context,
        generateCode: () -> String = PairingCodeGenerator::generate
    ): ChildPairingState = synchronized(lock) {
        val pairingPrefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
        val identityPrefs = context.getSharedPreferences(ChildDeviceIdentityStore.PREFS_NAME, Context.MODE_PRIVATE)
        val savedCode = pairingPrefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, null)
        val hadCode = pairingPrefs.contains(MonitorService.PREF_KEY_PAIRING_CODE)
        val savedPairingId = pairingPrefs.getString(PREF_KEY_PAIRING_ID, null)
            ?.takeIf(ChildDeviceIdentity::isValidId)
        val hadLegacyPairingId = identityPrefs.contains(ChildDeviceIdentityStore.LEGACY_KEY_PAIRING_ID)
        val legacyPairingId = identityPrefs.getString(ChildDeviceIdentityStore.LEGACY_KEY_PAIRING_ID, null)
            ?.takeIf(ChildDeviceIdentity::isValidId)
        val validCode = savedCode?.let(PairingCode::normalize)?.takeIf(PairingCode::isValid)
        if (validCode != null && savedPairingId != null && savedCode == validCode && !hadLegacyPairingId) {
            return@synchronized ChildPairingState(validCode, savedPairingId)
        }

        val code = validCode ?: generateCode().also {
            require(PairingCode.isValid(it)) { "Pairing code generator returned an invalid code" }
        }
        val pairingId = when {
            validCode == null && hadCode -> ChildDeviceIdentity.generateId()
            savedPairingId != null -> savedPairingId
            legacyPairingId != null -> legacyPairingId
            else -> ChildDeviceIdentity.generateId()
        }
        check(
            pairingPrefs.edit()
                .putString(MonitorService.PREF_KEY_PAIRING_CODE, PairingCode.normalize(code))
                .putString(PREF_KEY_PAIRING_ID, pairingId)
                .commit()
        ) { "Failed to persist atomic pairing settings" }
        if (hadLegacyPairingId) {
            check(identityPrefs.edit().remove(ChildDeviceIdentityStore.LEGACY_KEY_PAIRING_ID).commit()) {
                "Failed to remove migrated pairing identity"
            }
        }
        ChildPairingState(PairingCode.normalize(code), pairingId)
    }

    fun reset(
        context: Context,
        generateCode: () -> String = PairingCodeGenerator::generate
    ): ChildPairingState = synchronized(lock) {
        val newCode = generateCode()
        require(PairingCode.isValid(newCode)) { "Pairing code generator returned an invalid code" }
        val newPairingId = ChildDeviceIdentity.generateId()
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
        check(
            prefs.edit()
                .putString(MonitorService.PREF_KEY_PAIRING_CODE, PairingCode.normalize(newCode))
                .putString(PREF_KEY_PAIRING_ID, newPairingId)
                .commit()
        ) { "Failed to persist atomic pairing reset" }
        ChildPairingState(PairingCode.normalize(newCode), newPairingId)
    }

    internal const val PREF_KEY_PAIRING_ID = "pairingId"
}
