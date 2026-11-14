package com.ratig.app.core.timing.modes

import com.ratig.app.core.timing.MonotonicClock
import kotlinx.coroutines.CoroutineScope

/**
 * FOCUS_INHIBITION (go / no-go): a green circle = go (tap), a red circle =
 * no-go (refrain). The no-go probability per trial comes from
 * [ModeConfiguration.noGoRatio]. Responses to no-go stimuli are commission
 * errors (false alarms); missing a go stimulus is an omission; withholding on
 * a no-go is a correct rejection.
 *
 * Labeling policy: this mode is called "Fokus & Inhibisi" in all UI copy. It
 * is an observation aid - NO psychometric or diagnostic equivalence is
 * claimed anywhere.
 */
class FocusInhibitionModeEngine(
    clock: MonotonicClock,
    seed: Long,
    private val modeConfig: ModeConfiguration,
    scope: CoroutineScope,
) : ModeEngineBase(clock, seed, scope) {

    private val config = modeConfig.sanitized()

    /** Counted go / no-go trials drawn in the current run. */
    private var goDrawn = 0
    private var noGoDrawn = 0

    override fun onRunReset() {
        goDrawn = 0
        noGoDrawn = 0
    }

    /**
     * Draws one circle. Random per trial, but coverage is guaranteed: the last
     * counted trial is forced to fill whichever category is still missing, so a
     * short session cannot end up with zero no-go trials. A "Focus & Inhibition"
     * test with no no-go stimulus would not test inhibition at all.
     */
    override fun drawStimulus(context: TrialContext): ModeStimulus.FocusCircle {
        if (context.isPractice) {
            // Practice never counts toward coverage.
            return ModeStimulus.FocusCircle(isGo = !nextRandomBoolean(config.noGoRatio))
        }

        val isLastCounted = context.trialNumber >= context.countedTotal
        val isGo: Boolean = when {
            // Force the missing category on the final trial.
            isLastCounted && noGoDrawn == 0 -> false
            isLastCounted && goDrawn == 0 -> true
            else -> !nextRandomBoolean(config.noGoRatio)
        }

        if (isGo) goDrawn++ else noGoDrawn++
        return ModeStimulus.FocusCircle(isGo = isGo)
    }

    override fun classifyInTime(
        stimulus: ModeStimulus,
        tap: ModeTap,
        reactionTimeMs: Long,
        shownAtNanos: Long,
        tapAtNanos: Long,
    ): ModeEngineTrial {
        val circle = stimulus as ModeStimulus.FocusCircle
        return ModeEngineTrial(
            trialNumber = 0,
            stimulusKind = circle.kind,
            isTarget = circle.isGo,
            responseType = if (circle.isGo) ModeResponseType.CORRECT else ModeResponseType.WRONG,
            responseCorrect = circle.isGo,
            trialStatus = com.ratig.app.domain.model.TrialStatus.VALID,
            reactionTimeMs = reactionTimeMs,
            stimulusAtMonotonicNs = shownAtNanos,
            tapAtMonotonicNs = tapAtNanos,
        )
    }
}
