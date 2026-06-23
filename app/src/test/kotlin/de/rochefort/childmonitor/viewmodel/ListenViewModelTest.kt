package de.rochefort.childmonitor.viewmodel

import android.app.Application
import de.rochefort.childmonitor.service.ListenServiceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ListenViewModelTest {

    private lateinit var viewModel: ListenViewModel

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Application
        ListenServiceRepository.startConnecting("")
        viewModel = ListenViewModel(context)
    }

    @Test
    fun `initial state has connecting status`() = runTest {
        val state = viewModel.uiState.first { it.status == "Connecting..." }
        assertEquals("Connecting...", state.status)
    }

    @Test
    fun `updateVolumeHistory updates state`() = runTest {
        val history = floatArrayOf(0.1f, 0.5f, 0.9f)
        viewModel.updateVolumeHistory(history, 2.0f)
        val state = viewModel.uiState.first { it.volumeHistory.isNotEmpty() }
        assertEquals(3, state.volumeHistory.size)
        assertEquals(2.0f, state.volumeNorm, 0.01f)
    }

    @Test
    fun `connected status reflects in state`() = runTest {
        ListenServiceRepository.updateConnected(true)
        val state = viewModel.uiState.first { it.isConnected }
        assertTrue(state.isConnected)
    }

    @Test
    fun `error updates state to disconnected`() = runTest {
        ListenServiceRepository.updateError()
        val state = viewModel.uiState.first { it.isError }
        assertTrue(state.isError)
        assertFalse(state.isConnected)
        assertEquals("Disconnected", state.status)
    }

    @Test
    fun `successful reconnect clears previous error`() = runTest {
        ListenServiceRepository.updateError()
        ListenServiceRepository.updateConnected(true)

        val state = viewModel.uiState.first { it.isConnected }
        assertFalse(state.isError)
        assertEquals("Listening...", state.status)
    }

    @Test
    fun `child device name reflects in state`() = runTest {
        ListenServiceRepository.updateChildDeviceName("ChildDevice01")
        val state = viewModel.uiState.first { it.childDeviceName == "ChildDevice01" }
        assertEquals("ChildDevice01", state.childDeviceName)
    }
}
