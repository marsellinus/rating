package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.FitnessRecommendation
import com.ratig.app.domain.model.VitalKind
import com.ratig.app.domain.model.Vitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the screening thresholds to the desktop RATIG application. If these
 * drift, the same worker is judged differently on Android and on desktop,
 * which is exactly the kind of inconsistency an audit would flag.
 */
class VitalsClassifierTest {

    private val classifier = VitalsClassifier()

    private fun label(vitals: Vitals, kind: VitalKind): String? =
        classifier.assess(vitals).findings.firstOrNull { it.kind == kind }?.label

    // ------------------------------------------------------------- systolic

    @Test
    fun `systolic thresholds match the desktop boundaries`() {
        assertEquals("Hipotensi", label(Vitals(systolic = 99), VitalKind.SYSTOLIC))
        assertEquals(null, label(Vitals(systolic = 100), VitalKind.SYSTOLIC))
        assertEquals(null, label(Vitals(systolic = 139), VitalKind.SYSTOLIC))
        assertEquals("Hipertensi Ringan", label(Vitals(systolic = 140), VitalKind.SYSTOLIC))
        assertEquals("Hipertensi Ringan", label(Vitals(systolic = 159), VitalKind.SYSTOLIC))
        assertEquals("Hipertensi Sedang", label(Vitals(systolic = 160), VitalKind.SYSTOLIC))
        assertEquals("Hipertensi Sedang", label(Vitals(systolic = 179), VitalKind.SYSTOLIC))
        assertEquals("Hipertensi Berat", label(Vitals(systolic = 180), VitalKind.SYSTOLIC))
    }

    // ------------------------------------------------------------ diastolic

    @Test
    fun `diastolic thresholds match the desktop boundaries`() {
        assertEquals("Hipotensi", label(Vitals(diastolic = 69), VitalKind.DIASTOLIC))
        assertEquals(null, label(Vitals(diastolic = 70), VitalKind.DIASTOLIC))
        assertEquals(null, label(Vitals(diastolic = 89), VitalKind.DIASTOLIC))
        assertEquals("Hipertensi Ringan", label(Vitals(diastolic = 90), VitalKind.DIASTOLIC))
        assertEquals("Hipertensi Sedang", label(Vitals(diastolic = 100), VitalKind.DIASTOLIC))
        assertEquals("Hipertensi Berat", label(Vitals(diastolic = 120), VitalKind.DIASTOLIC))
    }

    // ---------------------------------------------------------------- pulse

    @Test
    fun `pulse thresholds match the desktop boundaries`() {
        assertEquals("Bradikardia", label(Vitals(pulse = 59), VitalKind.PULSE))
        assertEquals(null, label(Vitals(pulse = 60), VitalKind.PULSE))
        assertEquals(null, label(Vitals(pulse = 99), VitalKind.PULSE))
        assertEquals("Takikardia Ringan", label(Vitals(pulse = 100), VitalKind.PULSE))
        assertEquals("Takikardia Sedang", label(Vitals(pulse = 110), VitalKind.PULSE))
        assertEquals("Takikardia Berat", label(Vitals(pulse = 120), VitalKind.PULSE))
    }

    // -------------------------------------------------------------- alcohol

    @Test
    fun `alcohol below the limit passes and at the limit fails`() {
        assertEquals(null, label(Vitals(alcoholPercent = 0.079), VitalKind.ALCOHOL))
        assertEquals("Tidak Lolos", label(Vitals(alcoholPercent = 0.08), VitalKind.ALCOHOL))
        assertEquals("Tidak Lolos", label(Vitals(alcoholPercent = 0.5), VitalKind.ALCOHOL))
    }

    // ------------------------------------------------------------ aggregate

    @Test
    fun `nothing measured yields no findings and no severity`() {
        val assessment = classifier.assess(Vitals())
        assertFalse(assessment.hasFindings)
        assertFalse(assessment.isMeasured)
        assertEquals(-1, assessment.overallSeverity)
    }

    @Test
    fun `normal measurements are measured but produce no findings`() {
        val assessment = classifier.assess(
            Vitals(systolic = 120, diastolic = 80, pulse = 72, alcoholPercent = 0.0)
        )
        assertFalse(assessment.hasFindings)
        assertTrue(assessment.isMeasured)
        assertEquals(VitalsClassifier.NORMAL, assessment.overallSeverity)
    }

    @Test
    fun `overall severity is the worst finding`() {
        val assessment = classifier.assess(
            Vitals(systolic = 150, diastolic = 95, pulse = 115)
        )
        assertEquals(VitalsClassifier.MODERATE, assessment.overallSeverity)
        assertEquals(3, assessment.findings.size)
    }

    // -------------------------------------------------------- recommendation

    @Test
    fun `recommendation takes the worst of vitals and fatigue`() {
        val normal = classifier.assess(Vitals(systolic = 120, diastolic = 80))
        val severeVitals = classifier.assess(Vitals(systolic = 190))

        assertEquals(
            FitnessRecommendation.FIT,
            classifier.recommend(normal, fatigueSeverity = VitalsClassifier.NORMAL),
        )
        // Normal vitals must not mask severe fatigue.
        assertEquals(
            FitnessRecommendation.NOT_FIT,
            classifier.recommend(normal, fatigueSeverity = VitalsClassifier.SEVERE),
        )
        // Severe vitals must not be masked by a normal reaction test.
        assertEquals(
            FitnessRecommendation.NOT_FIT,
            classifier.recommend(severeVitals, fatigueSeverity = VitalsClassifier.NORMAL),
        )
        // Moderate on either side still allows work with a note.
        assertEquals(
            FitnessRecommendation.FIT_WITH_NOTE,
            classifier.recommend(normal, fatigueSeverity = VitalsClassifier.MODERATE),
        )
    }

    @Test
    fun `no measurements at all is treated as fit rather than unknown`() {
        // The examiner chose to skip screening; the reaction test stands alone.
        assertEquals(
            FitnessRecommendation.FIT,
            classifier.recommend(classifier.assess(Vitals()), fatigueSeverity = -1),
        )
    }
}
