package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.FitnessRecommendation
import com.ratig.app.domain.model.VitalFinding
import com.ratig.app.domain.model.VitalKind
import com.ratig.app.domain.model.Vitals
import com.ratig.app.domain.model.VitalsAssessment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw paramedic measurements into findings.
 *
 * Thresholds are copied verbatim from the desktop RATIG application
 * (`RATIG.py`, `_auto_calc`) so the same numbers produce the same wording on
 * both platforms:
 *
 *   systolic   <100 Hipotensi | <140 Normal | <160 Hipertensi Ringan
 *              <180 Hipertensi Sedang | else Hipertensi Berat
 *   diastolic   <70 Hipotensi |  <90 Normal | <100 Hipertensi Ringan
 *              <120 Hipertensi Sedang | else Hipertensi Berat
 *   pulse       <60 Bradikardia | <100 Normal | <110 Takikardia Ringan
 *              <120 Takikardia Sedang | else Takikardia Berat
 *   alcohol    <0.08 Lolos | else Tidak Lolos
 *
 * Bounds are exclusive on the upper side, matching the desktop `elif` chain.
 * An out-of-range or absent value produces no finding rather than a guess.
 */
@Singleton
class VitalsClassifier @Inject constructor() {

    fun assess(vitals: Vitals): VitalsAssessment {
        val findings = buildList {
            vitals.systolic?.let { add(systolicFinding(it)) }
            vitals.diastolic?.let { add(diastolicFinding(it)) }
            vitals.pulse?.let { add(pulseFinding(it)) }
            vitals.alcoholPercent?.let { add(alcoholFinding(it)) }
        }.filter { it.severity > NORMAL }

        val overall = findings.maxOfOrNull { it.severity } ?: if (vitals.hasAnyMeasurement) NORMAL else -1
        return VitalsAssessment(findings = findings, overallSeverity = overall)
    }

    /**
     * Recommendation from the screening plus the fatigue severity.
     *
     * The two inputs are combined by worst-case: a worker with normal vitals but
     * severe fatigue is not fit, and vice versa.
     */
    fun recommend(vitals: VitalsAssessment, fatigueSeverity: Int): FitnessRecommendation {
        val worst = maxOf(vitals.overallSeverity, fatigueSeverity)
        return when {
            worst < 0 -> FitnessRecommendation.FIT          // nothing measured
            worst <= NORMAL -> FitnessRecommendation.FIT
            worst <= MODERATE -> FitnessRecommendation.FIT_WITH_NOTE
            else -> FitnessRecommendation.NOT_FIT
        }
    }

    private fun systolicFinding(value: Int): VitalFinding = when {
        value < 100 -> finding(VitalKind.SYSTOLIC, "Hipotensi", MILD)
        value < 140 -> finding(VitalKind.SYSTOLIC, "Normal", NORMAL)
        value < 160 -> finding(VitalKind.SYSTOLIC, "Hipertensi Ringan", MILD)
        value < 180 -> finding(VitalKind.SYSTOLIC, "Hipertensi Sedang", MODERATE)
        else -> finding(VitalKind.SYSTOLIC, "Hipertensi Berat", SEVERE)
    }

    private fun diastolicFinding(value: Int): VitalFinding = when {
        value < 70 -> finding(VitalKind.DIASTOLIC, "Hipotensi", MILD)
        value < 90 -> finding(VitalKind.DIASTOLIC, "Normal", NORMAL)
        value < 100 -> finding(VitalKind.DIASTOLIC, "Hipertensi Ringan", MILD)
        value < 120 -> finding(VitalKind.DIASTOLIC, "Hipertensi Sedang", MODERATE)
        else -> finding(VitalKind.DIASTOLIC, "Hipertensi Berat", SEVERE)
    }

    private fun pulseFinding(value: Int): VitalFinding = when {
        value < 60 -> finding(VitalKind.PULSE, "Bradikardia", MILD)
        value < 100 -> finding(VitalKind.PULSE, "Normal", NORMAL)
        value < 110 -> finding(VitalKind.PULSE, "Takikardia Ringan", MILD)
        value < 120 -> finding(VitalKind.PULSE, "Takikardia Sedang", MODERATE)
        else -> finding(VitalKind.PULSE, "Takikardia Berat", SEVERE)
    }

    private fun alcoholFinding(value: Double): VitalFinding =
        if (value < ALCOHOL_LIMIT) {
            finding(VitalKind.ALCOHOL, "Lolos", NORMAL)
        } else {
            finding(VitalKind.ALCOHOL, "Tidak Lolos", SEVERE)
        }

    private fun finding(kind: VitalKind, label: String, severity: Int) =
        VitalFinding(kind = kind, label = label, severity = severity)

    companion object {
        const val NORMAL = 0
        const val MILD = 1
        const val MODERATE = 2
        const val SEVERE = 3

        /** Breath alcohol threshold used by the desktop application. */
        const val ALCOHOL_LIMIT = 0.08
    }
}
