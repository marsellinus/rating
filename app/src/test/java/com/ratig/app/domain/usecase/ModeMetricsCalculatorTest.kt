package com.ratig.app.domain.usecase

import com.ratig.app.core.timing.modes.ModeResponseType
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the offline metric calculators. These mirror the
 * server-authoritative formulas of `finalize_test_session` v2 exactly, so any
 * drift here means local results would disagree with the server.
 *
 * Server formulas (CONTRACT-2):
 *  - correct_response_rate = n_correct / n_target
 *  - false_alarm_rate      = n_response_to_distractor / n_distractor
 *  - omission_rate         = n_target_with_no_response / n_target
 *  - accuracy_rate         = (n_correct + n_distractor_ignored) / (n_target + n_distractor)
 */
class ModeMetricsCalculatorTest {

    private val calc = ModeMetricsCalculator()

    private fun trial(
        number: Int,
        status: TrialStatus,
        rtMs: Long?,
        kind: String? = null,
        target: Boolean? = null,
        response: String? = null,
        correct: Boolean? = null,
        missed: Boolean = false,
        falseStart: Boolean = false,
    ) = TrialRecord(
        sessionId = "s",
        trialNumber = number,
        trialStatus = status,
        reactionTimeMs = rtMs,
        stimulusKind = kind,
        isTarget = target,
        responseType = response,
        responseCorrect = correct,
        missedResponse = missed,
        falseStart = falseStart,
    )

    /** Mirrors the E2E fixture validated against the live database RPC v2. */
    private fun rgbFixture(): List<TrialRecord> = buildList {
        repeat(6) { i ->
            add(trial(i + 1, TrialStatus.VALID, 320L + i * 5, "rgb_green", true, ModeResponseType.CORRECT, true))
        }
        add(trial(7, TrialStatus.VALID, 280, "rgb_red", false, ModeResponseType.WRONG, false))
        add(trial(8, TrialStatus.VALID, 1500, "rgb_red", false, ModeResponseType.LATE, false))
        add(trial(9, TrialStatus.VALID, null, "rgb_blue", false, ModeResponseType.NONE, null))
        add(trial(10, TrialStatus.VALID, null, "rgb_blue", false, ModeResponseType.NONE, null))
        add(trial(11, TrialStatus.MISSED, null, "rgb_green", true, ModeResponseType.NONE, null, missed = true))
    }

    @Test
    fun `rgb fixture rates match server-verified RPC v2 values`() {
        val m = calc.compute(rgbFixture(), TestMode.RGB_RANDOM)

        assertEquals(11, m.totalTrials)
        assertEquals(7, m.targetCount)
        assertEquals(4, m.distractorCount)
        assertEquals(6, m.correctCount)
        assertEquals(1, m.omissionCount)
        assertEquals(2, m.correctRejectionCount)

        // Server verified: accuracy 8/11, correct 6/7, false alarm 2/4, omission 1/7.
        assertEquals(8.0 / 11.0, m.accuracyRate!!, 1e-9)
        assertEquals(6.0 / 7.0, m.correctResponseRate!!, 1e-9)
        assertEquals(2.0 / 4.0, m.falseAlarmRate!!, 1e-9)
        assertEquals(1.0 / 7.0, m.omissionRate!!, 1e-9)
    }

    @Test
    fun `rt stats use only correct trials inside plausible range`() {
        val trials = listOf(
            trial(1, TrialStatus.VALID, 300, "rgb_green", true, ModeResponseType.CORRECT, true),
            trial(2, TrialStatus.VALID, 400, "rgb_green", true, ModeResponseType.CORRECT, true),
            // Wrong response with RT: must not enter RT statistics.
            trial(3, TrialStatus.VALID, 250, "rgb_red", false, ModeResponseType.WRONG, false),
            // Correct but implausibly slow: excluded artifact, still counted.
            trial(4, TrialStatus.VALID, 30_000, "rgb_green", true, ModeResponseType.CORRECT, true),
            trial(5, TrialStatus.MISSED, null, "rgb_green", true, ModeResponseType.NONE, null, missed = true),
        )
        val m = calc.compute(trials, TestMode.RGB_RANDOM)

        assertEquals(350.0, m.meanReactionTimeMs!!, 1e-9)
        assertEquals(350.0, m.medianReactionTimeMs!!, 1e-9)
        assertEquals(300L, m.minReactionTimeMs)
        assertEquals(400L, m.maxReactionTimeMs)
        assertEquals(1, m.excludedArtifactCount)
    }

    @Test
    fun `classic sessions produce null rates`() {
        val trials = listOf(
            trial(1, TrialStatus.VALID, 300),
            trial(2, TrialStatus.VALID, 400),
            trial(3, TrialStatus.MISSED, null, missed = true),
        )
        val m = calc.compute(trials, TestMode.CLASSIC)

        assertNull(m.accuracyRate)
        assertNull(m.correctResponseRate)
        assertNull(m.falseAlarmRate)
        assertNull(m.omissionRate)
        assertEquals(0, m.targetCount)
        assertEquals(0, m.distractorCount)
    }

    @Test
    fun `empty trial list yields null statistics`() {
        val m = calc.compute(emptyList(), TestMode.RGB_RANDOM)

        assertEquals(0, m.totalTrials)
        assertNull(m.meanReactionTimeMs)
        assertNull(m.medianReactionTimeMs)
        assertNull(m.minReactionTimeMs)
        assertNull(m.maxReactionTimeMs)
        assertNull(m.standardDeviationMs)
        assertNull(m.accuracyRate)
    }

    @Test
    fun `classic calculator keeps reference-band behavior`() {
        val rtTrials = listOf(
            trial(1, TrialStatus.VALID, 300),
            trial(2, TrialStatus.VALID, 310),
            trial(3, TrialStatus.VALID, 320),
            trial(4, TrialStatus.VALID, 330),
            trial(5, TrialStatus.MISSED, null, missed = true),
            trial(6, TrialStatus.FALSE_START, null, falseStart = true),
        )
        val rule = com.ratig.app.domain.model.RuleConfig.defaultUnvalidated()
        val m = MetricsCalculator().compute(rtTrials, rule)

        assertEquals(4, m.validTrialCount)
        assertEquals(315.0, m.meanReactionTimeMs!!, 1e-9)
        assertEquals(315.0, m.medianReactionTimeMs!!, 1e-9)
        assertEquals(300L, m.minReactionTimeMs)
        assertEquals(330L, m.maxReactionTimeMs)
        assertEquals(1, m.missedResponseCount)
        assertEquals(1, m.falseStartCount)
        assertEquals(0, m.slowResponseCount)

        // Mean 315 ms falls in the reference 240-<410 band.
        val outcome = FatigueClassifier().classify(m, rule)
        assertEquals("BAND_2", outcome.bandCode)
    }
}
