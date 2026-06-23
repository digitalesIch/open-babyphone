package org.openbabyphone.viewmodel

import android.app.Application
import org.openbabyphone.viewmodel.DiscoverViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
