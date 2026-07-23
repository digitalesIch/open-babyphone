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
package org.openbabyphone.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPolicyTest {
    @Test
    fun `monitor policy keeps only running and recoverable advertising states active`() {
        val activeStates = listOf(
            MonitorSessionState.Starting,
            MonitorSessionState.WaitingForParent,
            MonitorSessionState.Connected(1),
            MonitorSessionState.NoNetwork,
            MonitorSessionState.Error(MonitorSessionError.Advertising, "advertising")
        )
        val inactiveStates = listOf(
            MonitorSessionState.Setup,
            MonitorSessionState.Stopped,
            MonitorSessionState.Error(MonitorSessionError.Startup, "startup"),
            MonitorSessionState.Error(MonitorSessionError.Authentication, "authentication"),
            MonitorSessionState.Error(MonitorSessionError.AudioCapture, "capture"),
            MonitorSessionState.Error(MonitorSessionError.AudioEncoding, "encoding")
        )

        activeStates.forEach { assertTrue("Expected $it to be active", it.isAuthoritativelyActive()) }
        inactiveStates.forEach { assertFalse("Expected $it to be inactive", it.isAuthoritativelyActive()) }
    }

    @Test
    fun `monitor heartbeat requires user action after terminal microphone failure`() {
        assertFalse(
            MonitorSessionState.Error(MonitorSessionError.AudioCapture, "failed")
                .allowsHeartbeatRecovery()
        )
        assertFalse(MonitorSessionState.Stopped.allowsHeartbeatRecovery())
        assertTrue(MonitorSessionState.Setup.allowsHeartbeatRecovery())
        assertTrue(
            MonitorSessionState.Error(MonitorSessionError.Advertising, "failed")
                .allowsHeartbeatRecovery()
        )
    }

    @Test
    fun `listen policy excludes idle and terminal states`() {
        val activeStates = listOf(
            ListenSessionState.Connecting,
            ListenSessionState.Listening,
            ListenSessionState.Reconnecting(1, 3),
            ListenSessionState.Disrupted
        )
        val inactiveStates = listOf(
            ListenSessionState.Idle,
            ListenSessionState.Lost,
            ListenSessionState.Error(ListenSessionError.Unreachable, "failed"),
            ListenSessionState.Stopped
        )

        activeStates.forEach { assertTrue("Expected $it to be active", it.isAuthoritativelyActive()) }
        inactiveStates.forEach { assertFalse("Expected $it to be inactive", it.isAuthoritativelyActive()) }
    }

    @Test
    fun `heartbeat never revives terminal listen states`() {
        assertFalse(ListenSessionState.Lost.allowsHeartbeatRecovery())
        assertFalse(
            ListenSessionState.Error(ListenSessionError.Unreachable, "failed")
                .allowsHeartbeatRecovery()
        )
        assertFalse(ListenSessionState.Stopped.allowsHeartbeatRecovery())
        assertTrue(ListenSessionState.Idle.allowsHeartbeatRecovery())
    }
}
