package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.Shift
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins shift boundary rules. The old code never derived shift from time, which
 * is why an 18:23 WIB test could land in "Pagi" when the profile shift was blank.
 */
class ShiftResolverTest {

    private val resolver = ShiftResolver()

    private fun shift(name: String, start: String, end: String, tz: String = "Asia/Jakarta") =
        Shift(
            id = name,
            name = name,
            startTime = LocalTime.parse(start),
            endTime = LocalTime.parse(end),
            timezone = tz,
        )

    /** Local wall-clock time in WIB, converted correctly across midnight. */
    private fun wib(hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(2026, 11, 20, hour, minute, 0, 0, ZoneId.of("Asia/Jakarta"))
            .toInstant()

    @Test
    fun `afternoon test resolves to Siang not Pagi`() {
        val pagi = shift("Pagi", "06:00", "14:00")
        val siang = shift("Siang", "14:00", "22:00")
        val malam = shift("Malam", "22:00", "06:00")

        assertEquals("Siang", resolver.resolve(wib(18, 23), listOf(pagi, siang, malam))?.name)
        assertEquals("Pagi", resolver.resolve(wib(8, 0), listOf(pagi, siang, malam))?.name)
        assertEquals("Malam", resolver.resolve(wib(23, 30), listOf(pagi, siang, malam))?.name)
    }

    @Test
    fun `end boundary belongs to the next shift`() {
        val pagi = shift("Pagi", "06:00", "14:00")
        val siang = shift("Siang", "14:00", "22:00")
        assertFalse(resolver.contains(pagi, wib(14, 0)))
        assertTrue(resolver.contains(siang, wib(14, 0)))
    }

    @Test
    fun `overnight shift covers both sides of midnight`() {
        val malam = shift("Malam", "22:00", "06:00")
        assertTrue(resolver.contains(malam, wib(22, 0)))
        assertTrue(resolver.contains(malam, wib(2, 15)))
        assertTrue(resolver.contains(malam, wib(5, 59)))
        assertFalse(resolver.contains(malam, wib(6, 0)))
    }

    @Test
    fun `a full day shift covers every time`() {
        val full = shift("Full", "00:00", "00:00")
        assertTrue(resolver.contains(full, wib(3, 0)))
        assertTrue(resolver.contains(full, wib(17, 0)))
    }

    @Test
    fun `inactive shifts are ignored`() {
        val active = shift("Siang", "14:00", "22:00")
        val inactive = active.copy(id = "old", name = "Pagi", activeStatus = false)
        assertNull(resolver.resolve(wib(9, 0), listOf(inactive)))
    }

    @Test
    fun `timezone is honoured`() {
        val utcShift = shift("UTC Day", "06:00", "14:00", tz = "UTC")
        // 18:23 WIB == 11:23 UTC -> inside UTC 06:00-14:00
        assertTrue(resolver.contains(utcShift, wib(18, 23)))
    }

    @Test
    fun `no matching shift returns null for fallback`() {
        val pagi = shift("Pagi", "06:00", "14:00")
        assertNull(resolver.resolve(wib(18, 0), listOf(pagi)))
    }
}
