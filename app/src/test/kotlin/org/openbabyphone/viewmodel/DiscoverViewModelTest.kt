package org.openbabyphone.viewmodel

import android.app.Application
import org.openbabyphone.PairingQrCode
import org.openbabyphone.TrustedChildStore
import org.openbabyphone.viewmodel.DiscoverViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiscoverViewModelTest {

    private lateinit var viewModel: DiscoverViewModel

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences("DiscoverPrefs", Application.MODE_PRIVATE)
        prefs.edit().clear().apply()
        context.getSharedPreferences("trusted_children", Application.MODE_PRIVATE)
            .edit().clear().apply()
        viewModel = DiscoverViewModel(context)
    }

    @Test
    fun `initial state has empty pairing code`() {
        assertEquals("", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `updatePairingCode updates state`() {
        viewModel.updatePairingCode("myCode")
        assertEquals("myCode", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `updatePairingCode trims whitespace`() {
        viewModel.updatePairingCode("  myCode  ")
        assertEquals("myCode", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `updatePairingCode ignores invalid characters`() {
        viewModel.updatePairingCode("valid123")
        viewModel.updatePairingCode("invalid-code")
        assertEquals("valid123", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `updatePairingCode persists to SharedPreferences`() {
        val context = RuntimeEnvironment.getApplication() as Application
        viewModel.updatePairingCode("persisted")

        val prefs = context.getSharedPreferences("DiscoverPrefs", Application.MODE_PRIVATE)
        assertEquals("persisted", prefs.getString("pairingCode", ""))
    }

    @Test
    fun `invalid pairing code is not persisted`() {
        val context = RuntimeEnvironment.getApplication() as Application
        viewModel.updatePairingCode("persisted")
        viewModel.updatePairingCode("invalid code")

        val prefs = context.getSharedPreferences("DiscoverPrefs", Application.MODE_PRIVATE)
        assertEquals("persisted", prefs.getString("pairingCode", ""))
    }

    @Test
    fun `saved pairing code is loaded on init`() {
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences("DiscoverPrefs", Application.MODE_PRIVATE)
        prefs.edit().putString("pairingCode", "savedCode").apply()

        viewModel = DiscoverViewModel(context)
        assertEquals("savedCode", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `initial state is not discovering`() {
        assertFalse(viewModel.uiState.value.isDiscovering)
    }

    @Test
    fun `initial state has empty device list`() {
        assertTrue(viewModel.uiState.value.devices.isEmpty())
    }

    @Test
    fun `initial state has empty trusted children`() {
        assertTrue(viewModel.uiState.value.trustedChildren.isEmpty())
    }

    @Test
    fun `handleQrScan with legacy code sets pairing code`() {
        val result = viewModel.handleQrScan("myCode42")
        assertTrue(result)
        assertEquals("myCode42", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `handleQrScan with structured payload stores trusted child`() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId123",
            pairingId = "pairingId456",
            name = "Nursery",
            pairingCode = "myCode42"
        )
        val result = viewModel.handleQrScan(payload)
        assertTrue(result)
        assertEquals(1, viewModel.uiState.value.trustedChildren.size)
        val trusted = viewModel.uiState.value.trustedChildren[0]
        assertEquals("childId123", trusted.childId)
        assertEquals("pairingId456", trusted.pairingId)
        assertEquals("Nursery", trusted.displayName)
        assertEquals("myCode42", trusted.pairingCode)
        assertEquals("myCode42", viewModel.uiState.value.pairingCode)
    }

    @Test
    fun `handleQrScan with invalid content returns false`() {
        assertFalse(viewModel.handleQrScan(null))
        assertFalse(viewModel.handleQrScan(""))
        assertFalse(viewModel.handleQrScan("   "))
    }

    @Test
    fun `handleQrScan with structured payload updates existing trusted child`() {
        val payload1 = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId1",
            name = "Nursery",
            pairingCode = "code1"
        )
        viewModel.handleQrScan(payload1)
        assertEquals(1, viewModel.uiState.value.trustedChildren.size)

        val payload2 = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId2",
            name = "Updated Room",
            pairingCode = "code2"
        )
        viewModel.handleQrScan(payload2)
        assertEquals(1, viewModel.uiState.value.trustedChildren.size)
        val trusted = viewModel.uiState.value.trustedChildren[0]
        assertEquals("pairId2", trusted.pairingId)
        assertEquals("Updated Room", trusted.displayName)
        assertEquals("code2", trusted.pairingCode)
    }

    @Test
    fun `recordLastKnownChildAddress updates trusted child in ui state`() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId1",
            name = "Nursery",
            pairingCode = "code1"
        )
        viewModel.handleQrScan(payload)

        viewModel.recordLastKnownChildAddress("childId1", "__VG_IPV4_7d0c5d0f3f3b__", 10000)

        val trusted = viewModel.uiState.value.trustedChildren.single()
        assertEquals("__VG_IPV4_7d0c5d0f3f3b__", trusted.lastKnownAddress)
        assertEquals(10000, trusted.lastKnownPort)
        assertTrue(trusted.lastSeenAt > 0)
    }

    @Test
    fun `forgetChild removes trusted child`() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId1",
            name = "Nursery",
            pairingCode = "code1"
        )
        viewModel.handleQrScan(payload)
        assertEquals(1, viewModel.uiState.value.trustedChildren.size)

        viewModel.forgetChild("childId1")
        assertEquals(0, viewModel.uiState.value.trustedChildren.size)
    }

    @Test
    fun `pairingCodeFor unknown device returns global pairing code`() {
        viewModel.updatePairingCode("globalCode")
        val device = DiscoveredDevice(
            name = "TestDevice",
            address = "__VG_IPV4_7d0c5d0f3f3b__",
            port = 10000
        )
        assertEquals("globalCode", viewModel.pairingCodeFor(device))
    }

    @Test
    fun `pairingCodeFor trusted device returns stored code`() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId1",
            name = "Nursery",
            pairingCode = "storedCode"
        )
        viewModel.handleQrScan(payload)

        val device = DiscoveredDevice(
            name = "Open Babyphone — Nursery",
            address = "__VG_IPV4_7d0c5d0f3f3b__",
            port = 10000,
            childId = "childId1",
            pairingId = "pairId1",
            displayName = "Nursery"
        )
        assertEquals("storedCode", viewModel.pairingCodeFor(device))
    }

    @Test
    fun `pairingCodeFor device with reset pairing returns global code`() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId1",
            name = "Nursery",
            pairingCode = "oldCode"
        )
        viewModel.handleQrScan(payload)

        val device = DiscoveredDevice(
            name = "Open Babyphone — Nursery",
            address = "__VG_IPV4_7d0c5d0f3f3b__",
            port = 10000,
            childId = "childId1",
            pairingId = "differentPairingId",
            displayName = "Nursery"
        )
        viewModel.updatePairingCode("globalCode")
        assertEquals("globalCode", viewModel.pairingCodeFor(device))
    }

    @Test
    fun `trusted children survive re-instantiation`() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId1",
            pairingId = "pairId1",
            name = "Nursery",
            pairingCode = "code1"
        )
        viewModel.handleQrScan(payload)

        val context = RuntimeEnvironment.getApplication() as Application
        viewModel = DiscoverViewModel(context)
        assertEquals(1, viewModel.uiState.value.trustedChildren.size)
        assertEquals("Nursery", viewModel.uiState.value.trustedChildren[0].displayName)
    }
}
