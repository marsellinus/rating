package com.ratig.app.core.timing.modes

import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.domain.model.TrialStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the trial-recording rules of [ModeEngineBase] and the classification of
 * each concrete mode. A regression here silently drops or mislabels trials,
 * which corrupts the per-mode rates the server recomputes on finalize.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeEngineTest {

    private class FakeClock(var nanos: Long = 1_000_000_000L) : MonotonicClock {
        override fun nowNanos(): Long = nanos
        fun advanceMs(ms: Long) { nanos += ms * 1_000_000L }
    }

    private fun TestScope.step(clock: FakeClock, ms: Long) {
        advanceTimeBy(ms)
        clock.advanceMs(ms)
        runCurrent()
    }

    private fun config(
        trials: Int = 2,
        delayMs: Long = 200,
        timeoutMs: Long = 1_000,
        practice: Int = 0,
    ) = ModeEngineConfig(
        trialCount = trials,
        delayMinMs = delayMs,
        delayMaxMs = delayMs,
        responseTimeoutMs = timeoutMs,
        practiceTrialCount = practice,
        restMs = 50,
        falseStartRule = FalseStartRule.CONSUME_TRIAL,
        minPlausibleReactionMs = 80,
        maxPlausibleReactionMs = 2_000,
    )

    /** noGoRatio = 1.0 -> every stimulus is a no-go (red circle). */
    private fun focus(clock: MonotonicClock, scope: kotlinx.coroutines.CoroutineScope) =
        FocusInhibitionModeEngine(
            clock = clock,
            seed = 42L,
            modeConfig = ModeConfiguration.DEFAULT.copy(noGoRatio = 1.0),
            scope = scope,
        )

    @Test
    fun `a response past the deadline is still recorded as a trial`() = runTest {
        // Regression: late responses were built with trialNumber = 0 and were
        // therefore discarded by record(), so the trial vanished from the data.
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 200, timeoutMs = 500))

        step(clock, 3_000)
        step(clock, 200)   // stimulus visible
        step(clock, 500)   // response window expires without a tap

        val trial = engine.trials.single()
        assertEquals(1, trial.trialNumber)
        assertEquals(ModeResponseType.NONE, trial.responseType)
    }

    @Test
    fun `practice trials never enter the counted trial list`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100, practice = 2))

        step(clock, 3_000)
        repeat(3) {
            step(clock, 100)   // stimulus
            step(clock, 300)   // reaction
            engine.onTap(ModeTap())
            runCurrent()
            step(clock, 50)    // rest
        }

        assertEquals(1, engine.trials.size)
        assertEquals(1, engine.trials.single().trialNumber)
    }

    @Test
    fun `tap on a no-go stimulus is a wrong response carrying its latency`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100))

        step(clock, 3_000)
        step(clock, 100)
        step(clock, 300)
        engine.onTap(ModeTap())
        runCurrent()

        val trial = engine.trials.single()
        assertEquals(ModeResponseType.WRONG, trial.responseType)
        assertEquals(false, trial.responseCorrect)
        assertEquals(300L, trial.reactionTimeMs)
    }

    @Test
    fun `withholding a response on a no-go stimulus is a correct rejection`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100, timeoutMs = 500))

        step(clock, 3_000)
        step(clock, 100)
        step(clock, 500)

        val trial = engine.trials.single()
        assertEquals(ModeResponseType.NONE, trial.responseType)
        assertEquals(true, trial.responseCorrect)
        assertNull(trial.reactionTimeMs)
    }

    @Test
    fun `a tap before the stimulus is an early response that consumes the trial`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 500))

        step(clock, 3_000)
        engine.onTap(ModeTap())   // still inside the waiting window
        runCurrent()

        val trial = engine.trials.single()
        assertEquals(ModeResponseType.EARLY, trial.responseType)
        assertEquals(TrialStatus.FALSE_START, trial.trialStatus)
        assertTrue(trial.falseStart)
    }

    @Test
    fun `reaction time is measured from the stimulus mark`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100))

        step(clock, 3_000)
        step(clock, 100)
        step(clock, 320)
        engine.onTap(ModeTap())
        runCurrent()

        assertEquals(320L, engine.trials.single().reactionTimeMs)
    }

    @Test
    fun `a stimulus faster than the plausibility floor is marked invalid`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100))

        step(clock, 3_000)
        step(clock, 100)
        step(clock, 10)            // below the 80 ms floor
        engine.onTap(ModeTap())
        runCurrent()

        assertEquals(TrialStatus.INVALID, engine.trials.single().trialStatus)
    }

    @Test
    fun `the same seed reproduces the same stimulus sequence`() = runTest {
        fun runOnce(): List<Boolean> {
            val clock = FakeClock()
            val engine = FocusInhibitionModeEngine(
                clock = clock,
                seed = 7L,
                modeConfig = ModeConfiguration.DEFAULT.copy(noGoRatio = 0.5),
                scope = this,
            )
            engine.start(config(trials = 6, delayMs = 50, timeoutMs = 200))
            step(clock, 3_000)
            repeat(6) {
                step(clock, 50)
                step(clock, 200)   // let the window expire
                step(clock, 50)    // rest
            }
            return engine.trials.map { it.isTarget == true }
        }
        assertEquals(runOnce(), runOnce())
    }

    @Test
    fun `start resets trials from the previous run`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100))
        step(clock, 3_000)
        step(clock, 100)
        step(clock, 300)
        engine.onTap(ModeTap())
        runCurrent()
        assertEquals(1, engine.trials.size)

        engine.start(config(trials = 3, delayMs = 100))
        assertEquals(0, engine.trials.size)
    }

    @Test
    fun `frame refinement never moves the stimulus mark forward`() = runTest {
        val clock = FakeClock()
        val engine = focus(clock, this)
        engine.start(config(trials = 1, delayMs = 100))

        step(clock, 3_000)
        step(clock, 100)

        val state = engine.state.value as ModeEngineState.StimulusVisible
        val provisional = state.shownAtNanos
        engine.onStimulusFrameRendered(frameTimeNanos = System.nanoTime() - 16_000_000L)
        val refined = (engine.state.value as ModeEngineState.StimulusVisible).shownAtNanos

        assertNotNull(refined)
        assertTrue("refinement must move the mark earlier", refined < provisional)
    }
}
