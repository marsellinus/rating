package com.ratig.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Paramedic screening captured next to the reaction test.
 *
 * Ported from the desktop RATIG application so the Android app produces the
 * same findings for the same numbers. All fields are nullable: the examiner may
 * legitimately record only the reaction test, and an absent measurement must
 * never be mistaken for a normal one.
 *
 * No value here is a diagnosis. The findings exist to support the examiner's
 * fit-to-work decision, which stays a human judgement.
 */
@Serializable
data class Vitals(
    /** Systolic blood pressure, mmHg. */
    val systolic: Int? = null,
    /** Diastolic blood pressure, mmHg. */
    val diastolic: Int? = null,
    /** Resting pulse, beats per minute. */
    val pulse: Int? = null,
    /** Breath alcohol concentration, percent (e.g. 0.02 = 0.02 %). */
    val alcoholPercent: Double? = null,
) {
    val hasAnyMeasurement: Boolean
        get() = systolic != null || diastolic != null || pulse != null || alcoholPercent != null
}

/** One screening finding, e.g. "Hypertensi Ringan" for a systolic of 150. */
@Serializable
data class VitalFinding(
    val kind: VitalKind,
    val label: String,
    /** 0 normal, 1 mild, 2 moderate, 3 severe; drives the shared colour scale. */
    val severity: Int,
)

@Serializable
enum class VitalKind {
    SYSTOLIC,
    DIASTOLIC,
    PULSE,
    ALCOHOL;

    val displayName: String
        get() = when (this) {
            SYSTOLIC -> "Tekanan darah sistolik"
            DIASTOLIC -> "Tekanan darah diastolik"
            PULSE -> "Nadi"
            ALCOHOL -> "Tes alkohol"
        }
}

/** Result of screening one worker: findings plus the overall impression. */
@Serializable
data class VitalsAssessment(
    val findings: List<VitalFinding> = emptyList(),
    /** Highest severity across all findings; -1 when nothing was measured. */
    val overallSeverity: Int = -1,
) {
    val hasFindings: Boolean get() = findings.isNotEmpty()
    val isMeasured: Boolean get() = overallSeverity >= 0
}

/**
 * Suggested disposition. The examiner records the real decision; this is a
 * recommendation derived from the recorded measurements so that a tired or
 * impaired worker is not waved through by oversight.
 */
@Serializable
enum class FitnessRecommendation {
    /** Nothing measured, or everything within normal limits. */
    FIT,

    /** Elevated findings or mild/moderate fatigue: proceed with a note. */
    FIT_WITH_NOTE,

    /** Severe findings: hold the worker pending a proper medical review. */
    NOT_FIT;

    val label: String
        get() = when (this) {
            FIT -> "Layak bekerja"
            FIT_WITH_NOTE -> "Layak dengan catatan"
            NOT_FIT -> "Tidak layak bekerja"
        }

    /** Severity used for the shared badge colour scale. */
    val severity: Int
        get() = when (this) {
            FIT -> 0
            FIT_WITH_NOTE -> 3
            NOT_FIT -> 4
        }
}
