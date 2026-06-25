package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrCodeTest {

    @Test
    fun parseScannedCode_validRawCode_returnsTrimmedCode() {
        val result = PairingQrCode.parseScannedCode("babyroom42")
        assertEquals("babyroom42", result)
    }

    @Test
    fun parseScannedCode_whitespaceIsTrimmed() {
        val result = PairingQrCode.parseScannedCode("  babyroom42  ")
        assertEquals("babyroom42", result)
    }

    @Test
    fun parseScannedCode_validAlphanumericWithLettersAndNumbers() {
        val result = PairingQrCode.parseScannedCode("RoomA1bC2")
        assertEquals("RoomA1bC2", result)
    }

    @Test
    fun parseScannedCode_nullContent_returnsNull() {
        val result = PairingQrCode.parseScannedCode(null)
        assertNull(result)
    }

    @Test
    fun parseScannedCode_emptyContent_returnsNull() {
        val result = PairingQrCode.parseScannedCode("")
        assertNull(result)
    }

    @Test
    fun parseScannedCode_blankContent_returnsNull() {
        val result = PairingQrCode.parseScannedCode("   ")
        assertNull(result)
    }
}