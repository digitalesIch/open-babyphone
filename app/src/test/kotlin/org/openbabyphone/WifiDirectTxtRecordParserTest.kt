package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiDirectTxtRecordParserTest {

    @Test
    fun parse_validRecord_returnsPeer() {
        val record = mapOf(
            "app" to "openbabyphone",
            "port" to "10000",
            "name" to "Nursery"
        )
        val peer = WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record)
        assertEquals("aa:bb:cc", peer?.deviceAddress)
        assertEquals("Pixel", peer?.deviceName)
        assertEquals(10000, peer?.port)
        assertEquals("Nursery", peer?.displayName)
    }

    @Test
    fun parse_missingApp_returnsNull() {
        val record = mapOf(
            "port" to "10000",
            "name" to "Nursery"
        )
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record))
    }

    @Test
    fun parse_wrongApp_returnsNull() {
        val record = mapOf(
            "app" to "other",
            "port" to "10000",
            "name" to "Nursery"
        )
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record))
    }

    @Test
    fun parse_missingPort_returnsNull() {
        val record = mapOf(
            "app" to "openbabyphone",
            "name" to "Nursery"
        )
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record))
    }

    @Test
    fun parse_nonNumericPort_returnsNull() {
        val record = mapOf(
            "app" to "openbabyphone",
            "port" to "abc",
            "name" to "Nursery"
        )
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record))
    }

    @Test
    fun parse_portOutOfRange_returnsNull() {
        val record = mapOf(
            "app" to "openbabyphone",
            "port" to "0",
            "name" to "Nursery"
        )
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record))

        val record2 = mapOf(
            "app" to "openbabyphone",
            "port" to "70000",
            "name" to "Nursery"
        )
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record2))
    }

    @Test
    fun parse_missingName_fallsBackToDeviceName() {
        val record = mapOf(
            "app" to "openbabyphone",
            "port" to "10000"
        )
        val peer = WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", record)
        assertEquals("Pixel", peer?.displayName)
    }

    @Test
    fun parse_emptyRecord_returnsNull() {
        assertNull(WifiDirectTxtRecordParser.parse("aa:bb:cc", "Pixel", emptyMap()))
    }
}