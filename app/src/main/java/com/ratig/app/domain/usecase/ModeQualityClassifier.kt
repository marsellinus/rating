package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.SeverityScale
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Response-quality indicator for the three observation modes.
 *
 * The fatigue bands only apply to the Classic (SRT) mode. The other three modes
 * measure attention and inhibition instead, and their contract is explicit: they
 * are NEVER merged into a fatigue score. They still deserve the same coloured
 * indicator so the examiner can read any result screen the same way, so this
 * classifies *response quality* — using the shared [SeverityScale] for colour,
 * with wording that does not imply a fatigue diagnosis.
 *
 * Primary metric is accuracy, i.e. correct responses on targets plus correct
 * rejections of distractors, over all trials:
 *  - Button Grid has no distractors, so accuracy reduces to hit rate.
 *  - Focus Inhibition and Random Colour include distractors, so accuracy also
 *    captures impulsive errors.
 *
 * Accuracy alone can hide a worker who never responds at all, so a high
 * omission rate downgrades the band: silence is not good performance.
 */
@Singleton
class ModeQualityClassifier @Inject constructor() {

    /** Band for one mode result. [severity] is a [SeverityScale] value. */
    data class Quality(
        val label: String,
        val severity: Int,
        val explanation: String,
    )

    /**
     * Returns null for Classic sessions, where the fatigue classification is the
     * indicator instead.
     */
    fun classify(result: TestResult): Quality? {
        val mode = result.testMode ?: return null
        if (mode == TestMode.CLASSIC) return null

        val accuracy = result.accuracyRate
            ?: result.correctResponseRate
            ?: return Quality(
                label = "Tidak dinilai",
                severity = SeverityScale.UNKNOWN,
                explanation = "Metrik akurasi tidak tersedia untuk sesi ini.",
            )

        val omission = result.omissionRate ?: 0.0
        val band = bandFor(accuracy)

        // A worker who simply stops responding can still post a decent accuracy
        // on the few trials they answered. Treat silence as the worse signal.
        if (omission >= HIGH_OMISSION) {
            return Quality(
                label = "Banyak terlewat",
                severity = SeverityScale.SEVERE,
                explanation = "Akurasi ${percent(accuracy)}, namun ${percent(omission)} " +
                    "stimulus tidak direspons. Kelalaian ini yang lebih menentukan.",
            )
        }
        if (omission >= MODERATE_OMISSION && band.severity < SeverityScale.MODERATE) {
            return band.copy(
                label = "${band.label} · terlewat",
                severity = SeverityScale.MODERATE,
                explanation = band.explanation + " Terdapat ${percent(omission)} stimulus " +
                    "tanpa respons.",
            )
        }

        return band.copy(
            explanation = band.explanation + modeNote(mode),
        )
    }

    private fun bandFor(accuracy: Double): Quality = when {
        accuracy >= GOOD_ACCURACY -> Quality(
            label = "Baik",
            severity = SeverityScale.NORMAL,
            explanation = "Akurasi ${percent(accuracy)}: respons sesuai target.",
        )
        accuracy >= FAIR_ACCURACY -> Quality(
            label = "Cukup",
            severity = SeverityScale.MILD,
            explanation = "Akurasi ${percent(accuracy)}: sebagian respons belum tepat.",
        )
        accuracy >= POOR_ACCURACY -> Quality(
            label = "Kurang",
            severity = SeverityScale.MODERATE,
            explanation = "Akurasi ${percent(accuracy)}: banyak respons tidak tepat.",
        )
        else -> Quality(
            label = "Sangat kurang",
            severity = SeverityScale.SEVERE,
            explanation = "Akurasi ${percent(accuracy)}: sebagian besar respons tidak tepat.",
        )
    }

    private fun modeNote(mode: TestMode): String = when (mode) {
        TestMode.RGB_RANDOM -> " Mode warna acak menguji diskriminasi warna target."
        TestMode.RANDOM_BUTTON -> " Mode tombol acak menguji pencarian target pada grid."
        TestMode.FOCUS_INHIBITION -> " Mode fokus & inhibisi menguji penahanan respons No-Go."
        TestMode.CLASSIC -> ""
    }

    private fun percent(rate: Double): String =
        "${(rate * 100).toInt()}%"

    companion object {
        const val GOOD_ACCURACY = 0.90
        const val FAIR_ACCURACY = 0.75
        const val POOR_ACCURACY = 0.60

        /** Omission at or above this overrides the accuracy band entirely. */
        const val HIGH_OMISSION = 0.40

        /** Omission at or above this downgrades a good band to moderate. */
        const val MODERATE_OMISSION = 0.20
    }
}
