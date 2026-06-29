package org.openbabyphone

import android.net.wifi.p2p.WifiP2pManager
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiDirectErrorsTest {

    @Test
    fun describe_p2pUnsupported() {
        assertEquals(
            "Wi-Fi Direct is not supported on this device",
            WifiDirectErrors.describe(WifiP2pManager.P2P_UNSUPPORTED)
        )
    }

    @Test
    fun describe_busy() {
        assertEquals(
            "Wi-Fi Direct is busy, try again",
            WifiDirectErrors.describe(WifiP2pManager.BUSY)
        )
    }

    @Test
    fun describe_error() {
        assertEquals(
            "Wi-Fi Direct operation failed",
            WifiDirectErrors.describe(WifiP2pManager.ERROR)
        )
    }

    @Test
    fun describe_unknownCode_includesCode() {
        val result = WifiDirectErrors.describe(42)
        assertEquals("Wi-Fi Direct error (42)", result)
    }
}