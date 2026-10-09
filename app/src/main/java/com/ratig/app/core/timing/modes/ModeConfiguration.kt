package com.ratig.app.core.timing.modes

import com.ratig.app.core.json.JsonCodec
import com.ratig.app.domain.model.TestMode
import kotlinx.serialization.Serializable

/**
 * Per-mode execution configuration (admin editable). Serialized as the nested
 * `mode` object inside the protocol `configuration` jsonb, and frozen per
 * session in `test_sessions.mode_config`.
 *
 * The class is a SUPERSET-friendly envelope: parsing only reads the `mode`
 * key, so the same JSON works for classic protocols (mode omitted -> defaults)
 * and for mode protocols.
 */
@Serializable
data class ModeConfiguration(
    /** RGB_RANDOM: which color (+ its distinct shape/symbol) is the target. */
    val targetColor: String = RgbColor.GREEN.wire,
    /** RANDOM_BUTTON: the one symbol the worker must tap, e.g. "A". */
    val targetSymbol: String = "A",
    /** RGB_RANDOM: randomize the screen slot of the stimulus each trial. */
    val randomizePosition: Boolean = false,
    /** RANDOM_BUTTON: grid is [gridSize] x [gridSize] buttons (UI min 64dp). */
    val gridSize: Int = 3,
    /**
     * RANDOM_BUTTON: fraction of the non-target cells filled with distractor
     * symbols (0.0 - 0.9); remaining cells stay blank.
     */
    val distractorRatio: Double = 0.5,
    /** FOCUS_INHIBITION: probability a trial is a no-go (red circle). */
    val noGoRatio: Double = 0.3,
    /** Extra inter-trial rest after every trial (ms). */
    val restMs: Long = 300,
    /** How a pre-stimulus tap is handled; see [FalseStartRule]. */
    val falseStartRule: String = FalseStartRule.CONSUME_TRIAL.wire,
    /** Validity rule: reaction times below this are device artifacts. */
    val minPlausibleReactionMs: Long = 80L,
    /** Validity rule: reaction times above this are excluded artifacts. */
    val maxPlausibleReactionMs: Long = 2000L,
) {
    val rgbTargetColor: RgbColor get() = RgbColor.fromWire(targetColor)
    val falseStart: FalseStartRule get() = FalseStartRule.fromRaw(falseStartRule)

    /** Clamps every field into its documented range (defensive admin input). */
    fun sanitized(): ModeConfiguration = copy(
        targetColor = rgbTargetColor.wire,
        targetSymbol = targetSymbol.trim().uppercase().take(1).ifEmpty { "A" },
        gridSize = gridSize.coerceIn(MIN_GRID, MAX_GRID),
        distractorRatio = distractorRatio.coerceIn(0.0, 0.9),
        noGoRatio = noGoRatio.coerceIn(0.0, 0.9),
        restMs = restMs.coerceIn(0, 5_000),
        falseStartRule = falseStart.wire,
        minPlausibleReactionMs = minPlausibleReactionMs.coerceIn(0, 1_000),
        maxPlausibleReactionMs = maxOf(maxPlausibleReactionMs, minPlausibleReactionMs + 100),
    )

    companion object {
        const val MIN_GRID = 2
        const val MAX_GRID = 5
        val DEFAULT = ModeConfiguration()

        /**
         * Resolves the mode configuration out of a full protocol/session
         * configuration JSON (`{"interTrialDelayMs":..., "mode":{...}}`).
         * Falls back to defaults for classic protocols or unparsable JSON.
         */
        fun fromConfigurationJson(json: String?): ModeConfiguration {
            if (json.isNullOrBlank()) return DEFAULT
            return runCatching { JsonCodec.json.decodeFromString<Envelope>(json).mode }
                .getOrNull() ?: DEFAULT
        }
    }

    @Serializable
    private data class Envelope(val mode: ModeConfiguration? = null)
}

/** How a tap before the stimulus is scored (modeConfig.falseStartRule). */
enum class FalseStartRule(val wire: String) {
    /** Early tap ends the trial immediately as FALSE_START (classic behavior). */
    CONSUME_TRIAL("consume_trial"),

    /** Early taps are ignored; the trial is only scored after the stimulus. */
    IGNORE_TAP("ignore_tap"),
    ;

    companion object {
        fun fromRaw(raw: String?): FalseStartRule =
            entries.firstOrNull { it.wire == raw || it.name.equals(raw, ignoreCase = true) }
                ?: CONSUME_TRIAL
    }
}

/** Response classification values stored in test_trials.response_type. */
object ModeResponseType {
    const val CORRECT = "correct"
    const val WRONG = "wrong"
    const val LATE = "late"
    const val EARLY = "early"
    const val NONE = "none"
}

/** User-facing mode names (Bahasa Indonesia; no "Stroop" wording anywhere). */
fun TestMode.modeLabel(): String = when (this) {
    TestMode.CLASSIC -> "Waktu Reaksi Klasik"
    TestMode.RGB_RANDOM -> "Warna Acak (RGB)"
    TestMode.RANDOM_BUTTON -> "Kisi Tombol Acak"
    TestMode.FOCUS_INHIBITION -> "Fokus & Inhibisi"
}
