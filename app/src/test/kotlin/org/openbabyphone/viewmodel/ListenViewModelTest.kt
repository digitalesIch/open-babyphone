package org.openbabyphone.viewmodel

import android.app.Application
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.R
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
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
    fun `presentation is derived from connecting state`() {
        ListenServiceRepository.startConnecting("Nursery")
        val state = currentState { it.sessionState == ListenSessionState.Connecting }
        assertEquals(context.getString(R.string.listen_connecting_title), state.presentation.message)
        assertTrue(state.presentation.showProgress)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `listening presentation has no action or spinner`() {
        ListenServiceRepository.updateListening()
        val state = currentState { it.sessionState == ListenSessionState.Listening }
        assertEquals(context.getString(R.string.listen_listening_title), state.presentation.message)
        assertFalse(state.presentation.showProgress)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `disrupted presentation promises automatic recovery without action`() {
        ListenServiceRepository.updateDisrupted()
        val state = currentState { it.sessionState == ListenSessionState.Disrupted }
        assertEquals(context.getString(R.string.audio_interrupted), state.presentation.message)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `reconnecting presentation has one progress state and no action`() {
        val reconnecting = ListenSessionState.Reconnecting(2, 5)
        ListenServiceRepository.updateReconnecting(2, 5)
        val state = currentState { it.sessionState == reconnecting }
        assertTrue(state.presentation.showProgress)
        assertEquals(context.getString(R.string.listen_reconnecting_detail, 2, 5), state.presentation.detail)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `lost presentation retries`() {
        ListenServiceRepository.updateLost()
        val state = currentState { it.sessionState == ListenSessionState.Lost }
        assertEquals(context.getString(R.string.connection_lost), state.presentation.message)
        assertEquals(ListenPrimaryAction.Retry, state.presentation.primaryAction)
    }

    @Test
    fun `typed terminal failures expose only their actionable recovery`() {
        val cases = listOf(
            ListenSessionError.Unreachable to ListenPrimaryAction.Retry,
            ListenSessionError.Authentication to ListenPrimaryAction.PairAgain,
            ListenSessionError.CredentialStorage to ListenPrimaryAction.Retry,
            ListenSessionError.CredentialUnavailable to ListenPrimaryAction.PairAgain,
            ListenSessionError.CredentialCorrupt to ListenPrimaryAction.PairAgain,
            ListenSessionError.Playback to ListenPrimaryAction.Retry,
            ListenSessionError.Decoding to ListenPrimaryAction.ConnectionHelp
        )
        cases.forEach { (error, action) ->
            val presentation = listenPresentation(context, ListenSessionState.Error(error, "internal reason"))
            assertEquals(action, presentation.primaryAction)
            assertFalse(presentation.showProgress)
        }
    }

    @Test
    fun `credential read failures present distinct messages`() {
        val unavailable = listenPresentation(context, ListenSessionState.Error(ListenSessionError.CredentialUnavailable, "reason"))
        val corrupt = listenPresentation(context, ListenSessionState.Error(ListenSessionError.CredentialCorrupt, "reason"))

        assertEquals(context.getString(R.string.saved_pairing_unavailable), unavailable.message)
        assertEquals(context.getString(R.string.saved_pairing_unavailable_detail), unavailable.detail)
        assertEquals(ListenPrimaryAction.PairAgain, unavailable.primaryAction)

        assertEquals(context.getString(R.string.saved_pairing_damaged), corrupt.message)
        assertEquals(context.getString(R.string.saved_pairing_damaged_detail), corrupt.detail)
        assertEquals(ListenPrimaryAction.PairAgain, corrupt.primaryAction)
    }

    @Test
    fun `idle and stopped presentation has no recovery action`() {
        listOf(ListenSessionState.Idle, ListenSessionState.Stopped).forEach { state ->
            assertEquals(null, listenPresentation(context, state).primaryAction)
        }
    }

    @Test
    fun `updateVolumeHistory updates qualitative meter input`() {
        viewModel.updateVolumeHistory(floatArrayOf(0f, 0.5f, 0.9f), 2.0f)
        val state = currentState { it.volumeHistory.isNotEmpty() }
        assertEquals(3, state.volumeHistory.size)
        assertEquals(2.0f, state.volumeNorm, 0.01f)
        assertTrue(state.lastAudioUpdateAtMillis > 0L)
    }

    @Test
    fun `child device name reflects in state`() {
        ListenServiceRepository.updateChildDeviceName("Nursery")
        assertEquals("Nursery", currentState { it.childDeviceName == "Nursery" }.childDeviceName)
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
