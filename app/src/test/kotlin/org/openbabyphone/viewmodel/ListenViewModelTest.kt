package org.openbabyphone.viewmodel

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    fun `presentation is derived from connecting state`() = runTest {
        ListenServiceRepository.startConnecting("Nursery")

        val state = awaitState(ListenSessionState.Connecting)

        assertEquals(context.getString(R.string.listen_connecting_title), state.presentation.message)
        assertTrue(state.presentation.showProgress)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `listening presentation has no action or spinner`() = runTest {
        ListenServiceRepository.updateListening()

        val state = awaitState(ListenSessionState.Listening)

        assertEquals(context.getString(R.string.listen_listening_title), state.presentation.message)
        assertFalse(state.presentation.showProgress)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `disrupted presentation promises automatic recovery without action`() = runTest {
        ListenServiceRepository.updateDisrupted()

        val state = awaitState(ListenSessionState.Disrupted)

        assertEquals(context.getString(R.string.audio_interrupted), state.presentation.message)
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `reconnecting presentation has one progress state and no action`() = runTest {
        val reconnecting = ListenSessionState.Reconnecting(2, 5)
        ListenServiceRepository.updateReconnecting(2, 5)

        val state = awaitState(reconnecting)

        assertTrue(state.presentation.showProgress)
        assertEquals(
            context.getString(R.string.listen_reconnecting_detail, 2, 5),
            state.presentation.detail
        )
        assertEquals(null, state.presentation.primaryAction)
    }

    @Test
    fun `lost presentation retries`() = runTest {
        ListenServiceRepository.updateLost()

        val state = awaitState(ListenSessionState.Lost)

        assertEquals(context.getString(R.string.connection_lost), state.presentation.message)
        assertEquals(ListenPrimaryAction.Retry, state.presentation.primaryAction)
    }

    @Test
    fun `typed terminal failures expose only their actionable recovery`() = runTest {
        val cases = listOf(
            ListenSessionError.Unreachable to ListenPrimaryAction.Retry,
            ListenSessionError.Authentication to ListenPrimaryAction.PairAgain,
            ListenSessionError.CredentialStorage to ListenPrimaryAction.Retry,
            ListenSessionError.Playback to ListenPrimaryAction.Retry,
            ListenSessionError.Decoding to ListenPrimaryAction.ConnectionHelp
        )

        cases.forEach { (error, action) ->
            val presentation = listenPresentation(
                context,
                ListenSessionState.Error(error, "internal reason")
            )
            assertEquals(action, presentation.primaryAction)
            assertFalse(presentation.showProgress)
        }
    }

    @Test
    fun `idle and stopped presentation has no recovery action`() {
        listOf(ListenSessionState.Idle, ListenSessionState.Stopped).forEach { state ->
            assertEquals(null, listenPresentation(context, state).primaryAction)
        }
    }

    @Test
    fun `updateVolumeHistory updates qualitative meter input`() = runTest {
        viewModel.updateVolumeHistory(floatArrayOf(0f, 0.5f, 0.9f), 2.0f)

        val state = viewModel.uiState.first { it.volumeHistory.isNotEmpty() }

        assertEquals(3, state.volumeHistory.size)
        assertEquals(2.0f, state.volumeNorm, 0.01f)
        assertTrue(state.lastAudioUpdateAtMillis > 0L)
    }

    @Test
    fun `child device name reflects in state`() = runTest {
        ListenServiceRepository.updateChildDeviceName("Nursery")
        assertEquals("Nursery", viewModel.uiState.first { it.childDeviceName == "Nursery" }.childDeviceName)
    }

    private suspend fun awaitState(expected: ListenSessionState): ListenUiState =
        viewModel.uiState.first { it.sessionState == expected }
}
