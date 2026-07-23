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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.openbabyphone.navigation.Listen
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.isAuthoritativelyActive

class ListenResumeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getLongExtra(EXTRA_SESSION_TOKEN, INVALID_SESSION_TOKEN)
        val registered = ActiveListenSessionRegistry.resolve(token)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val route = registered?.let { session ->
            if (session.active && ListenServiceRepository.sessionState.value.isAuthoritativelyActive()) {
                Listen(resumeOnly = true)
            } else {
                buildRetryRoute(session)
            }
        }
        route?.let {
            mainIntent.putExtra(
                MainActivity.EXTRA_INTERNAL_ROUTE_ID,
                InternalListenRouteRegistry.put(it)
            )
        }
        startActivity(mainIntent)
        finish()
    }

    private fun buildRetryRoute(session: ActiveListenSessionRegistry.RouteSession): Listen? {
        val existingRequest = session.requestId?.takeIf(PendingConnections.store::contains)
        if (existingRequest != null) {
            return Listen(
                requestId = existingRequest,
                expectedChildId = session.identity?.childId.orEmpty(),
                expectedPairingId = session.identity?.pairingId.orEmpty()
            )
        }
        val identity = session.identity ?: return null
        val child = trustedChildStore().findById(identity.childId)
            ?.takeIf { it.pairingId == identity.pairingId }
            ?: return null
        val address = child.lastKnownAddress ?: return null
        val port = child.lastKnownPort ?: return null
        val requestId = PendingConnections.store.put(
            PendingConnection(
                address = address,
                port = port,
                name = child.displayName,
                pairingCode = null,
                expectedChildId = identity.childId,
                expectedPairingId = identity.pairingId
            )
        )
        return Listen(requestId, identity.childId, identity.pairingId)
    }

    companion object {
        const val EXTRA_SESSION_TOKEN = "org.openbabyphone.extra.LISTEN_SESSION_TOKEN"
        private const val INVALID_SESSION_TOKEN = Long.MIN_VALUE
    }
}
