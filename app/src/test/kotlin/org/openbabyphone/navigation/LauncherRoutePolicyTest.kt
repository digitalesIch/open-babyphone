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
package org.openbabyphone.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState

class LauncherRoutePolicyTest {
    @Test
    fun `active monitor routes to monitor`() {
        assertEquals(
            LauncherDestination.Monitor,
            launcherDestination(MonitorSessionState.WaitingForParent, ListenSessionState.Idle)
        )
    }

    @Test
    fun `active listen routes to resume listen`() {
        listOf(
            ListenSessionState.Connecting,
            ListenSessionState.Listening,
            ListenSessionState.Reconnecting(1, 5),
            ListenSessionState.Disrupted
        ).forEach { state ->
            assertEquals(
                LauncherDestination.Listen,
                launcherDestination(MonitorSessionState.Stopped, state)
            )
        }
    }

    @Test
    fun `terminal states route to role selection`() {
        assertEquals(
            LauncherDestination.Start,
            launcherDestination(
                MonitorSessionState.Error(MonitorSessionError.AudioCapture, "failed"),
                ListenSessionState.Error(ListenSessionError.Playback, "failed")
            )
        )
    }

    @Test
    fun `stopped sessions route to role selection`() {
        assertEquals(
            LauncherDestination.Start,
            launcherDestination(MonitorSessionState.Stopped, ListenSessionState.Stopped)
        )
    }

    @Test
    fun `lost listen session routes to role selection`() {
        assertEquals(
            LauncherDestination.Start,
            launcherDestination(MonitorSessionState.Stopped, ListenSessionState.Lost)
        )
    }

    @Test
    fun `monitor takes precedence if both repositories report active sessions`() {
        assertEquals(
            LauncherDestination.Monitor,
            launcherDestination(
                MonitorSessionState.Error(MonitorSessionError.Advertising, "failed"),
                ListenSessionState.Connecting
            )
        )
    }
}
