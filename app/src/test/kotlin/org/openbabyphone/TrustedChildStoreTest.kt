package org.openbabyphone

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TrustedChildStoreTest {

    private lateinit var store: TrustedChildStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences("trusted_children", Application.MODE_PRIVATE)
            .edit().clear().apply()
        store = TrustedChildStore(context)
    }

    @Test
    fun `empty store returns empty list`() {
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `upsert adds a child`() {
        val child = TrustedChild(
            childId = "child1",
            pairingId = "pair1",
            displayName = "Nursery",
            pairingCode = "code123"
        )
        store.upsert(child)
        assertEquals(1, store.getAll().size)
        assertEquals("Nursery", store.getAll()[0].displayName)
    }

    @Test
    fun `upsert replaces existing child with same id`() {
        val child = TrustedChild("child1", "pair1", "Nursery", "code123")
        store.upsert(child)
        val updated = TrustedChild("child1", "pair2", "Living Room", "code456")
        store.upsert(updated)
        assertEquals(1, store.getAll().size)
        assertEquals("Living Room", store.getAll()[0].displayName)
        assertEquals("pair2", store.getAll()[0].pairingId)
    }

    @Test
    fun `findById returns matching child`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        val found = store.findById("child1")
        assertNotNull(found)
        assertEquals("Nursery", found!!.displayName)
    }

    @Test
    fun `findById returns null for unknown id`() {
        assertNull(store.findById("nonexistent"))
    }

    @Test
    fun `forget removes a child`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        store.forget("child1")
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `forget is no-op for unknown id`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        store.forget("nonexistent")
        assertEquals(1, store.getAll().size)
    }

    @Test
    fun `clear removes all children`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        store.upsert(TrustedChild("child2", "pair2", "Living Room", "code456"))
        store.clear()
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `updateLastKnown updates address and port`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        store.updateLastKnown("child1", "192.168.1.100", 10000)
        val child = store.findById("child1")!!
        assertEquals("192.168.1.100", child.lastKnownAddress)
        assertEquals(10000, child.lastKnownPort)
        assertTrue(child.lastSeenAt > 0)
    }

    @Test
    fun `updateLastKnown is no-op for unknown id`() {
        store.updateLastKnown("nonexistent", "192.168.1.100", 10000)
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `upsert preserves lastKnown when re-scanning existing child`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        store.updateLastKnown("child1", "192.168.1.100", 10000)
        store.upsert(TrustedChild("child1", "pair2", "Nursery", "newcode"))
        val child = store.findById("child1")!!
        assertEquals("pair2", child.pairingId)
        assertEquals("newcode", child.pairingCode)
        assertEquals("192.168.1.100", child.lastKnownAddress)
        assertEquals(10000, child.lastKnownPort)
        assertTrue(child.lastSeenAt > 0)
    }

    @Test
    fun `matchesPairing returns true for same pairingId`() {
        val child = TrustedChild("child1", "pair1", "Nursery", "code123")
        assertTrue(child.matchesPairing("pair1"))
    }

    @Test
    fun `matchesPairing returns false for different pairingId`() {
        val child = TrustedChild("child1", "pair1", "Nursery", "code123")
        assertFalse(child.matchesPairing("pair2"))
    }

    @Test
    fun `persistence survives re-instantiation`() {
        store.upsert(TrustedChild("child1", "pair1", "Nursery", "code123"))
        val context = RuntimeEnvironment.getApplication() as Application
        val newStore = TrustedChildStore(context)
        assertEquals(1, newStore.getAll().size)
        assertEquals("Nursery", newStore.getAll()[0].displayName)
    }

    @Test
    fun `corrupt json is cleared gracefully`() {
        val context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences("trusted_children", Application.MODE_PRIVATE)
            .edit().putString("trustedChildren", "NOT_JSON").apply()
        val newStore = TrustedChildStore(context)
        assertTrue(newStore.getAll().isEmpty())
    }
}