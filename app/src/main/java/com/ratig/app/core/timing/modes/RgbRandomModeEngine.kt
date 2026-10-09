package com.ratig.app.core.timing.modes

import com.ratig.app.core.timing.MonotonicClock
import kotlinx.coroutines.CoroutineScope

/**
 * RGB_RANDOM: each trial shows one of the three colored squares. Every color
 * carries a fixed, distinct shape + symbol overlay (color is never the only
 * cue), the target combination comes from [ModeConfiguration.targetColor],
 * and the screen slot is optionally randomized per trial.
 *
 * Response rule: any screen tap while the stimulus is visible. Tap on the
 * target combination -> correct; tap on a distractor -> wrong (false alarm);
 * no tap on target -> omission; no tap on distractor -> correct rejection.
 */
class RgbRandomModeEngine(
    clock: MonotonicClock,
    seed: Long,
    private val modeConfig: ModeConfiguration,
    scope: CoroutineScope,
) : ModeEngineBase(clock, seed, scope) {

    private val colors = RgbColor.entries
    private val slotCount = SLOT_ROWS * SLOT_COLS

    override fun drawStimulus(): ModeStimulus.Rgb {
        val color = colors[nextRandom(colors.size)]
        val position = if (modeConfig.randomizePosition) nextRandom(slotCount) else null
        return ModeStimulus.Rgb(
            color = color,
            position = position,
            isTarget = color == modeConfig.rgbTargetColor,
        )
    }

    override fun classifyInTime(
        stimulus: ModeStimulus,
        tap: ModeTap,
        reactionTimeMs: Long,
        shownAtNanos: Long,
        tapAtNanos: Long,
    ): ModeEngineTrial {
        val rgb = stimulus as ModeStimulus.Rgb
        val correct = rgb.isTarget
        return ModeEngineTrial(
            trialNumber = 0,
            stimulusKind = rgb.kind,
            isTarget = rgb.isTarget,
            responseType = if (correct) ModeResponseType.CORRECT else ModeResponseType.WRONG,
            responseCorrect = correct,
            trialStatus = com.ratig.app.domain.model.TrialStatus.VALID,
            reactionTimeMs = reactionTimeMs,
            stimulusAtMonotonicNs = shownAtNanos,
            tapAtMonotonicNs = tapAtNanos,
        )
    }

    private companion object {
        const val SLOT_ROWS = 3
        const val SLOT_COLS = 3
    }
}
