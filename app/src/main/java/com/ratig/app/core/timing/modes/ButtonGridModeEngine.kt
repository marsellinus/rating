package com.ratig.app.core.timing.modes

import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.domain.model.TrialStatus
import kotlinx.coroutines.CoroutineScope

/**
 * RANDOM_BUTTON: each trial fills a gridSize x gridSize grid from the seeded
 * RNG - exactly one cell shows the target symbol, up to
 * `distractorRatio * (cells - 1)` cells show distinct distractor symbols, the
 * rest stay blank. The worker taps ONLY the target-symbol button (min 64dp is
 * enforced by the UI).
 *
 * Tapping the target cell -> correct; tapping any other cell (distractor or
 * blank) -> wrong (false alarm); no tap -> omission.
 */
class ButtonGridModeEngine(
    clock: MonotonicClock,
    seed: Long,
    private val modeConfig: ModeConfiguration,
    scope: CoroutineScope,
) : ModeEngineBase(clock, seed, scope) {

    private val config = modeConfig.sanitized()
    private val targetSymbol: String = config.targetSymbol

    override fun drawStimulus(): ModeStimulus.Grid {
        val cells = config.gridSize * config.gridSize
        val order = shuffledIndices(cells)
        val targetPosition = order.first()

        val symbols = arrayOfNulls<String>(cells)
        symbols[targetPosition] = targetSymbol

        val distractorCount = distractorCount(config.gridSize, config.distractorRatio)
        if (distractorCount > 0) {
            val pool = SYMBOL_ALPHABET.filter { it != targetSymbol }
            val poolOrder = shuffledIndices(pool.size)
            order.drop(1).take(distractorCount).forEachIndexed { index, cell ->
                symbols[cell] = pool[poolOrder[index % pool.size]]
            }
        }

        return ModeStimulus.Grid(
            symbols = symbols.toList(),
            targetPosition = targetPosition,
            targetSymbol = targetSymbol,
        )
    }

    override fun classifyInTime(
        stimulus: ModeStimulus,
        tap: ModeTap,
        reactionTimeMs: Long,
        shownAtNanos: Long,
        tapAtNanos: Long,
    ): ModeEngineTrial {
        val grid = stimulus as ModeStimulus.Grid
        // Taps without a cell position never occur on the grid screen; treat
        // them as wrong (they did not hit the target button).
        val correct = tap.position != null && tap.position == grid.targetPosition
        return ModeEngineTrial(
            trialNumber = 0,
            stimulusKind = grid.kind,
            isTarget = true,
            responseType = if (correct) ModeResponseType.CORRECT else ModeResponseType.WRONG,
            responseCorrect = correct,
            trialStatus = TrialStatus.VALID,
            reactionTimeMs = reactionTimeMs,
            stimulusAtMonotonicNs = shownAtNanos,
            tapAtMonotonicNs = tapAtNanos,
        )
    }

    private companion object {
        val SYMBOL_ALPHABET = listOf("A", "B", "C", "D", "E", "F")
    }
}
