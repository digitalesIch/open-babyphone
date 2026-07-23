package org.openbabyphone.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRepositoryStateTest {
    @After
    fun resetRepositories() {
        ListenServiceRepository.reset()
        MonitorServiceRepository.reset()
    }

    @Test
    fun `verified audio restores listening after disruption`() {
        ListenServiceRepository.updateDisrupted()

        ListenServiceRepository.updateListening()

        assertEquals(ListenSessionState.Listening, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `reconnect status does not revive terminal audio loss`() {
        ListenServiceRepository.updateLost()

        ListenServiceRepository.updateReconnecting(2, 5)

        assertEquals(ListenSessionState.Lost, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `active disruption can advance to reconnecting`() {
        ListenServiceRepository.updateDisrupted()

        ListenServiceRepository.updateReconnecting(2, 5)

        assertEquals(ListenSessionState.Reconnecting(2, 5), ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `late audio delivery cannot revive terminal loss`() {
        ListenServiceRepository.updateLost()

        ListenServiceRepository.updateListening()

        assertEquals(ListenSessionState.Lost, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `fresh disruption cannot downgrade audio loss before delivery`() {
        ListenServiceRepository.updateLost()

        ListenServiceRepository.updateDisrupted()

        assertEquals(ListenSessionState.Lost, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `listen terminal error is preserved when service is destroyed`() {
        val error = ListenSessionState.Error(ListenSessionError.Playback, "playback failed")
        ListenServiceRepository.updateError(error.type, error.reason)

        ListenServiceRepository.updateStopped()

        assertEquals(error, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `terminal post-connect loss is preserved when service is destroyed`() {
        ListenServiceRepository.updateLost()

        ListenServiceRepository.updateStopped()

        assertEquals(ListenSessionState.Lost, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `late audio delivery cannot overwrite a stopped session`() {
        ListenServiceRepository.updateStopped()

        ListenServiceRepository.updateListening()

        assertEquals(ListenSessionState.Stopped, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `monitor capture error is preserved when service is destroyed`() {
        val error = MonitorSessionState.Error(MonitorSessionError.AudioCapture, "capture failed")
        MonitorServiceRepository.updateError(error.type, error.reason)

        MonitorServiceRepository.updateStopped()

        assertEquals(error, MonitorServiceRepository.sessionState.value)
    }
}
