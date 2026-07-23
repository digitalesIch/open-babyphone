package org.openbabyphone.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openbabyphone.service.ListenServiceRepository

class PacketLossConcealerTest {
    @Test
    fun `underruns progressively fade last decoded PCM then emit silence`() {
        val concealer = PacketLossConcealer(4)
        val output = ShortArray(4)
        concealer.onRealFrame(shortArrayOf(400, -400, 200, -200), 4)

        concealer.concealInto(output)
        assertArrayEquals(shortArrayOf(300, -300, 150, -150), output)
        concealer.concealInto(output)
        assertArrayEquals(shortArrayOf(200, -200, 100, -100), output)
        concealer.concealInto(output)
        assertArrayEquals(shortArrayOf(100, -100, 50, -50), output)
        concealer.concealInto(output)
        assertArrayEquals(shortArrayOf(0, 0, 0, 0), output)
        assertEquals(PacketLossConcealer.FADE_FRAMES, concealer.consecutiveLosses())
    }

    @Test
    fun `real frame resets concealment fade`() {
        val concealer = PacketLossConcealer(2)
        val output = ShortArray(2)
        concealer.onRealFrame(shortArrayOf(400, 200), 2)
        repeat(2) { concealer.concealInto(output) }

        concealer.onRealFrame(shortArrayOf(800, 400), 2)
        concealer.concealInto(output)

        assertArrayEquals(shortArrayOf(600, 300), output)
        assertEquals(1, concealer.consecutiveLosses())
    }

    @Test
    fun `concealment does not mark delivery healthy or publish listening`() {
        var now = 1_000L
        val health = AudioDeliveryHealth({ now })
        health.arm()
        ListenServiceRepository.reset()
        val initialRepositoryState = ListenServiceRepository.sessionState.value
        val concealer = PacketLossConcealer(2)
        val output = ShortArray(2)
        concealer.onRealFrame(shortArrayOf(400, 200), 2)

        repeat(5) { concealer.concealInto(output) }
        now = 7_000L

        assertEquals(AudioDeliveryStatus.Disrupted, health.status())
        assertEquals(initialRepositoryState, ListenServiceRepository.sessionState.value)
    }

    @Test
    fun `real silent PCM remains a real frame and resets PLC`() {
        val concealer = PacketLossConcealer(2)
        val output = ShortArray(2)
        concealer.onRealFrame(shortArrayOf(400, 200), 2)
        concealer.concealInto(output)

        concealer.onRealFrame(shortArrayOf(0, 0), 2)
        concealer.concealInto(output)

        assertArrayEquals(shortArrayOf(0, 0), output)
        assertEquals(1, concealer.consecutiveLosses())
    }

    @Test
    fun `clear removes reconnect audio and concealment state`() {
        val concealer = PacketLossConcealer(2)
        val output = ShortArray(2)
        concealer.onRealFrame(shortArrayOf(400, 200), 2)
        concealer.concealInto(output)

        concealer.clear()
        concealer.concealInto(output)

        assertArrayEquals(shortArrayOf(0, 0), output)
        assertEquals(1, concealer.consecutiveLosses())
    }
}
