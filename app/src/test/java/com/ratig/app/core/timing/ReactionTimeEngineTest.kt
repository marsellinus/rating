package com.ratig.app.core.timing

import com.ratig.app.domain.model.TrialStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the measurement rules of [ReactionTimeEngine] and the frame-refinement
 * helper. These rules decide what a "reaction time" means in the product, so a
 * regression here silently corrupts every stored result.
 *
 * The engine schedules delays on coroutine virtual time but *measures* on the
 * monotonic clock, so the harness advances both in lockstep. Advancing the
 * virtual clock alone would run the whole session to completion instantly and
 * make mid-run states unobservable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionTimeEngineTest {

    private class FakeClock(var nanos: Long = 1_000_000_000L) : MonotonicClock {
        override fun nowNanos(): Long = nanos
        fun advanceMs(ms: Long) { nanos += ms * 1_000_000L }
    }

    /** Advances virtual time and the fake clock together, then settles. */
    private fun TestScope.step(clock: FakeClock, ms: Long) {
        advanceTimeBy(ms)
        clock.advanceMs(ms)
        runCurrent()
    }

    private fun config(
        trials: Int = 3,
        delayMs: Long = 500,
        timeoutMs: Long = 2_000,
        minPlausibleMs: Long = 80,
        practice: Int = 0,
    ) = EngineConfig(
        trialCount = trials,
        delayMinMs = delayMs,
        delayMaxMs = delayMs,
        responseTimeoutMs = timeoutMs,
        interTrialDelayMs = 100,
        minPlausibleReactionMs = minPlausibleMs,
        practiceTrialCount = practice,
    )

    // ---------------------------------------------------------- refinement

    @Test
    fun `frame refinement moves the mark back to the presented frame`() {
        val provisional = 5_000_000_000L
        val frameLag = 16_000_000L // one 60 Hz frame
        val refined = refineStimulusTimestamp(
            provisionalNanos = provisional,
            frameTimeNanos = 900_000_000L,
            clockNowNanos = provisional,
            nanoTimeNowNanos = 900_000_000L + frameLag,
        )
        assertEquals(provisional - frameLag, refined)
    }

    @Test
    fun `frame refinement rejects a mark later than the provisional sample`() {
        // A stale callback from an unrelated frame would push the mark forward
        // and shrink the measured reaction time. It must be ignored.
        val provisional = 5_000_000_000L
        val refined = refineStimulusTimestamp(
            provisionalNanos = provisional,
            frameTimeNanos = 900_000_000L,
            clockNowNanos = provisional + 30_000_000L,
            nanoTimeNowNanos = 900_000_000L,
        )
        assertNull(refined)
    }

    @Test
    fun `frame refinement rejects a frame older than the accepted window`() {
        assertNull(
            refineStimulusTimestamp(
                provisionalNanos = 5_000_000_000L,
                frameTimeNanos = 0L,
                clockNowNanos = 5_000_000_000L,
                nanoTimeNowNanos = 5_000_000_000L, // 5 s old frame
            )
        )
    }

    @Test
    fun `frame refinement rejects a frame stamped in the future`() {
        assertNull(
            refineStimulusTimestamp(
                provisionalNanos = 5_000_000_000L,
                frameTimeNanos = 6_000_000_000L,
                clockNowNanos = 5_000_000_000L,
                nanoTimeNowNanos = 5_000_000_000L,
            )
        )
    }

    @Test
    fun `reaction millis rounds to nearest instead of truncating`() {
        // 79.6 ms must not be truncated to 79 ms and fall below an 80 ms floor.
        assertEquals(80L, reactionMillis(tapNanos = 1_079_600_000L, shownAtNanos = 1_000_000_000L))
        assertEquals(79L, reactionMillis(tapNanos = 1_079_400_000L, shownAtNanos = 1_000_000_000L))
        assertEquals(0L, reactionMillis(tapNanos = 1_000_000_000L, shownAtNanos = 1_000_000_000L))
    }

    // -------------------------------------------------------------- engine

    @Test
    fun `records a valid trial measured from the stimulus mark`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 500))

        step(clock, 3_000)   // countdown
        step(clock, 500)     // pre-stimulus window expires -> stimulus

        assertTrue(
            "stimulus must be visible before the response window expires",
            engine.state.value is EngineState.StimulusVisible,
        )

        step(clock, 250)     // user reacts after 250 ms
        engine.onTap()
        runCurrent()

        val trial = engine.trials.single()
        assertEquals(TrialStatus.VALID, trial.status)
        assertEquals(250L, trial.reactionTimeMs)
    }

    @Test
    fun `tap before the stimulus is a false start and consumes the trial`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 500))

        step(clock, 3_000)
        engine.onTap()       // still inside the waiting window
        runCurrent()

        val trial = engine.trials.single()
        assertTrue(trial.falseStart)
        assertEquals(TrialStatus.FALSE_START, trial.status)
        assertNull(trial.reactionTimeMs)
    }

    @Test
    fun `no response within the timeout is recorded as missed`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 500, timeoutMs = 1_000))

        step(clock, 3_000)
        step(clock, 500)     // stimulus visible
        step(clock, 1_000)   // response window expires

        val trial = engine.trials.single()
        assertTrue(trial.missedResponse)
        assertEquals(TrialStatus.MISSED, trial.status)
        assertNull(trial.reactionTimeMs)
    }

    @Test
    fun `response faster than the plausibility floor is an invalid artifact`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 500, minPlausibleMs = 80))

        step(clock, 3_000)
        step(clock, 500)

        step(clock, 20)      // device artifact, not a human response
        engine.onTap()
        runCurrent()

        assertEquals(TrialStatus.INVALID, engine.trials.single().status)
    }

    @Test
    fun `practice trials are not counted in the recorded trials`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 100, practice = 2))

        step(clock, 3_000)
        repeat(3) {
            step(clock, 100)   // stimulus
            step(clock, 200)   // reaction
            engine.onTap()
            runCurrent()
            step(clock, 100)   // inter-trial gap
        }

        assertEquals(1, engine.trials.size)
        assertEquals(1, engine.trials.single().trialNumber)
    }

    @Test
    fun `frame refinement moves the mark earlier and never forward`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 500))

        step(clock, 3_000)
        step(clock, 500)

        val provisional = (engine.state.value as EngineState.StimulusVisible).shownAtNanos
        val frameLagNanos = 16_000_000L
        engine.onStimulusFrameRendered(frameTimeNanos = System.nanoTime() - frameLagNanos)
        val refined = (engine.state.value as EngineState.StimulusVisible).shownAtNanos

        assertTrue("refinement must move the mark earlier", refined < provisional)

        // A second, later callback must not move the mark forward again.
        engine.onStimulusFrameRendered(frameTimeNanos = System.nanoTime())
        assertEquals(refined, (engine.state.value as EngineState.StimulusVisible).shownAtNanos)
    }

    @Test
    fun `start resets the trial list of a previous run`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 1, delayMs = 100))
        step(clock, 3_000)
        step(clock, 100)
        step(clock, 200)
        engine.onTap()
        runCurrent()
        assertEquals(1, engine.trials.size)

        engine.start(config(trials = 2, delayMs = 100))
        assertEquals(0, engine.trials.size)
    }

    @Test
    fun `cancel discards recorded trials`() = runTest {
        val clock = FakeClock()
        val engine = ReactionTimeEngine(clock, scope = this)
        engine.start(config(trials = 2, delayMs = 100))
        step(clock, 3_000)
        step(clock, 100)
        step(clock, 200)
        engine.onTap()
        runCurrent()

        engine.cancel()
        assertTrue(engine.trials.isEmpty())
        assertEquals(EngineState.Idle, engine.state.value)
    }
}
