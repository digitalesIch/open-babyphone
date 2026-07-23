package org.openbabyphone.viewmodel

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.MonitorService
import org.openbabyphone.ChildDeviceNamePreferences
import org.openbabyphone.OpenBabyphoneApplication
import org.openbabyphone.PairingCode
import org.openbabyphone.PairingQrCode
import org.openbabyphone.R
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MonitorViewModelTest {

    private lateinit var context: Application
    private lateinit var viewModel: MonitorViewModel

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().apply()
        MonitorServiceRepository.reset()
        viewModel = MonitorViewModel(context)
    }

    @Test
    fun `fresh install generates and persists a secure pairing code`() = runTest {
        val state = viewModel.uiState.first { it.pairingCode.isNotEmpty() }
        val persisted = context.getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        ).getString(MonitorService.PREF_KEY_PAIRING_CODE, "")

        assertTrue(PairingCode.isValid(state.pairingCode))
        assertTrue(state.pairingCode.isNotBlank())
        assertEquals(state.pairingCode, persisted)
    }

    @Test
    fun `invalid saved pairing code is replaced automatically`() = runTest {
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
        prefs.edit().putString(MonitorService.PREF_KEY_PAIRING_CODE, "invalid code").apply()

        viewModel = MonitorViewModel(context)
        val state = viewModel.uiState.first { it.pairingCode.isNotEmpty() }

        assertNotEquals("invalid code", state.pairingCode)
        assertTrue(PairingCode.isValid(state.pairingCode))
        assertTrue(state.pairingCode.isNotBlank())
    }

    @Test
    fun `fresh install uses and persists default child name`() = runTest {
        val state = viewModel.uiState.first { it.deviceName.isNotEmpty() }
        val expectedName = context.getString(R.string.default_child_name)
        val persisted = context.getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Application.MODE_PRIVATE
        ).getString(MonitorService.PREF_KEY_DEVICE_NAME, "")

        assertEquals(expectedName, state.deviceName)
        assertEquals(expectedName, persisted)
    }

    @Test
    fun `saved child name is retained`() = runTest {
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().putString(MonitorService.PREF_KEY_DEVICE_NAME, "Nursery").apply()

        viewModel = MonitorViewModel(context)

        assertEquals("Nursery", viewModel.uiState.first { it.deviceName == "Nursery" }.deviceName)
    }

    @Test
    fun `start and stop update typed monitor session state`() {
        viewModel.startMonitoring()
        assertEquals(MonitorSessionState.Starting, MonitorServiceRepository.sessionState.value)

        viewModel.stopMonitoring()
        assertEquals(MonitorSessionState.Stopped, MonitorServiceRepository.sessionState.value)
    }

    @Test
    fun `connected session count is authoritative`() = runTest {
        MonitorServiceRepository.updateConnectedClients(1)
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(2))

        val state = viewModel.uiState.first { it.connectedClients == 2 }

        assertEquals(2, state.connectedClients)
    }

    @Test
    fun `service details remain available for connection details`() = runTest {
        MonitorServiceRepository.updateServiceInfo("TestService", 8080, listOf("test.local"))

        val state = viewModel.uiState.first { it.serviceName == "TestService" }

        assertEquals(8080, state.port)
        assertEquals(listOf("test.local"), state.addresses)
    }

    @Test
    fun `terminal service errors render inactive recovery with retained reason`() = runTest {
        val reason = "Microphone failed"
        MonitorServiceRepository.updateError(MonitorSessionError.AudioCapture, reason)

        val state = viewModel.uiState.first {
            it.sessionState == MonitorSessionState.Error(MonitorSessionError.AudioCapture, reason)
        }

        assertFalse(state.isMonitoring)
        assertEquals(reason, state.terminalErrorReason)
    }

    @Test
    fun `advertising error remains active and recoverable`() = runTest {
        MonitorServiceRepository.updateError(MonitorSessionError.Advertising, "Advertising failed")

        val state = viewModel.uiState.first { it.sessionState is MonitorSessionState.Error }

        assertTrue(state.isMonitoring)
        assertNull(state.terminalErrorReason)
    }

    @Test
    fun `qr payload is generated from automatic credentials`() = runTest {
        val state = viewModel.uiState.first { it.qrPayload.isNotEmpty() }

        assertTrue(state.qrPayload.startsWith("openbabyphone://pair?"))
        assertTrue(state.qrPayload.contains("childId="))
        assertTrue(state.qrPayload.contains("pairingId="))
    }

    @Test
    fun `start refreshes renamed child configuration in existing view model`() = runTest {
        assertEquals("Nursery", ChildDeviceNamePreferences.write(context, "Nursery"))

        viewModel.startMonitoring()

        val state = viewModel.uiState.first { it.deviceName == "Nursery" && it.qrPayload.isNotEmpty() }
        val qr = PairingQrCode.parse(state.qrPayload) as PairingQrCode.ParsedQrCode.Structured
        assertEquals("Nursery", qr.name)
    }
}
