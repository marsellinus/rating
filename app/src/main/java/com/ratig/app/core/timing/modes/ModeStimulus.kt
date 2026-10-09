package com.ratig.app.core.timing.modes

import kotlinx.serialization.Serializable

/**
 * Stimulus colors of RGB_RANDOM. Color is NEVER the only cue: every color has
 * a fixed, distinct shape overlay AND symbol so color-blind workers can run
 * the test safely. The mapping is frozen here so instructions and results
 * always describe the same stimulus.
 */
@Serializable
enum class RgbColor(
    val wire: String,
    val label: String,
    val shape: RgbShape,
    val symbol: String,
) {
    RED("red", "MERAH", RgbShape.TRIANGLE, "\u2715"),
    GREEN("green", "HIJAU", RgbShape.CIRCLE, "\u2713"),
    BLUE("blue", "BIRU", RgbShape.DIAMOND, "\u2605"),
    ;

    companion object {
        fun fromWire(raw: String?): RgbColor =
            entries.firstOrNull { it.wire == raw?.lowercase() || it.name.equals(raw, ignoreCase = true) }
                ?: GREEN
    }
}

/** Distinct shape overlays (drawn by the UI, one per color). */
enum class RgbShape(val label: String) {
    CIRCLE("LINGKARAN"),
    TRIANGLE("SEGITIGA"),
    DIAMOND("BELAH KETUPAT"),
}

/**
 * Stimulus prepared for the current trial. Drawn ONCE from the seeded RNG
 * before the pre-stimulus window opens; never re-randomized after a response
 * is recorded.
 *
 * [kind] is the value stored in test_trials.stimulus_kind.
 */
sealed class ModeStimulus {
    abstract val kind: String
    abstract val isTarget: Boolean

    /** Layout slot (row-major index); null = centered, no slot layout. */
    abstract val position: Int?

    /** RGB_RANDOM: colored square + distinct shape + distinct symbol. */
    data class Rgb(
        val color: RgbColor,
        override val position: Int?,
        override val isTarget: Boolean,
    ) : ModeStimulus() {
        override val kind: String get() = "rgb_${color.wire}"
    }

    /**
     * RANDOM_BUTTON: one cell holds the target symbol, up to k cells hold
     * distractor symbols, the rest stay blank. Every trial contains the
     * target, so isTarget is always true.
     */
    data class Grid(
        val symbols: List<String?>,
        val targetPosition: Int,
        val targetSymbol: String,
    ) : ModeStimulus() {
        override val kind: String get() = "button_$targetSymbol"
        override val isTarget: Boolean get() = true
        override val position: Int get() = targetPosition
    }

    /** FOCUS_INHIBITION: green circle = go, red circle = no-go. */
    data class FocusCircle(val isGo: Boolean) : ModeStimulus() {
        override val kind: String get() = if (isGo) "circle_go" else "circle_nogo"
        override val isTarget: Boolean get() = isGo
        override val position: Int? get() = null
    }
}

/** A tap during a mode run. [position] is the grid cell index; null = anywhere. */
data class ModeTap(val position: Int? = null)
