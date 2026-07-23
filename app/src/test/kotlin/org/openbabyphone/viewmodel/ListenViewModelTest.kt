package org.openbabyphone.viewmodel

import android.app.Application
import android.os.Looper
import org.openbabyphone.R
import org.openbabyphone.service.ListenServiceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

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
    fun `initial state has connecting status`() {
        val connecting = context.getString(R.string.connecting)
        val state = currentState { it.status == connecting }
        assertEquals(connecting, state.status)
    }

    @Test
    fun `updateVolumeHistory updates state`() {
        val history = floatArrayOf(0.1f, 0.5f, 0.9f)
        viewModel.updateVolumeHistory(history, 2.0f)
        val state = currentState { it.volumeHistory.isNotEmpty() }
        assertEquals(3, state.volumeHistory.size)
        assertEquals(2.0f, state.volumeNorm, 0.01f)
    }

    @Test
    fun `listening state reflects in state`() {
        ListenServiceRepository.updateListening()
        val state = currentState { it.isConnected }
        assertTrue(state.isConnected)
        assertFalse(state.isReconnecting)
    }

    @Test
    fun `error updates state to disconnected`() {
        ListenServiceRepository.updateError(context.getString(R.string.disconnected))
        val state = currentState { it.isError }
        assertTrue(state.isError)
        assertFalse(state.isConnected)
        assertFalse(state.isReconnecting)
        assertEquals(context.getString(R.string.disconnected), state.status)
    }

    @Test
    fun `reconnecting state reflects in state`() {
        ListenServiceRepository.updateReconnecting(1, 5)
        val state = currentState { it.isReconnecting }
        assertFalse(state.isConnected)
        assertFalse(state.isError)
        assertTrue(state.isReconnecting)
        assertEquals(context.getString(R.string.reconnecting_status, 1, 5), state.status)
    }

    @Test
    fun `successful reconnect clears previous error`() {
        ListenServiceRepository.updateError(context.getString(R.string.disconnected))
        ListenServiceRepository.updateListening()

        val state = currentState { it.isConnected }
        assertFalse(state.isError)
        assertEquals(context.getString(R.string.listening), state.status)
    }

    @Test
    fun `child device name reflects in state`() {
        ListenServiceRepository.updateChildDeviceName("ChildDevice01")
        val state = currentState { it.childDeviceName == "ChildDevice01" }
        assertEquals("ChildDevice01", state.childDeviceName)
    }

    private fun currentState(predicate: (ListenUiState) -> Boolean): ListenUiState {
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.uiState.value
            if (predicate(state)) return state
        }
        throw AssertionError("ViewModel state did not reach the expected value")
    }
}
