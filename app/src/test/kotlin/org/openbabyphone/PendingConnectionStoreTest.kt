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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingConnectionStoreTest {
    private var now = 0L
    private var nextId = 0
    private val scheduled = mutableListOf<() -> Unit>()
    private val store = PendingConnectionStore(
        maxEntries = 2,
        lifetimeMillis = 100,
        elapsedRealtime = { now },
        requestIdFactory = { "request-${nextId++}" },
        scheduleExpiration = { _, action -> scheduled += action }
    )

    @Test
    fun `lease preserves request for retry until consumed`() {
        val requestId = store.put(connection("code1234"))

        assertEquals("code1234", store.lease(requestId)?.pairingCode?.concatToString())
        assertEquals("code1234", store.lease(requestId)?.pairingCode?.concatToString())
        store.consume(requestId)

        assertNull(store.lease(requestId))
    }

    @Test
    fun `eviction wipes oldest secret`() {
        val oldest = connection("code1234")
        val firstId = store.put(oldest)
        store.put(connection("code5678"))
        store.put(connection("code9012"))

        assertFalse(store.contains(firstId))
        assertTrue(oldest.pairingCode!!.all { it == '\u0000' })
    }

    @Test
    fun `remove and clear wipe secrets`() {
        val first = connection("code1234")
        val second = connection("code5678")
        val firstId = store.put(first)
        store.put(second)

        store.remove(firstId)
        store.clear()

        assertTrue(first.pairingCode!!.all { it == '\u0000' })
        assertTrue(second.pairingCode!!.all { it == '\u0000' })
    }

    @Test
    fun `scheduled expiration wipes request without another access`() {
        val connection = connection("code1234")
        val requestId = store.put(connection)
        val revisionBeforeExpiry = store.changes.value
        now = 100

        scheduled.single().invoke()

        assertFalse(store.contains(requestId))
        assertTrue(connection.pairingCode!!.all { it == '\u0000' })
        assertTrue(store.changes.value > revisionBeforeExpiry)
    }

    @Test
    fun `endpoint completion retains pending credential without copying it`() {
        val credential = "code1234".toCharArray()
        val requestId = store.put(
            PendingConnection(
                name = "Nursery",
                pairingCode = credential,
                expectedChildId = "child",
                expectedPairingId = "pair"
            )
        )

        assertEquals(requestId, store.completeEndpoint(requestId, "child.local", 10000, "Nursery"))

        val completed = store.lease(requestId)
        assertEquals("child.local", completed?.address)
        assertEquals(10000, completed?.port)
        assertSame(credential, completed?.pairingCode)
    }

    @Test
    fun `manual credential remains valid after success and later loss until retry`() {
        val requestId = store.put(connection("code1234"))

        val authenticatedLease = store.lease(requestId)
        val retryLease = store.lease(requestId)

        assertSame(authenticatedLease, retryLease)
        assertEquals("code1234", retryLease?.pairingCode?.concatToString())
    }

    private fun connection(code: String) =
        PendingConnection("host", 10000, "Nursery", code.toCharArray())
}
