package org.openbabyphone

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenAddressProviderTest {

    @Test
    fun `keeps reachable IPv4 and IPv6 addresses in stable order`() {
        val result = collectListenAddresses(
            listOf(
                snapshot("192.168.43.1", "fd00::2"),
                snapshot("192.168.1.12", "2001:db8::1")
            )
        )

        assertEquals(
            listOf("192.168.1.12", "192.168.43.1", "2001:db8:0:0:0:0:0:1", "fd00:0:0:0:0:0:0:2"),
            result
        )
    }

    @Test
    fun `excludes unusable and unavailable interface addresses`() {
        val result = collectListenAddresses(
            listOf(
                snapshot("127.0.0.1", "0.0.0.0", "169.254.10.2", "::1", "fe80::1"),
                snapshot("192.168.1.20", isUp = false),
                snapshot("192.168.1.21", isLoopback = true)
            )
        )

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `deduplicates addresses reported by multiple interfaces`() {
        val result = collectListenAddresses(
            listOf(snapshot("192.168.1.12"), snapshot("192.168.1.12"))
        )

        assertEquals(listOf("192.168.1.12"), result)
    }

    private fun snapshot(
        vararg addresses: String,
        isUp: Boolean = true,
        isLoopback: Boolean = false
    ) = NetworkInterfaceSnapshot(
        isUp = isUp,
        isLoopback = isLoopback,
        addresses = addresses.map(InetAddress::getByName)
    )
}
