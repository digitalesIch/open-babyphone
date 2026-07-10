package org.openbabyphone.viewmodel

import android.app.Application
import org.openbabyphone.R
import org.openbabyphone.service.ListenServiceRepository
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
    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        ListenServiceRepository.reset()
        viewModel = ListenViewModel(context)
    }

    @Test
    fun `initial state has connecting status`() = runTest {
        val connecting = context.getString(R.string.connecting)
        val state = viewModel.uiState.first { it.status == connecting }
        assertEquals(connecting, state.status)
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
    fun `listening state reflects in state`() = runTest {
        ListenServiceRepository.updateListening()
        val state = viewModel.uiState.first { it.isConnected }
        assertTrue(state.isConnected)
        assertFalse(state.isReconnecting)
    }

    @Test
    fun `error updates state to disconnected`() = runTest {
        ListenServiceRepository.updateError(context.getString(R.string.disconnected))
        val state = viewModel.uiState.first { it.isError }
        assertTrue(state.isError)
        assertFalse(state.isConnected)
        assertFalse(state.isReconnecting)
        assertEquals(context.getString(R.string.disconnected), state.status)
    }

    @Test
    fun `reconnecting state reflects in state`() = runTest {
        ListenServiceRepository.updateReconnecting(1, 5)
        val state = viewModel.uiState.first { it.isReconnecting }
        assertFalse(state.isConnected)
        assertFalse(state.isError)
        assertTrue(state.isReconnecting)
        assertEquals(context.getString(R.string.reconnecting_status, 1, 5), state.status)
    }

    @Test
    fun `successful reconnect clears previous error`() = runTest {
        ListenServiceRepository.updateError(context.getString(R.string.disconnected))
        ListenServiceRepository.updateListening()

        val state = viewModel.uiState.first { it.isConnected }
        assertFalse(state.isError)
        assertEquals(context.getString(R.string.listening), state.status)
    }

    @Test
    fun `child device name reflects in state`() = runTest {
        ListenServiceRepository.updateChildDeviceName("ChildDevice01")
        val state = viewModel.uiState.first { it.childDeviceName == "ChildDevice01" }
        assertEquals("ChildDevice01", state.childDeviceName)
    }
}