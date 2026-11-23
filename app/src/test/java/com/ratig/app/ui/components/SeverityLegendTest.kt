package com.ratig.app.ui.components

import com.ratig.app.domain.model.RuleBand
import com.ratig.app.domain.model.SeverityScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The legend must state the ranges that actually apply. These tests pin the
 * printed range format so the four bands stay contiguous - a gap or an overlap
 * would make the legend contradict the classification.
 */
class SeverityLegendTest {

    @Test
    fun `a bounded band prints the last included millisecond`() {
        // maxMsExclusive = 240 means 239 ms is still in this band.
        assertEquals("0–239 ms", RuleBand(0, 240, "B1", "Normal", 1).rangeLabel())
        assertEquals("240–400 ms", RuleBand(240, 401, "B2", "Ringan", 2).rangeLabel())
    }

    @Test
    fun `the open ended band prints a lower bound only`() {
        assertEquals("≥ 581 ms", RuleBand(581, null, "B4", "Berat", 4).rangeLabel())
    }

    @Test
    fun `consecutive bands leave no gap and no overlap`() {
        val bands = listOf(
            RuleBand(0, 240, "B1", "Normal", 1),
            RuleBand(240, 401, "B2", "Ringan", 2),
            RuleBand(401, 581, "B3", "Sedang", 3),
            RuleBand(581, null, "B4", "Berat", 4),
        )
        bands.zipWithNext().forEach { (lower, upper) ->
            assertEquals(
                "band ${lower.code} must end exactly where ${upper.code} starts",
                lower.maxMsExclusive,
                upper.minMs,
            )
        }
    }

    @Test
    fun `every severity has a distinct operational meaning`() {
        val meanings = listOf(
            SeverityScale.NORMAL,
            SeverityScale.MILD,
            SeverityScale.MODERATE,
            SeverityScale.SEVERE,
        ).map { bandMeaning(it) }
        assertEquals(4, meanings.toSet().size)
        meanings.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `placeholder labels fall back to the canonical band name`() {
        // The seeded rule ships "Perlu validasi" on every band. Showing that four
        // times tells the reader nothing, so the legend derives the real name.
        assertEquals("Normal", bandDisplayName("Perlu validasi", SeverityScale.NORMAL))
        assertEquals("Ringan", bandDisplayName("Perlu validasi", SeverityScale.MILD))
        assertEquals("Sedang", bandDisplayName("Perlu validasi", SeverityScale.MODERATE))
        assertEquals("Berat", bandDisplayName("Perlu validasi", SeverityScale.SEVERE))
        assertEquals("Normal", bandDisplayName(null, SeverityScale.NORMAL))
        assertEquals("Sedang", bandDisplayName("  ", SeverityScale.MODERATE))
    }

    @Test
    fun `a real organization label is shown as written`() {
        assertEquals("Fatigue Ringan", bandDisplayName("Fatigue Ringan", SeverityScale.MILD))
        assertEquals("Aman", bandDisplayName("Aman", SeverityScale.NORMAL))
    }

    @Test
    fun `unknown severity is labelled rather than left blank`() {
        assertEquals("Kategori belum ditetapkan aturan.", bandMeaning(SeverityScale.UNKNOWN))
    }
}
