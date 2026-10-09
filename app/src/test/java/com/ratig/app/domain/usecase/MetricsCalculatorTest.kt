package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.RuleBand
import com.ratig.app.domain.model.RuleConfig
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [MetricsCalculator]. These pin the documented exclusion rules
 * that the server-authoritative `finalize_test_session` also enforces, so local
 * and server metrics cannot silently diverge.
 */
class MetricsCalculatorTest {

    private val calc = MetricsCalculator()

    private fun config(excludeAbove: Long? = 2000L, slowThreshold: Long = 580L) = RuleConfig(
        bands = listOf(
            RuleBand(0, 240, "B1", "Cepat", 1),
            RuleBand(240, 410, "B2", "Normal", 2),
            RuleBand(410, 580, "B3", "Lambat", 3),
            RuleBand(580, null, "B4", "Sangat lambat", 4),
        ),
        slowResponseThresholdMs = slowThreshold,
        excludeReactionAboveMs = excludeAbove,
    )

    private fun trial(
        number: Int,
        status: TrialStatus,
        rtMs: Long?,
        falseStart: Boolean = false,
        missed: Boolean = false,
    ) = TrialRecord(
        sessionId = "s1",
        trialNumber = number,
        reactionTimeMs = rtMs,
        trialStatus = status,
        falseStart = falseStart,
        missedResponse = missed,
    )

    @Test
    fun `mean median and sample stddev computed over valid trials`() {
        val metrics = calc.compute(
            listOf(
                trial(1, TrialStatus.VALID, 300),
                trial(2, TrialStatus.VALID, 400),
                trial(3, TrialStatus.VALID, 500),
            ),
            config(),
        )
        assertEquals(3, metrics.validTrialCount)
        assertEquals(400.0, metrics.meanReactionTimeMs!!, 1e-9)
        assertEquals(400.0, metrics.medianReactionTimeMs!!, 1e-9)
        // sample stddev of {300,400,500} = 100
        assertEquals(100.0, metrics.standardDeviationMs!!, 1e-9)
    }

    @Test
    fun `even count median averages the two middle values`() {
        val metrics = calc.compute(
            listOf(
                trial(1, TrialStatus.VALID, 100),
                trial(2, TrialStatus.VALID, 200),
                trial(3, TrialStatus.VALID, 300),
                trial(4, TrialStatus.VALID, 400),
            ),
            config(),
        )
        assertEquals(250.0, metrics.medianReactionTimeMs!!, 1e-9)
    }

    @Test
    fun `invalid false start and missed trials never contribute reaction times`() {
        val metrics = calc.compute(
            listOf(
                trial(1, TrialStatus.VALID, 350),
                trial(2, TrialStatus.INVALID, null, falseStart = true),
                trial(3, TrialStatus.INVALID, null, missed = true),
            ),
            config(),
        )
        assertEquals(3, metrics.totalTrials)
        assertEquals(1, metrics.validTrialCount)
        assertEquals(2, metrics.invalidTrialCount)
        assertEquals(1, metrics.falseStartCount)
        assertEquals(1, metrics.missedResponseCount)
        assertEquals(350.0, metrics.meanReactionTimeMs!!, 1e-9)
    }

    @Test
    fun `reaction times above the artifact ceiling are excluded but counted`() {
        val metrics = calc.compute(
            listOf(
                trial(1, TrialStatus.VALID, 300),
                trial(2, TrialStatus.VALID, 5000),
            ),
            config(excludeAbove = 2000L),
        )
        assertEquals(1, metrics.validTrialCount)
        assertEquals(1, metrics.excludedArtifactCount)
        assertEquals(300.0, metrics.meanReactionTimeMs!!, 1e-9)
        assertEquals(300L, metrics.maxReactionTimeMs)
    }

    @Test
    fun `null artifact ceiling keeps every valid trial`() {
        val metrics = calc.compute(
            listOf(
                trial(1, TrialStatus.VALID, 300),
                trial(2, TrialStatus.VALID, 9000),
            ),
            config(excludeAbove = null),
        )
        assertEquals(2, metrics.validTrialCount)
        assertEquals(0, metrics.excludedArtifactCount)
        assertEquals(9000L, metrics.maxReactionTimeMs)
    }

    @Test
    fun `slow response count uses inclusive threshold`() {
        val metrics = calc.compute(
            listOf(
                trial(1, TrialStatus.VALID, 579),
                trial(2, TrialStatus.VALID, 580),
                trial(3, TrialStatus.VALID, 581),
            ),
            config(slowThreshold = 580L),
        )
        assertEquals(2, metrics.slowResponseCount)
    }

    @Test
    fun `stddev is null below two samples and mean null with no valid trials`() {
        val single = calc.compute(listOf(trial(1, TrialStatus.VALID, 300)), config())
        assertNull(single.standardDeviationMs)

        val none = calc.compute(listOf(trial(1, TrialStatus.INVALID, null, missed = true)), config())
        assertNull(none.meanReactionTimeMs)
        assertNull(none.medianReactionTimeMs)
        assertNull(none.minReactionTimeMs)
        assertEquals(0, none.validTrialCount)
    }

    @Test
    fun `session duration is passed through untouched`() {
        val metrics = calc.compute(
            listOf(trial(1, TrialStatus.VALID, 300)),
            config(),
            sessionDurationMs = 12_345L,
        )
        assertEquals(12_345L, metrics.sessionDurationMs)
    }
}
