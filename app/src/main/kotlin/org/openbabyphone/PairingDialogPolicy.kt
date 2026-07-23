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

import org.openbabyphone.service.MonitorSessionState

object PairingDialogPolicy {
    fun shouldAutoOpen(
        sessionState: MonitorSessionState,
        pairingReady: Boolean,
        wasAutoOpened: Boolean
    ): Boolean = sessionState is MonitorSessionState.WaitingForParent && pairingReady && !wasAutoOpened

    fun shouldDismissAutoOpened(parentCount: Int, openedAutomatically: Boolean): Boolean =
        parentCount > 0 && openedAutomatically
}
