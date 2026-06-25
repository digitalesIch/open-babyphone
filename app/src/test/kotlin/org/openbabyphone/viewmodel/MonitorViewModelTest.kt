package org.openbabyphone.viewmodel

import android.app.Application
import org.openbabyphone.DeviceName
import org.openbabyphone.MonitorService
import org.openbabyphone.PairingCode
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
        MonitorServiceRepository.updateConnectedClients(0)

        viewModel = MonitorViewModel(context)
    }

    @Test
    fun `initial pairing code is non-empty when no saved value`() = runTest {
        val state = viewModel.uiState.first { it.pairingCode.isNotEmpty() }
        assertTrue(state.pairingCode.isNotEmpty())
    }

    @Test
    fun `fresh install generates non-empty pairing code`() = runTest {
        val state = viewModel.uiState.first { it.pairingCode.isNotEmpty() }
        assertTrue(state.pairingCode.isNotEmpty())
        assertTrue(PairingCode.isValid(state.pairingCode))
    }

    @Test
    fun `generated pairing code is persisted to SharedPreferences`() = runTest {
        val state = viewModel.uiState.first { it.pairingCode.isNotEmpty() }
        assertTrue("Initial code should be non-empty", state.pairingCode.isNotEmpty())
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        val persisted = prefs.getString(MonitorService.PREF_KEY_PAIRING_CODE, "")
        assertEquals("Generated code should be persisted", state.pairingCode, persisted)
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
    fun `updatePairingCode keeps invalid characters visible`() = runTest {
        viewModel.updatePairingCode("valid123")
        viewModel.updatePairingCode("invalid-code")
        val state = viewModel.uiState.first { it.pairingCode == "invalid-code" }
        assertEquals("invalid-code", state.pairingCode)
        assertTrue(!state.pairingCodeValid)
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
    fun `connected client count updates reflect in state`() = runTest {
        MonitorServiceRepository.updateConnectedClients(2)
        val state = viewModel.uiState.first { it.connectedClients == 2 }
        assertEquals(2, state.connectedClients)
    }

    @Test
    fun `isLoading is true when service name is empty`() = runTest {
        MonitorServiceRepository.updateServiceInfo("", 10000, emptyList())
        val state = viewModel.uiState.first()
        assertTrue(state.isLoading)
    }

    @Test
    fun `device name is empty when no saved value`() = runTest {
        val state = viewModel.uiState.first { it.pairingCode.isNotEmpty() }
        assertEquals("", state.deviceName)
    }

    @Test
    fun `updateDeviceName updates state`() = runTest {
        viewModel.updateDeviceName("Nursery")
        val state = viewModel.uiState.first { it.deviceName == "Nursery" }
        assertEquals("Nursery", state.deviceName)
    }

    @Test
    fun `updateDeviceName trims whitespace`() = runTest {
        viewModel.updateDeviceName("  Nursery  ")
        val state = viewModel.uiState.first { it.deviceName == "Nursery" }
        assertEquals("Nursery", state.deviceName)
    }

    @Test
    fun `updateDeviceName persists to SharedPreferences`() {
        viewModel.updateDeviceName("LivingRoom")
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        assertEquals("LivingRoom", prefs.getString(MonitorService.PREF_KEY_DEVICE_NAME, ""))
    }

    @Test
    fun `saved device name is loaded on init`() = runTest {
        val context = RuntimeEnvironment.getApplication() as Application
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        prefs.edit().putString(MonitorService.PREF_KEY_DEVICE_NAME, "MyRoom").apply()
        viewModel = MonitorViewModel(context)
        val state = viewModel.uiState.first { it.deviceName == "MyRoom" }
        assertEquals("MyRoom", state.deviceName)
    }

    @Test
    fun `updateDeviceName ignores names with newlines`() = runTest {
        viewModel.updateDeviceName("Valid")
        viewModel.updateDeviceName("Invalid\nName")
        val state = viewModel.uiState.first { it.deviceName == "Valid" }
        assertEquals("Valid", state.deviceName)
    }

    @Test
    fun `updateDeviceName ignores names exceeding 63 characters`() = runTest {
        viewModel.updateDeviceName("Valid")
        viewModel.updateDeviceName("a".repeat(64))
        val state = viewModel.uiState.first { it.deviceName == "Valid" }
        assertEquals("Valid", state.deviceName)
    }

    @Test
    fun `empty device name is accepted and cleared`() = runTest {
        viewModel.updateDeviceName("SomeName")
        viewModel.updateDeviceName("")
        val state = viewModel.uiState.first { it.deviceName == "" }
        assertEquals("", state.deviceName)
    }
}
