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
import android.content.Intent

object ActiveListenSessionRegistry {
    class RouteSession internal constructor(
        val token: Long,
        val identity: ExpectedChildIdentity?,
        val requestId: String?,
        val active: Boolean
    )

    private data class Session(
        val token: Long,
        val identity: ExpectedChildIdentity?,
        val requestId: String?,
        val active: Boolean
    )
    private var session: Session? = null
    private var nextToken = 0L

    @Synchronized
    fun register(identity: ExpectedChildIdentity?, requestId: String? = null): Long {
        val token = ++nextToken
        session = Session(token, identity, requestId, active = true)
        return token
    }

    @Synchronized
    fun markInactive(token: Long?) {
        if (token != null && session?.token == token) {
            session = session?.copy(active = false)
        }
    }

    @Synchronized
    fun resolve(token: Long): RouteSession? = session
        ?.takeIf { it.token == token }
        ?.let { RouteSession(it.token, it.identity, it.requestId, it.active) }

    @Synchronized
    internal fun clearForTests() {
        session = null
    }

    fun revoke(context: Context, childId: String) {
        val matches = synchronized(this) {
            (session?.identity?.childId == childId).also { if (it) session = null }
        }
        if (matches) stop(context)
    }

    fun revokeAll(context: Context) {
        val hadSession = synchronized(this) { (session != null).also { session = null } }
        if (hadSession) stop(context)
    }

    private fun stop(context: Context) {
        ServiceHeartbeatScheduler.cancelListen(context)
        context.stopService(Intent(context, ListenService::class.java))
    }
}
