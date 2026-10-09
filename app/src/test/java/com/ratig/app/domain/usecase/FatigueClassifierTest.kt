package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.ClassificationLogic
import com.ratig.app.domain.model.RuleBand
import com.ratig.app.domain.model.RuleConfig
import com.ratig.app.domain.model.SlowCountBand
import com.ratig.app.domain.model.TestMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FatigueClassifier]. Covers the three classification logics,
 * the insufficient-data gate, and the "not yet validated" fallbacks that the UI
 * relies on to show a placeholder instead of a misleading band.
 */
class FatigueClassifierTest {

    private val classifier = FatigueClassifier()

    private fun bands() = listOf(
        RuleBand(0, 240, "BAND_1", "Baik", 1),
        RuleBand(240, 410, "BAND_2", "Waspada", 2),
        RuleBand(410, 580, "BAND_3", "Lelah", 3),
        RuleBand(580, null, "BAND_4", "Sangat lelah", 4),
    )

    private fun metrics(
        validCount: Int = 10,
        mean: Double? = 300.0,
        median: Double? = 300.0,
        slowCount: Int = 0,
    ) = TestMetrics(
        totalTrials = validCount,
        validTrialCount = validCount,
        invalidTrialCount = 0,
        missedResponseCount = 0,
        falseStartCount = 0,
        meanReactionTimeMs = mean,
        medianReactionTimeMs = median,
        slowResponseCount = slowCount,
    )

    private fun config(
        logic: ClassificationLogic = ClassificationLogic.MEAN_MS_BAND,
        bands: List<RuleBand> = bands(),
        slowBands: List<SlowCountBand> = emptyList(),
        minValid: Int = 4,
    ) = RuleConfig(
        bands = bands,
        logic = logic,
        slowCountBands = slowBands,
        minValidTrials = minValid,
    )

    @Test
    fun `mean band selects the matching range and reports severity`() {
        val out = classifier.classify(metrics(mean = 300.0), config())
        assertEquals("BAND_2", out.bandCode)
        assertEquals("Waspada", out.bandLabel)
        assertEquals(2, out.severity)
        assertFalse(out.needsValidation)
        assertTrue(out.explanation.contains("300.0 ms"))
    }

    @Test
    fun `band boundary is inclusive on min and exclusive on max`() {
        assertEquals("BAND_2", classifier.classify(metrics(mean = 240.0), config()).bandCode)
        assertEquals("BAND_3", classifier.classify(metrics(mean = 410.0), config()).bandCode)
        assertEquals("BAND_4", classifier.classify(metrics(mean = 580.0), config()).bandCode)
    }

    @Test
    fun `median logic uses the median metric`() {
        val out = classifier.classify(
            metrics(mean = 100.0, median = 500.0),
            config(logic = ClassificationLogic.MEDIAN_MS_BAND),
        )
        assertEquals("BAND_3", out.bandCode)
        assertTrue(out.explanation.contains("median"))
    }

    @Test
    fun `slow count logic picks the highest band reached`() {
        val slowBands = listOf(
            SlowCountBand(1, "SLOW_1", "Ada lambat", 1),
            SlowCountBand(3, "SLOW_3", "Banyak lambat", 2),
            SlowCountBand(6, "SLOW_6", "Kritis", 3),
        )
        val out = classifier.classify(
            metrics(slowCount = 4),
            config(logic = ClassificationLogic.SLOW_COUNT_BAND, slowBands = slowBands),
        )
        assertEquals("SLOW_3", out.bandCode)
        assertEquals(2, out.severity)
    }

    @Test
    fun `insufficient valid trials short-circuits before classification`() {
        val out = classifier.classify(metrics(validCount = 2), config(minValid = 4))
        assertEquals(ClassificationOutcome.INSUFFICIENT_DATA, out.bandCode)
        assertEquals(-1, out.severity)
        assertFalse(out.needsValidation)
    }

    @Test
    fun `missing metric yields insufficient data not a wrong band`() {
        val out = classifier.classify(metrics(mean = null), config())
        assertEquals(ClassificationOutcome.INSUFFICIENT_DATA, out.bandCode)
    }

    @Test
    fun `value outside every configured band reports not configured`() {
        val out = classifier.classify(
            metrics(mean = 900.0),
            config(bands = listOf(RuleBand(0, 240, "BAND_1", "Baik", 1))),
        )
        assertEquals(ClassificationOutcome.NOT_CONFIGURED, out.bandCode)
        assertTrue(out.needsValidation)
    }

    @Test
    fun `placeholder bands are flagged as needing validation`() {
        val out = classifier.classify(metrics(mean = 300.0), RuleConfig.defaultUnvalidated())
        assertEquals(ClassificationOutcome.UNVALIDATED_LABEL, out.bandLabel)
        assertTrue(out.needsValidation)
    }

    @Test
    fun `slow count logic without slow bands reports not configured`() {
        val out = classifier.classify(
            metrics(slowCount = 5),
            config(logic = ClassificationLogic.SLOW_COUNT_BAND, slowBands = emptyList()),
        )
        assertEquals(ClassificationOutcome.NOT_CONFIGURED, out.bandCode)
    }
}
