package com.ratig.app.core.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the strict QR payload contract used by the NIK scanner.
 * A scan must never be mistaken for authentication: unrecognized payloads are
 * rejected outright rather than partially accepted.
 */
class QrPayloadTest {

    private val qr = QrPayload()

    @Test
    fun `encode prefixes the nik with the ratig marker`() {
        assertEquals("RATIG1:3201234567890099", qr.encode("3201234567890099"))
    }

    @Test
    fun `round trip encode then parse returns the original nik`() {
        val nik = "3201234567890099"
        val parsed = qr.parse(qr.encode(nik))
        assertTrue(parsed is QrParseResult.Valid)
        assertEquals(nik, (parsed as QrParseResult.Valid).nik)
    }

    @Test
    fun `unknown prefix is unrecognized`() {
        assertEquals(QrParseResult.Unrecognized, qr.parse("HTTP://example.com/3201234567890099"))
    }

    @Test
    fun `non digit remainder is unrecognized`() {
        assertEquals(QrParseResult.Unrecognized, qr.parse("RATIG1:32012ABC67890099"))
    }

    @Test
    fun `empty remainder is unrecognized`() {
        assertEquals(QrParseResult.Unrecognized, qr.parse("RATIG1:"))
    }

    @Test
    fun `known prefix with wrong length is invalid nik`() {
        val parsed = qr.parse("RATIG1:1234")
        assertTrue(parsed is QrParseResult.InvalidNik)
    }

    @Test
    fun `custom prefix is honoured`() {
        val parsed = qr.parse("RATIG2:3201234567890099", prefix = "RATIG2:")
        assertTrue(parsed is QrParseResult.Valid)
    }

    @Test
    fun `leading zeros survive parsing`() {
        val parsed = qr.parse("RATIG1:0001234567890099")
        assertEquals("0001234567890099", (parsed as QrParseResult.Valid).nik)
    }
}
