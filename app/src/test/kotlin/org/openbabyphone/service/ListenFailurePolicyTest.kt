package org.openbabyphone.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenFailurePolicyTest {
    @Test
    fun `connection failure before verified audio is unreachable`() {
        assertEquals(
            TerminalConnectionFailure.Unreachable,
            classifyTerminalConnectionFailure(hasVerifiedAudio = false)
        )
    }

    @Test
    fun `connection failure after verified audio is terminal loss`() {
        assertEquals(
            TerminalConnectionFailure.Lost,
            classifyTerminalConnectionFailure(hasVerifiedAudio = true)
        )
    }

    @Test
    fun `authenticated manual session retains retry credential`() {
        assertFalse(shouldConsumePendingConnection(hasDurableTrustedIdentity = false))
    }

    @Test
    fun `durably trusted session may consume pending credential`() {
        assertTrue(shouldConsumePendingConnection(hasDurableTrustedIdentity = true))
    }
}
