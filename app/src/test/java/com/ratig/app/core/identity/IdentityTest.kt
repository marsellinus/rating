package com.ratig.app.core.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for NIK validation, masking, and QR payload parsing. */
class IdentityTest {

    private val validator = NikValidator()
    private val qr = QrPayload()

    @Test
    fun `valid 16-digit nik passes`() {
        assertTrue(validator.isValid("3201234567890099"))
    }

    @Test
    fun `leading zeros are preserved end to end`() {
        val nik = "0012345678901234"
        val sanitized = validator.sanitize(" $nik ")
        assertEquals(nik, sanitized)
        assertTrue(validator.isValid(sanitized))
    }

    @Test
    fun `empty nik is rejected`() {
        assertEquals("NIK belum diisi.", (validator.validate("") as NikValidation.Invalid).message)
    }

    @Test
    fun `non-digit nik is rejected`() {
        assertFalse(validator.isValid("32012345678900AB"))
        assertFalse(validator.isValid("3201-2345-6789-009"))
    }

    @Test
    fun `short nik is rejected`() {
        assertFalse(validator.isValid("3201"))
    }

    @Test
    fun `long nik is rejected by default policy`() {
        assertFalse(validator.isValid("32012345678900991"))
    }

    @Test
    fun `sanitize strips separators but keeps digits`() {
        assertEquals("3201234567890099", validator.sanitize("3201-2345-6789-0099"))
        assertEquals("3201234567890099", validator.sanitize("3201 2345 6789 0099"))
    }

    @Test
    fun `sanitize caps at maxLength`() {
        assertEquals("32", validator.sanitize("3201", maxLength = 2))
    }

    @Test
    fun `mask hides middle digits but keeps prefix and suffix`() {
        val masked = NikMask.mask("3201234567890099")
        assertTrue(masked.startsWith("3201"))
        assertTrue(masked.endsWith("0099"))
        assertFalse(masked.contains("23456789"))
        assertEquals(16, masked.length)
    }

    @Test
    fun `qr round trip preserves nik including leading zeros`() {
        val nik = "0012345678901234"
        val payload = qr.encode(nik)
        val parsed = qr.parse(payload)
        assertTrue(parsed is QrParseResult.Valid)
        assertEquals(nik, (parsed as QrParseResult.Valid).nik)
    }

    @Test
    fun `foreign qr is unrecognized, never authenticated`() {
        val parsed = qr.parse("https://example.com/x")
        assertTrue(parsed is QrParseResult.Unrecognized)
    }

    @Test
    fun `qr with wrong prefix is unrecognized`() {
        val parsed = qr.parse("OTHER:3201234567890099")
        assertTrue(parsed is QrParseResult.Unrecognized)
    }

    @Test
    fun `qr with malformed nik is invalid`() {
        val parsed = qr.parse("RATIG1:3201")
        assertTrue(parsed is QrParseResult.InvalidNik)
    }
}
