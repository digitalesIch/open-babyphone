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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState

class PairingDialogPolicyTest {
    @Test
    fun `auto open requires first usable waiting state`() {
        assertTrue(
            PairingDialogPolicy.shouldAutoOpen(MonitorSessionState.WaitingForParent, true, false)
        )
        assertFalse(PairingDialogPolicy.shouldAutoOpen(MonitorSessionState.Starting, true, false))
        assertFalse(PairingDialogPolicy.shouldAutoOpen(MonitorSessionState.NoNetwork, true, false))
        assertFalse(
            PairingDialogPolicy.shouldAutoOpen(
                MonitorSessionState.Error(MonitorSessionError.Advertising, "failed"),
                true,
                false
            )
        )
        assertFalse(
            PairingDialogPolicy.shouldAutoOpen(MonitorSessionState.WaitingForParent, false, false)
        )
        assertFalse(
            PairingDialogPolicy.shouldAutoOpen(MonitorSessionState.WaitingForParent, true, true)
        )
    }

    @Test
    fun `only automatically opened dialog closes when a parent connects`() {
        assertTrue(PairingDialogPolicy.shouldDismissAutoOpened(1, true))
        assertFalse(PairingDialogPolicy.shouldDismissAutoOpened(0, true))
        assertFalse(PairingDialogPolicy.shouldDismissAutoOpened(1, false))
    }
}
