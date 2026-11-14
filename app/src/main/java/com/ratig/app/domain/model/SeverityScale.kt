package com.ratig.app.domain.model

/**
 * The single severity scale used across the whole app.
 *
 * Every screen that shows a coloured indicator — fatigue classification,
 * paramedic findings, fit-to-work verdict, mode response quality — maps onto
 * these five values, so a colour always means the same thing. Before this
 * existed the screening used 0..3 while the fatigue bands used 1..4, and a
 * "Hipertensi Berat" finding rendered orange instead of red.
 *
 * [UNKNOWN] is negative on purpose: "not measured" must never be confused with
 * "measured and normal".
 */
object SeverityScale {
    /** Nothing measured, or classification not applicable. Rendered neutral. */
    const val UNKNOWN = -1

    /** Within normal limits. Rendered neutral (not a health claim of "good"). */
    const val NORMAL = 1

    /** Mild deviation: green. */
    const val MILD = 2

    /** Moderate deviation: amber. */
    const val MODERATE = 3

    /** Severe deviation: red. */
    const val SEVERE = 4
}
