package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.SeverityScale
import com.ratig.app.domain.model.TestMetrics
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the observation-mode indicator. The fatigue bands apply only to Classic
 * sessions, so the other three modes need their own verdict on the same colour
 * scale. A regression here means the examiner reads a blank or misleading
 * indicator for three of the four modes.
 */
class ModeQualityClassifierTest {

    private val classifier = ModeQualityClassifier()

    private fun result(
        mode: TestMode,
        accuracy: Double?,
        omission: Double? = 0.0,
        correct: Double? = accuracy,
    ) = TestResult(
        id = "r1",
        sessionId = "s1",
        metrics = TestMetrics(
            totalTrials = 10,
            validTrialCount = 10,
            invalidTrialCount = 0,
            missedResponseCount = 0,
            falseStartCount = 0,
        ),
        classificationCode = null,
        classificationLabel = null,
        accuracyRate = accuracy,
        correctResponseRate = correct,
        omissionRate = omission,
        testMode = mode,
    )

    @Test
    fun `classic sessions have no mode indicator`() {
        // The fatigue classification is the indicator for classic; showing a
        // second, differently-worded verdict would confuse the examiner.
        assertNull(classifier.classify(result(TestMode.CLASSIC, accuracy = 0.95)))
    }

    @Test
    fun `every observation mode produces an indicator`() {
        listOf(
            TestMode.RGB_RANDOM,
            TestMode.RANDOM_BUTTON,
            TestMode.FOCUS_INHIBITION,
        ).forEach { mode ->
            val quality = classifier.classify(result(mode, accuracy = 0.95))
            assertNotNull("mode $mode must have an indicator", quality)
            assertEquals(SeverityScale.NORMAL, quality!!.severity)
        }
    }

    @Test
    fun `accuracy bands map onto the shared severity scale`() {
        assertEquals(
            SeverityScale.NORMAL,
            classifier.classify(result(TestMode.RGB_RANDOM, 0.95))!!.severity,
        )
        assertEquals(
            SeverityScale.MILD,
            classifier.classify(result(TestMode.RGB_RANDOM, 0.80))!!.severity,
        )
        assertEquals(
            SeverityScale.MODERATE,
            classifier.classify(result(TestMode.RGB_RANDOM, 0.65))!!.severity,
        )
        assertEquals(
            SeverityScale.SEVERE,
            classifier.classify(result(TestMode.RGB_RANDOM, 0.40))!!.severity,
        )
    }

    @Test
    fun `high omission overrides a good accuracy`() {
        // A worker who answers only a few trials correctly and skips the rest
        // must not be reported as performing well.
        val quality = classifier.classify(
            result(TestMode.FOCUS_INHIBITION, accuracy = 1.0, omission = 0.5),
        )!!
        assertEquals(SeverityScale.SEVERE, quality.severity)
        assertEquals("Banyak terlewat", quality.label)
    }

    @Test
    fun `moderate omission downgrades a good band`() {
        val quality = classifier.classify(
            result(TestMode.RGB_RANDOM, accuracy = 0.95, omission = 0.25),
        )!!
        assertEquals(SeverityScale.MODERATE, quality.severity)
    }

    @Test
    fun `missing accuracy yields an explicit unknown rather than a guess`() {
        val quality = classifier.classify(
            result(TestMode.RANDOM_BUTTON, accuracy = null, correct = null),
        )!!
        assertEquals(SeverityScale.UNKNOWN, quality.severity)
        assertEquals("Tidak dinilai", quality.label)
    }

    @Test
    fun `the explanation never claims a fatigue diagnosis`() {
        TestMode.entries.filter { it != TestMode.CLASSIC }.forEach { mode ->
            val quality = classifier.classify(result(mode, accuracy = 0.95))!!
            val text = quality.explanation.lowercase()
            assertEquals(
                "observation modes must not describe fatigue: $text",
                false,
                text.contains("kelelahan"),
            )
        }
    }
}
