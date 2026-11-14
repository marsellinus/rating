package com.ratig.app.core.timing.modes

import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.domain.model.TrialStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit: drive a full session of every mode and inspect what lands. */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineAuditTest {

    private class FakeClock(var n: Long = 1_000_000_000L) : MonotonicClock {
        override fun nowNanos() = n
    }

    private fun TestScope.step(clock: FakeClock, ms: Long) {
        advanceTimeBy(ms); clock.n += ms * 1_000_000; runCurrent()
    }

    private fun cfg(trials: Int, delay: Long = 100, timeout: Long = 500) = ModeEngineConfig(
        trialCount = trials, delayMinMs = delay, delayMaxMs = delay,
        responseTimeoutMs = timeout, practiceTrialCount = 0, restMs = 50,
        falseStartRule = FalseStartRule.CONSUME_TRIAL,
        minPlausibleReactionMs = 80, maxPlausibleReactionMs = 2000,
    )

    @Test
    fun `focus mode records exactly trialCount trials when every trial is answered`() = runTest {
        val clock = FakeClock()
        val e = FocusInhibitionModeEngine(clock, 1L, ModeConfiguration.DEFAULT.copy(noGoRatio = 0.0), this)
        val finished = mutableListOf<List<ModeEngineTrial>>()
        backgroundScope.launch { e.onFinished.collect { finished += it } }
        runCurrent()

        e.start(cfg(5))
        step(clock, 3_000)
        repeat(5) {
            step(clock, 100)   // stimulus
            step(clock, 250)   // answer
            e.onTap(ModeTap())
            runCurrent()
            step(clock, 50)    // rest
        }
        step(clock, 200)

        assertEquals("recorded trials", 5, e.trials.size)
        assertEquals("finished events", 1, finished.size)
        assertEquals(5, finished.single().size)
        assertTrue(e.trials.all { it.trialStatus == TrialStatus.VALID })
    }

    @Test
    fun `grid mode records a wrong response when a blank cell is tapped`() = runTest {
        val clock = FakeClock()
        val e = ButtonGridModeEngine(
            clock, 3L,
            ModeConfiguration.DEFAULT.copy(gridSize = 3, distractorRatio = 0.0),
            this,
        )
        e.start(cfg(1))
        step(clock, 3_000)
        step(clock, 100)
        val stim = (e.state.value as ModeEngineState.StimulusVisible).stimulus as ModeStimulus.Grid
        val blank = stim.symbols.indices.first { it != stim.targetPosition }
        step(clock, 200)
        e.onTap(ModeTap(blank))
        runCurrent()

        val t = e.trials.single()
        assertEquals(ModeResponseType.WRONG, t.responseType)
        assertEquals(false, t.responseCorrect)
    }

    @Test
    fun `rgb mode with randomizePosition still scores the target colour correctly`() = runTest {
        val clock = FakeClock()
        val e = RgbRandomModeEngine(
            clock, 5L,
            ModeConfiguration.DEFAULT.copy(randomizePosition = true, targetColor = "green"),
            this,
        )
        e.start(cfg(6))
        step(clock, 3_000)
        var correct = 0
        repeat(6) {
            step(clock, 100)
            val stim = (e.state.value as ModeEngineState.StimulusVisible).stimulus as ModeStimulus.Rgb
            step(clock, 200)
            e.onTap(ModeTap())
            runCurrent()
            if (stim.isTarget) correct++
            step(clock, 50)
        }
        assertEquals(6, e.trials.size)
        // Any tap is a response; correctness must follow the drawn stimulus.
        assertEquals(correct, e.trials.count { it.responseCorrect == true })
    }

    @Test
    fun `finished event is delivered even when nobody subscribed before start`() = runTest {
        // If this fails, a session that completes before the ViewModel starts
        // collecting would never finalize - the results would silently vanish.
        val clock = FakeClock()
        val e = FocusInhibitionModeEngine(clock, 9L, ModeConfiguration.DEFAULT.copy(noGoRatio = 0.0), this)
        e.start(cfg(1))
        step(clock, 3_000)
        step(clock, 100)
        step(clock, 600) // let it finish without a subscriber
        assertEquals(ModeEngineState.Finished, e.state.value)

        val late = withTimeoutOrNullAudit { e.onFinished.first() }
        assertTrue("onFinished must replay the completion", late != null)
    }

    @Test
    fun `focus mode always contains at least one no-go trial`() {
        // A "Focus & Inhibition" run with zero no-go stimuli would not test
        // inhibition at all. With noGoRatio = 0 the random draw can never
        // produce one, so coverage must be forced.
        runTest {
            val clock = FakeClock()
            val e = FocusInhibitionModeEngine(
                clock, 11L,
                ModeConfiguration.DEFAULT.copy(noGoRatio = 0.0),
                this,
            )
            e.start(cfg(4))
            step(clock, 3_000)
            repeat(4) {
                step(clock, 100)
                step(clock, 600) // never respond; we only inspect stimuli
                step(clock, 50)
            }
            assertEquals(4, e.trials.size)
            assertTrue(
                "at least one no-go trial must exist",
                e.trials.any { it.isTarget == false },
            )
        }
    }

    @Test
    fun `focus mode always contains at least one go trial`() {
        runTest {
            val clock = FakeClock()
            val e = FocusInhibitionModeEngine(
                clock, 12L,
                ModeConfiguration.DEFAULT.copy(noGoRatio = 0.9),
                this,
            )
            e.start(cfg(4))
            step(clock, 3_000)
            repeat(4) {
                step(clock, 100)
                step(clock, 600)
                step(clock, 50)
            }
            assertEquals(4, e.trials.size)
            assertTrue(
                "at least one go trial must exist",
                e.trials.any { it.isTarget == true },
            )
        }
    }

    @Test
    fun `practice trials do not consume the no-go coverage guarantee`() {
        runTest {
            val clock = FakeClock()
            val e = FocusInhibitionModeEngine(
                clock, 13L,
                ModeConfiguration.DEFAULT.copy(noGoRatio = 0.0),
                this,
            )
            e.start(cfg(2).copy(practiceTrialCount = 2))
            step(clock, 3_000)
            repeat(4) {
                step(clock, 100)
                step(clock, 600)
                step(clock, 50)
            }
            // Only counted trials are recorded, and one of them must be no-go.
            assertEquals(2, e.trials.size)
            assertTrue(e.trials.any { it.isTarget == false })
        }
    }

    private suspend fun <T> withTimeoutOrNullAudit(block: suspend () -> T): T? =
        kotlinx.coroutines.withTimeoutOrNull(1_000) { block() }
}
