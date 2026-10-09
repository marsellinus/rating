package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.RuleConfig
import com.ratig.app.domain.model.TestMetrics
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Pure metric computation from recorded trials.
 *
 * Rules (explicit, auditable - nothing is excluded silently):
 *  - FALSE_START / MISSED / INVALID trials never contribute reaction times.
 *  - VALID trials above [RuleConfig.excludeReactionAboveMs] are counted as
 *    invalid (device artifact) and excluded from RT statistics; the counts
 *    remain visible in the metrics so audits can recompute.
 *  - Slow response = valid RT >= [RuleConfig.slowResponseThresholdMs].
 *  - Std-dev uses the sample formula (n-1); null when n < 2.
 *  - Median is the average of the two middle values for even n.
 */
@Singleton
class MetricsCalculator @Inject constructor() {

    fun compute(
        trials: List<TrialRecord>,
        config: RuleConfig,
        sessionDurationMs: Long? = null,
    ): TestMetrics {
        val falseStartCount = trials.count { it.falseStart }
        val missedCount = trials.count { it.missedResponse }

        val explicitValid = trials.filter { it.trialStatus == TrialStatus.VALID && it.reactionTimeMs != null }

        val included = explicitValid.filter { isWithinPlausibleRange(it.reactionTimeMs!!, config) }
        val excludedArtifacts = explicitValid.size - included.size

        val rtTimes = included.map { it.reactionTimeMs!! }.sorted()
        val validCount = included.size
        val invalidCount = trials.size - validCount

        val mean = if (validCount > 0) rtTimes.average() else null
        val median = medianOf(rtTimes)
        val stdDev = sampleStdDev(rtTimes)

        return TestMetrics(
            totalTrials = trials.size,
            validTrialCount = validCount,
            invalidTrialCount = invalidCount,
            missedResponseCount = missedCount,
            falseStartCount = falseStartCount,
            minReactionTimeMs = rtTimes.firstOrNull(),
            maxReactionTimeMs = rtTimes.lastOrNull(),
            meanReactionTimeMs = mean,
            medianReactionTimeMs = median,
            standardDeviationMs = stdDev,
            slowResponseCount = rtTimes.count { it >= config.slowResponseThresholdMs },
            excludedArtifactCount = excludedArtifacts,
            sessionDurationMs = sessionDurationMs,
        )
    }

    private fun isWithinPlausibleRange(rtMs: Long, config: RuleConfig): Boolean {
        val above = config.excludeReactionAboveMs
        return above == null || rtMs <= above
    }

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
}
