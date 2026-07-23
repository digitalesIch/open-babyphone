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

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PairingSettingsTest {
    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(ChildDeviceIdentityStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `reset policy blocks every active monitor state`() {
        assertEquals(
            PairingResetAvailability.StopMonitoringFirst,
            pairingResetAvailability(MonitorSessionState.WaitingForParent)
        )
        assertEquals(
            PairingResetAvailability.StopMonitoringFirst,
            pairingResetAvailability(
                MonitorSessionState.Error(MonitorSessionError.Advertising, "failed")
            )
        )
        assertEquals(
            PairingResetAvailability.Allowed,
            pairingResetAvailability(
                MonitorSessionState.Error(MonitorSessionError.Authentication, "failed")
            )
        )
        assertEquals(
            PairingResetAvailability.Allowed,
            pairingResetAvailability(MonitorSessionState.Stopped)
        )
    }

    @Test
    fun `reset persists a strong new code and rotates only pairing generation`() {
        val pairingPrefs = context.getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val oldPairingId = "abcdefghijklmnop"
        pairingPrefs.edit()
            .putString(MonitorService.PREF_KEY_PAIRING_CODE, "23456789")
            .putString(PairingSettings.PREF_KEY_PAIRING_ID, oldPairingId)
            .commit()
        val identityStore = ChildDeviceIdentityStore(context)
        val oldIdentity = identityStore.identity

        PairingSettings.reset(context) { "ABCDEFGH" }

        val newIdentity = identityStore.identity
        assertEquals("ABCDEFGH", pairingPrefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, null))
        assertEquals(oldIdentity.childId, newIdentity.childId)
        assertNotEquals(oldIdentity.pairingId, newIdentity.pairingId)
        assertEquals(newIdentity.pairingId, pairingPrefs.getString(PairingSettings.PREF_KEY_PAIRING_ID, null))
    }

    @Test
    fun `legacy pairing identity migrates into pairing preferences`() {
        val identityPrefs = context.getSharedPreferences(ChildDeviceIdentityStore.PREFS_NAME, Context.MODE_PRIVATE)
        identityPrefs.edit()
            .putString(ChildDeviceIdentityStore.LEGACY_KEY_PAIRING_ID, "abcdefghijklmnop")
            .commit()

        val state = PairingSettings.load(context) { "ABCDEFGH" }

        assertEquals("abcdefghijklmnop", state.pairingId)
        assertEquals(
            "abcdefghijklmnop",
            context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PairingSettings.PREF_KEY_PAIRING_ID, null)
        )
        assertFalse(identityPrefs.contains(ChildDeviceIdentityStore.LEGACY_KEY_PAIRING_ID))
    }

    @Test
    fun `invalid stored code replacement atomically rotates pairing generation`() {
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(MonitorService.PREF_KEY_PAIRING_CODE, "invalid-code")
            .putString(PairingSettings.PREF_KEY_PAIRING_ID, "abcdefghijklmnop")
            .commit()

        val state = PairingSettings.load(context) { "ABCDEFGH" }

        assertEquals("ABCDEFGH", state.pairingCode)
        assertNotEquals("abcdefghijklmnop", state.pairingId)
        assertEquals("ABCDEFGH", prefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, null))
        assertEquals(state.pairingId, prefs.getString(PairingSettings.PREF_KEY_PAIRING_ID, null))
    }

    @Test
    fun `concurrent first load produces one complete pairing generation`() {
        val results = java.util.Collections.synchronizedList(mutableListOf<ChildPairingState>())
        val threads = List(8) {
            Thread { results += PairingSettings.load(context) { "ABCDEFGH" } }
        }

        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(8, results.size)
        assertEquals(1, results.map { it.pairingCode to it.pairingId }.toSet().size)
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.contains(MonitorService.PREF_KEY_PAIRING_CODE))
        assertTrue(prefs.contains(PairingSettings.PREF_KEY_PAIRING_ID))
    }
}
