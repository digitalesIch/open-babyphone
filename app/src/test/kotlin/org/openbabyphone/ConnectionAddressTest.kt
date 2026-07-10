package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionAddressTest {

    @Test
    fun `normalize strips parenthetical network type suffix`() {
        assertEquals("192.168.1.5", ConnectionAddress.normalize("192.168.1.5 (WIFI)"))
    }

    @Test
    fun `normalize strips trailing spaces`() {
        assertEquals("192.168.1.5", ConnectionAddress.normalize("192.168.1.5   "))
    }

    @Test
    fun `normalize strips space-separated suffix`() {
        assertEquals("192.168.1.5", ConnectionAddress.normalize("192.168.1.5 WIFI"))
    }

    @Test
    fun `normalize leaves clean address unchanged`() {
        assertEquals("192.168.1.5", ConnectionAddress.normalize("192.168.1.5"))
    }

    @Test
    fun `normalize handles empty string`() {
        assertEquals("", ConnectionAddress.normalize(""))
    }

    @Test
    fun `normalize handles address with spaces inside parentheses only`() {
        assertEquals("192.168.1.5", ConnectionAddress.normalize("192.168.1.5 (WIFI) "))
    }

    @Test
    fun `normalize strips multiple suffixes`() {
        assertEquals("192.168.1.5", ConnectionAddress.normalize("192.168.1.5 (WIFI) extra"))
    }
}