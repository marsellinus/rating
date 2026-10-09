package com.ratig.app.domain.usecase

import com.ratig.app.core.timing.modes.ModeConfiguration
import com.ratig.app.core.timing.modes.ModeResponseType
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TrialRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Per-mode observation metrics computed OFFLINE from recorded trials. Pure
 * computation (no Android, no I/O): mirrors the server-authoritative rate
 * formulas of `finalize_test_session` v2 EXACTLY so local results match the
 * server when connectivity returns.
 *
 * Server formulas (CONTRACT-2):
 *  - correct_response_rate = n_correct / n_target
 *  - false_alarm_rate      = n_response_to_distractor / n_distractor
 *        (response_type in correct|wrong|late)
 *  - omission_rate         = n_target_with_no_response / n_target
 *        (response_type='none' or missed)
 *  - accuracy_rate         = (n_correct + n_distractor_ignored)
 *                            / (n_target + n_distractor)
 *
 * RT statistics use ONLY trials with response_correct == true AND a reaction
 * time inside the plausible range ([ModeConfiguration.minPlausibleReactionMs]
 * .. [ModeConfiguration.maxPlausibleReactionMs]). Invalid trials NEVER enter
 * RT statistics; excluded artifacts stay visible in the counts.
 *
 * For CLASSIC sessions (isTarget/response columns all null) every denominator
 * is zero and all rates are null - classic metrics come from
 * [MetricsCalculator] instead.
 */
@Singleton
class ModeMetricsCalculator @Inject constructor() {

    fun compute(
        trials: List<TrialRecord>,
        mode: TestMode,
        config: ModeConfiguration = ModeConfiguration.DEFAULT,
    ): ModeMetrics {
        val targets = trials.filter { it.isTarget == true }
        val distractors = trials.filter { it.isTarget == false }

        val nTarget = targets.size
        val nDistractor = distractors.size
        val nCorrect = trials.count { it.responseType == ModeResponseType.CORRECT }
        val nDistractorResponded = distractors.count { it.responseType in RESPONDED_TYPES }
        val nDistractorIgnored = distractors.count { it.responseType == ModeResponseType.NONE }
        val nTargetNoResponse = targets.count {
            it.responseType == ModeResponseType.NONE || it.missedResponse
        }

        val earlyCount = trials.count { it.responseType == ModeResponseType.EARLY }
        val wrongCount = trials.count { it.responseType == ModeResponseType.WRONG }
        val lateCount = trials.count { it.responseType == ModeResponseType.LATE }

        val correctWithRt = trials.filter {
            it.responseCorrect == true && it.reactionTimeMs != null
        }
        val inRange = correctWithRt.filter {
            val rt = it.reactionTimeMs!!
            rt >= config.minPlausibleReactionMs && rt <= config.maxPlausibleReactionMs
        }
        val excludedArtifacts = correctWithRt.size - inRange.size
        val rts = inRange.map { it.reactionTimeMs!! }.sorted()

        return ModeMetrics(
            totalTrials = trials.size,
            targetCount = nTarget,
            distractorCount = nDistractor,
            correctCount = nCorrect,
            wrongCount = wrongCount,
            lateCount = lateCount,
            earlyCount = earlyCount,
            omissionCount = nTargetNoResponse,
            correctRejectionCount = nDistractorIgnored,
            excludedArtifactCount = excludedArtifacts,
            meanReactionTimeMs = rts.averageOrNull(),
            medianReactionTimeMs = medianOf(rts),
            minReactionTimeMs = rts.firstOrNull(),
            maxReactionTimeMs = rts.lastOrNull(),
            standardDeviationMs = sampleStdDev(rts),
            accuracyRate = ratio(nCorrect + nDistractorIgnored, nTarget + nDistractor),
            correctResponseRate = ratio(nCorrect, nTarget),
            falseAlarmRate = ratio(nDistractorResponded, nDistractor),
            omissionRate = ratio(nTargetNoResponse, nTarget),
            testMode = mode,
        )
    }

    private fun ratio(numerator: Int, denominator: Int): Double? =
        if (denominator == 0) null else numerator.toDouble() / denominator.toDouble()

    private fun List<Long>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun medianOf(sorted: List<Long>): Double? {
        if (sorted.isEmpty()) return null
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2].toDouble()
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    private fun sampleStdDev(sorted: List<Long>): Double? {
        if (sorted.size < 2) return null
        val mean = sorted.average()
        val variance = sorted.sumOf { (it - mean) * (it - mean) } / (sorted.size - 1)
        return sqrt(variance)
    }

    private companion object {
        /** Any of these on a distractor trial counts as "responded to distractor". */
        val RESPONDED_TYPES = setOf(
            ModeResponseType.CORRECT,
            ModeResponseType.WRONG,
            ModeResponseType.LATE,
        )
    }
}

/** Offline/local counterpart of the per-mode columns in test_results. */
data class ModeMetrics(
    val totalTrials: Int,
    val targetCount: Int,
    val distractorCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val lateCount: Int,
    val earlyCount: Int,
    val omissionCount: Int,
    val correctRejectionCount: Int,
    val excludedArtifactCount: Int,
    val meanReactionTimeMs: Double?,
    val medianReactionTimeMs: Double?,
    val minReactionTimeMs: Long?,
    val maxReactionTimeMs: Long?,
    val standardDeviationMs: Double?,
    val accuracyRate: Double?,
    val correctResponseRate: Double?,
    val falseAlarmRate: Double?,
    val omissionRate: Double?,
    val testMode: TestMode,
)
