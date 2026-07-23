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

import java.security.SecureRandom
import java.util.Base64
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingConnection(
    val address: String = "",
    val port: Int = 0,
    val name: String,
    val pairingCode: CharArray?,
    val expectedChildId: String? = null,
    val expectedPairingId: String? = null,
    val rememberAfterAuthentication: Boolean = false
) {
    init {
        require((address.isBlank()) == (port == 0))
        require(port == 0 || port in 1..65535)
        require(pairingCode == null || PairingCode.isValid(pairingCode.concatToString()))
        require((expectedChildId == null) == (expectedPairingId == null))
        require(!rememberAfterAuthentication || expectedChildId != null)
    }

    internal fun wipeCredential() {
        pairingCode?.fill('\u0000')
    }
}

class PendingConnectionStore internal constructor(
    private val maxEntries: Int = MAX_ENTRIES,
    private val lifetimeMillis: Long = REQUEST_LIFETIME_MS,
    private val elapsedRealtime: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val requestIdFactory: () -> String = ::newRequestId,
    private val scheduleExpiration: (Long, () -> Unit) -> Unit = ::scheduleExpiration
) {
    private data class Entry(val connection: PendingConnection, val expiresAt: Long)

    private val entries = LinkedHashMap<String, Entry>()
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        require(maxEntries > 0)
        require(lifetimeMillis > 0)
    }

    @Synchronized
    fun put(connection: PendingConnection): String {
        removeExpired()
        while (entries.size >= maxEntries) {
            removeEntry(entries.keys.first())
        }
        var requestId: String
        do {
            requestId = requestIdFactory()
        } while (entries.containsKey(requestId))
        val expiresAt = elapsedRealtime() + lifetimeMillis
        entries[requestId] = Entry(connection, expiresAt)
        publishChange()
        scheduleExpiration(lifetimeMillis) { expire(requestId, expiresAt) }
        return requestId
    }

    /** Leases a request without consuming it so connection retries can reuse it. */
    @Synchronized
    fun lease(requestId: String): PendingConnection? {
        removeExpired()
        return entries[requestId]?.connection
    }

    @Synchronized
    fun complete(
        requestId: String,
        address: String,
        port: Int,
        name: String,
        childId: String?,
        pairingId: String?
    ): String? {
        removeExpired()
        if (address.isBlank() || port !in 1..65535) return null
        val entry = entries[requestId] ?: return null
        val connection = entry.connection
        if (connection.expectedChildId != null &&
            (connection.expectedChildId != childId || connection.expectedPairingId != pairingId)
        ) {
            return null
        }
        entries[requestId] = entry.copy(
            connection = connection.copy(address = address, port = port, name = name)
        )
        publishChange()
        return requestId
    }

    /** Adds an endpoint while retaining the credential and expected identity in this store. */
    @Synchronized
    fun completeEndpoint(requestId: String, address: String, port: Int, name: String): String? {
        removeExpired()
        if (address.isBlank() || port !in 1..65535) return null
        val entry = entries[requestId] ?: return null
        entries[requestId] = entry.copy(
            connection = entry.connection.copy(address = address, port = port, name = name)
        )
        publishChange()
        return requestId
    }

    @Synchronized
    fun consume(requestId: String) {
        removeEntry(requestId)
    }

    @Synchronized
    fun remove(requestId: String) {
        removeEntry(requestId)
    }

    @Synchronized
    fun removeForChild(childId: String) {
        entries.filterValues { it.connection.expectedChildId == childId }
            .keys.toList().forEach(::removeEntry)
    }

    @Synchronized
    fun clear() {
        entries.keys.toList().forEach(::removeEntry)
    }

    @Synchronized
    fun contains(requestId: String): Boolean {
        removeExpired()
        return entries.containsKey(requestId)
    }

    @Synchronized
    internal fun size(): Int {
        removeExpired()
        return entries.size
    }

    @Synchronized
    private fun expire(requestId: String, expiresAt: Long) {
        if (entries[requestId]?.expiresAt == expiresAt && elapsedRealtime() >= expiresAt) {
            removeEntry(requestId)
        }
    }

    private fun removeExpired() {
        val now = elapsedRealtime()
        entries.filterValues { it.expiresAt <= now }.keys.toList().forEach(::removeEntry)
    }

    private fun removeEntry(requestId: String) {
        entries.remove(requestId)?.connection?.let { connection ->
            connection.wipeCredential()
            publishChange()
        }
    }

    private fun publishChange() {
        _changes.value = _changes.value + 1
    }

    companion object {
        const val MAX_ENTRIES = 16
        const val REQUEST_LIFETIME_MS = 10 * 60 * 1000L
        private val expirationExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "PendingConnectionExpiry").apply { isDaemon = true }
        }

        private fun newRequestId(): String {
            val bytes = ByteArray(18).also(SecureRandom()::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun scheduleExpiration(delayMillis: Long, action: () -> Unit) {
            expirationExecutor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
        }
    }
}

object PendingConnections {
    val store = PendingConnectionStore()
}
