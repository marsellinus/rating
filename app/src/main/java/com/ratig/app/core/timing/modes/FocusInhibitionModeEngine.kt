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

    override fun drawStimulus(): ModeStimulus.FocusCircle {
        val isNoGo = nextRandomBoolean(config.noGoRatio)
        return ModeStimulus.FocusCircle(isGo = !isNoGo)
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
