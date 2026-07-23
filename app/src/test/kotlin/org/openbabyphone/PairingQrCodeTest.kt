package org.openbabyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun parse_structuredPayload_returnsStructuredResult() {
        val payload = PairingQrCode.buildPayload(
            childId = "abc123def456",
            pairingId = "xyz789",
            name = "Nursery",
            pairingCode = "myCode42"
        )
        val result = PairingQrCode.parse(payload)
        assertTrue(result is PairingQrCode.ParsedQrCode.Structured)
        val structured = result as PairingQrCode.ParsedQrCode.Structured
        assertEquals("abc123def456", structured.childId)
        assertEquals("xyz789", structured.pairingId)
        assertEquals("Nursery", structured.name)
        assertEquals("myCode42", structured.pairingCode)
    }

    @Test
    fun parse_structuredPayload_returnsCodeFromParseScannedCode() {
        val payload = PairingQrCode.buildPayload(
            childId = "child1",
            pairingId = "pair1",
            name = "LivingRoom",
            pairingCode = "secret123"
        )
        val code = PairingQrCode.parseScannedCode(payload)
        assertEquals("secret123", code)
    }

    @Test
    fun parse_legacyRawCode_returnsLegacyResult() {
        val result = PairingQrCode.parse("babyroom42")
        assertTrue(result is PairingQrCode.ParsedQrCode.Legacy)
        assertEquals("babyroom42", (result as PairingQrCode.ParsedQrCode.Legacy).pairingCode)
    }

    @Test
    fun parse_nullContent_returnsNull() {
        assertNull(PairingQrCode.parse(null))
    }

    @Test
    fun parse_blankContent_returnsNull() {
        assertNull(PairingQrCode.parse("   "))
    }

    @Test
    fun buildPayload_roundTripPreservesAllFields() {
        val payload = PairingQrCode.buildPayload(
            childId = "childId123",
            pairingId = "pairingId456",
            name = "My Room",
            pairingCode = "code7890"
        )
        val parsed = PairingQrCode.parse(payload) as PairingQrCode.ParsedQrCode.Structured
        assertEquals("childId123", parsed.childId)
        assertEquals("pairingId456", parsed.pairingId)
        assertEquals("My Room", parsed.name)
        assertEquals("code7890", parsed.pairingCode)
    }

    @Test
    fun buildPayload_withSpecialCharactersInCode_roundTripsCorrectly() {
        val payload = PairingQrCode.buildPayload(
            childId = "cid",
            pairingId = "pid",
            name = "Test",
            pairingCode = "ABC12345"
        )
        val parsed = PairingQrCode.parse(payload) as PairingQrCode.ParsedQrCode.Structured
        assertEquals("ABC12345", parsed.pairingCode)
    }

    @Test
    fun buildPayload_withEmptyName_roundTripsCorrectly() {
        val payload = PairingQrCode.buildPayload(
            childId = "cid",
            pairingId = "pid",
            name = "",
            pairingCode = "code1234"
        )
        val parsed = PairingQrCode.parse(payload) as PairingQrCode.ParsedQrCode.Structured
        assertEquals("", parsed.name)
    }

    @Test
    fun parse_invalidUriScheme_returnsLegacyForNonOpenBabyphoneScheme() {
        val result = PairingQrCode.parse("other://pair?childId=x&pairingId=y&name=z&code=w")
        assertTrue(result is PairingQrCode.ParsedQrCode.Legacy)
    }

    @Test
    fun parse_structuredMissingChildId_returnsNull() {
        val payload = "openbabyphone://pair?pairingId=pid&name=Test&code=Y29kZQ=="
        val result = PairingQrCode.parse(payload)
        assertNull(result)
    }

    @Test
    fun parse_structuredMissingPairingId_returnsNull() {
        val payload = "openbabyphone://pair?childId=cid&name=Test&code=Y29kZQ=="
        val result = PairingQrCode.parse(payload)
        assertNull(result)
    }

    @Test
    fun parse_legacyWithWhitespace_trimsCode() {
        val result = PairingQrCode.parse("  code123  ") as PairingQrCode.ParsedQrCode.Legacy
        assertEquals("code123", result.pairingCode)
    }

    @Test
    fun generateId_producesUniqueIds() {
        val id1 = ChildDeviceIdentity.generateId()
        val id2 = ChildDeviceIdentity.generateId()
        assertNotNull(id1)
        assertNotNull(id2)
        assertTrue(id1 != id2)
    }

    @Test
    fun generateId_hasExpectedLength() {
        val id = ChildDeviceIdentity.generateId()
        assertEquals(16, id.length)
    }

    @Test
    fun generate_producesValidIdentity() {
        val identity = ChildDeviceIdentity.generate()
        assertNotNull(identity.childId)
        assertNotNull(identity.pairingId)
        assertEquals(16, identity.childId.length)
        assertEquals(16, identity.pairingId.length)
        assertTrue(identity.childId != identity.pairingId)
    }
}
