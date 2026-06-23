package org.openbabyphone.viewmodel

import android.app.Application
import org.openbabyphone.MonitorService
import org.openbabyphone.service.MonitorServiceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MonitorViewModelTest {

    private lateinit var viewModel: MonitorViewModel

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        prefs.edit().clear().apply()

        MonitorServiceRepository.updateServiceInfo("", 10000, emptyList())
        MonitorServiceRepository.updateStatus("Waiting for Parent...")

        viewModel = MonitorViewModel(context)
    }

    @Test
    fun `initial pairing code is empty when no saved value`() = runTest {
        val state = viewModel.uiState.first { it.pairingCode.isEmpty() || it.pairingCode.isNotEmpty() }
        assertEquals("", state.pairingCode)
    }

    @Test
    fun `updatePairingCode updates state`() = runTest {
        viewModel.updatePairingCode("test123")
        val state = viewModel.uiState.first { it.pairingCode == "test123" }
        assertEquals("test123", state.pairingCode)
    }

    @Test
    fun `updatePairingCode trims whitespace`() = runTest {
        viewModel.updatePairingCode("  code  ")
        val state = viewModel.uiState.first { it.pairingCode == "code" }
        assertEquals("code", state.pairingCode)
    }

    @Test
    fun `updatePairingCode ignores invalid characters`() = runTest {
        viewModel.updatePairingCode("valid123")
        viewModel.updatePairingCode("invalid-code")
        val state = viewModel.uiState.first { it.pairingCode == "valid123" }
        assertEquals("valid123", state.pairingCode)
    }

    @Test
    fun `updatePairingCode persists to SharedPreferences`() {
        viewModel.updatePairingCode("persisted")
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        assertEquals("persisted", prefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, ""))
    }

    @Test
    fun `invalid pairing code is not persisted`() {
        viewModel.updatePairingCode("persisted")
        viewModel.updatePairingCode("invalid code")
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        assertEquals("persisted", prefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, ""))
    }

    @Test
    fun `saved pairing code is loaded on init`() = runTest {
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        prefs.edit().putString(MonitorService.PREF_KEY_PAIRING_CODE, "savedCode").apply()
        viewModel = MonitorViewModel(context)
        val state = viewModel.uiState.first { it.pairingCode == "savedCode" }
        assertEquals("savedCode", state.pairingCode)
    }

    @Test
    fun `service info updates reflect in state`() = runTest {
        MonitorServiceRepository.updateServiceInfo("TestService", 8080, listOf("192.168.1.1"))
        val state = viewModel.uiState.first { it.serviceName == "TestService" }
        assertEquals("TestService", state.serviceName)
        assertEquals(8080, state.port)
        assertTrue(state.addresses.contains("192.168.1.1"))
    }

    @Test
    fun `isLoading is true when service name is empty`() = runTest {
        MonitorServiceRepository.updateServiceInfo("", 10000, emptyList())
        val state = viewModel.uiState.first()
        assertTrue(state.isLoading)
    }
}
