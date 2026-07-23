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

fun MonitorSessionState.isAuthoritativelyActive(): Boolean = when (this) {
    MonitorSessionState.Starting,
    MonitorSessionState.WaitingForParent,
    is MonitorSessionState.Connected,
    MonitorSessionState.NoNetwork -> true
    is MonitorSessionState.Error -> type == MonitorSessionError.Advertising
    MonitorSessionState.Setup,
    MonitorSessionState.Stopped -> false
}

fun MonitorSessionState.allowsHeartbeatRecovery(): Boolean = when (this) {
    is MonitorSessionState.Error -> type == MonitorSessionError.Advertising
    MonitorSessionState.Stopped -> false
    else -> true
}

fun ListenSessionState.isAuthoritativelyActive(): Boolean = when (this) {
    ListenSessionState.Connecting,
    ListenSessionState.Listening,
    is ListenSessionState.Reconnecting,
    ListenSessionState.Disrupted -> true
    ListenSessionState.Idle,
    ListenSessionState.Lost,
    is ListenSessionState.Error,
    ListenSessionState.Stopped -> false
}

fun ListenSessionState.allowsHeartbeatRecovery(): Boolean = when (this) {
    ListenSessionState.Lost,
    is ListenSessionState.Error,
    ListenSessionState.Stopped -> false
    else -> true
}
