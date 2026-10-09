package com.ratig.app.core.timing

import com.ratig.app.domain.model.TrialStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Execution parameters of one reaction-test run (mirrors the active protocol). */
data class EngineConfig(
    val trialCount: Int,
    val delayMinMs: Long,
    val delayMaxMs: Long,
    val responseTimeoutMs: Long,
    val interTrialDelayMs: Long,
    /** Reaction times faster than this are device artifacts, not human responses. */
    val minPlausibleReactionMs: Long,
    val practiceTrialCount: Int = 0,
)

/** Phases of a reaction-test run. All timestamps use the [MonotonicClock] time base. */
sealed interface EngineState {
    data object Idle : EngineState

    /** Decorative 3-2-1 countdown; [remainingMs] is 3000/2000/1000. */
    data class Countdown(val remainingMs: Long) : EngineState

    /** Random pre-stimulus window; a tap in this state is a false start. */
    data class Waiting(val stimulusInMs: Long, val sinceNanos: Long) : EngineState

    /** Stimulus on screen; [shownAtNanos] is the rendered frame time (clock base). */
    data class StimulusVisible(val shownAtNanos: Long) : EngineState

    /** Brief inter-trial window; taps are ignored (double-tap prevention). */
    data object TrialDone : EngineState

    data object Finished : EngineState
}

/**
 * Outcome of a single trial. Shaped like `domain.model.TrialRecord` so the
 * ViewModel can map it 1:1 for the finalize RPC.
 *
 * [trialNumber] is 1-based for counted trials; practice trials use negative
 * numbers (-1, -2, ...) and are never appended to [ReactionTimeEngine.trials].
 */
data class EngineTrial(
    val trialNumber: Int,
    val reactionTimeMs: Long?,
    val status: TrialStatus,
    val falseStart: Boolean,
    val missedResponse: Boolean,
    val stimulusAtMonotonicNs: Long?,
    val tapAtMonotonicNs: Long?,
)

/**
 * Pure-Kotlin reaction-time state machine. No Android imports: fully
 * unit-testable with fake [MonotonicClock]/[Random] and a test [CoroutineScope].
 *
 * Measurement rules:
 *  - Reaction time = (tapNanos - shownAtNanos) / 1e6, both in the monotonic
 *    clock time base. The wall clock is NEVER used for measurement.
 *  - Delays (countdown, random stimulus delay, timeout, inter-trial) use
 *    coroutine [delay] wall scheduling - acceptable, they are not measured.
 *  - The stimulus presentation timestamp is refined to the actual rendered
 *    frame time reported by [onStimulusFrameRendered]. `withFrameNanos {}`
 *    timestamps use the `System.nanoTime()` base; the frame's age is
 *    converted into the clock base so every stored value of one trial shares
 *    a single time base.
 *
 * Threading: all public API is synchronous and MUST be called from the same
 * main thread; pass a main-confined [scope] (e.g. `viewModelScope`).
 */
class ReactionTimeEngine(
    private val clock: MonotonicClock,
    private val random: Random = Random.Default,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val countedTrials = mutableListOf<EngineTrial>()

    /** Counted trials recorded so far, in order (practice trials excluded). */
    val trials: List<EngineTrial> get() = countedTrials.toList()

    private val _onTrialDone = MutableSharedFlow<EngineTrial>(extraBufferCapacity = 16)
    val onTrialDone: SharedFlow<EngineTrial> = _onTrialDone.asSharedFlow()

    private val _onFinished = MutableSharedFlow<List<EngineTrial>>(extraBufferCapacity = 4)
    val onFinished: SharedFlow<List<EngineTrial>> = _onFinished.asSharedFlow()

    private var worker: Job? = null
    private val taps = Channel<Unit>(Channel.CONFLATED)

    /** Presentation timestamp of the current stimulus (clock base); null pre-stimulus. */
    private var currentShownAtNanos: Long? = null

    /** Timestamp of the last accepted tap (clock base). */
    private var pendingTapNanos: Long? = null

    /**
     * Starts a fresh run: cancels any previous run, clears recorded trials and
     * launches the single worker coroutine (countdown -> trials -> finished).
     */
    fun start(config: EngineConfig) {
        require(config.trialCount > 0) { "trialCount harus lebih dari 0" }
        require(config.delayMinMs >= 0 && config.delayMaxMs >= config.delayMinMs) {
            "delayMinMs harus 0..delayMaxMs"
        }
        require(config.responseTimeoutMs > 0 && config.minPlausibleReactionMs >= 0) {
            "Konfigurasi durasi tidak valid"
        }
        worker?.cancel()
        countedTrials.clear()
        drainTaps()
        currentShownAtNanos = null
        pendingTapNanos = null
        _state.value = EngineState.Countdown(COUNTDOWN_TOTAL_MS)
        worker = scope.launch { runSession(config) }
    }

    /**
     * Called by the UI right after the stimulus frame is composed, passing the
     * actual `withFrameNanos {}` frame time. Refines the provisional stimulus
     * timestamp; ignored outside the stimulus phase.
     */
    fun onStimulusFrameRendered(frameTimeNanos: Long) {
        if (_state.value !is EngineState.StimulusVisible) return
        // frameTimeNanos uses the System.nanoTime() base (Choreographer); the
        // frame's age converts it into the MonotonicClock base.
        val frameLagNanos = (System.nanoTime() - frameTimeNanos).coerceAtLeast(0)
        val shownAt = clock.nowNanos() - frameLagNanos
        val previous = currentShownAtNanos
        if (previous == null || shownAt > previous) {
            currentShownAtNanos = shownAt
            _state.value = EngineState.StimulusVisible(shownAtNanos = shownAt)
        }
    }

    /**
     * Screen tap. Before the stimulus (Waiting) -> FALSE_START trial; after the
     * stimulus -> VALID trial (or INVALID artifact when faster than
     * [EngineConfig.minPlausibleReactionMs]). Only the first tap per stimulus
     * counts; taps during Countdown/TrialDone/inter-trial are ignored.
     */
    fun onTap() {
        when (_state.value) {
            is EngineState.Waiting, is EngineState.StimulusVisible -> {
                pendingTapNanos = clock.nowNanos()
                taps.trySend(Unit)
            }
            else -> Unit
        }
    }

    /** Stops the run and discards all recorded trials (hard reset to [EngineState.Idle]). */
    fun cancel() {
        worker?.cancel()
        worker = null
        countedTrials.clear()
        drainTaps()
        currentShownAtNanos = null
        pendingTapNanos = null
        _state.value = EngineState.Idle
    }

    /**
     * Stops the run without discarding recorded trials (audit) and returns to
     * [EngineState.Idle]; used e.g. when the app loses focus mid-test.
     */
    fun interrupt(reason: String?) {
        worker?.cancel()
        worker = null
        currentShownAtNanos = null
        _state.value = EngineState.Idle
    }

    private suspend fun runSession(config: EngineConfig) {
        runCountdown()
        repeat(config.practiceTrialCount) { index -> runTrial(trialNumber = -(index + 1), config) }
        for (number in 1..config.trialCount) {
            runTrial(trialNumber = number, config)
            if (number < config.trialCount) delay(config.interTrialDelayMs)
        }
        _state.value = EngineState.Finished
        _onFinished.tryEmit(countedTrials.toList())
    }

    private suspend fun runCountdown() {
        for (second in 3 downTo 1) {
            _state.value = EngineState.Countdown(remainingMs = second * 1_000L)
            delay(1_000)
        }
    }

    private suspend fun runTrial(trialNumber: Int, config: EngineConfig) {
        drainTaps()
        currentShownAtNanos = null
        pendingTapNanos = null

        val stimulusInMs = randomStimulusDelay(config)
        _state.value = EngineState.Waiting(stimulusInMs = stimulusInMs, sinceNanos = clock.nowNanos())

        // A tap before the stimulus is a false start and consumes the trial.
        val earlyTap = withTimeoutOrNull(stimulusInMs) { taps.receive() }
        if (earlyTap != null) {
            record(
                EngineTrial(
                    trialNumber = trialNumber,
                    reactionTimeMs = null,
                    status = TrialStatus.FALSE_START,
                    falseStart = true,
                    missedResponse = false,
                    stimulusAtMonotonicNs = null,
                    tapAtMonotonicNs = pendingTapNanos,
                )
            )
            return
        }

        // Stimulus due: provisional monotonic timestamp now; refined to the
        // actual rendered frame time by onStimulusFrameRendered().
        val provisionalShownAt = clock.nowNanos()
        currentShownAtNanos = provisionalShownAt
        _state.value = EngineState.StimulusVisible(shownAtNanos = provisionalShownAt)

        val response = withTimeoutOrNull(config.responseTimeoutMs) { taps.receive() }
        val trial = if (response == null) {
            EngineTrial(
                trialNumber = trialNumber,
                reactionTimeMs = null,
                status = TrialStatus.MISSED,
                falseStart = false,
                missedResponse = true,
                stimulusAtMonotonicNs = currentShownAtNanos,
                tapAtMonotonicNs = null,
            )
        } else {
            val tapAt = pendingTapNanos ?: clock.nowNanos()
            val shownAt = currentShownAtNanos ?: provisionalShownAt
            val reactionMs = (tapAt - shownAt) / 1_000_000
            val status =
                if (reactionMs < config.minPlausibleReactionMs) TrialStatus.INVALID else TrialStatus.VALID
            EngineTrial(
                trialNumber = trialNumber,
                reactionTimeMs = reactionMs,
                status = status,
                falseStart = false,
                missedResponse = false,
                stimulusAtMonotonicNs = shownAt,
                tapAtMonotonicNs = tapAt,
            )
        }
        record(trial)
    }

    private fun record(trial: EngineTrial) {
        if (trial.trialNumber > 0) countedTrials += trial
        _state.value = EngineState.TrialDone
        _onTrialDone.tryEmit(trial)
    }

    private fun randomStimulusDelay(config: EngineConfig): Long =
        if (config.delayMaxMs > config.delayMinMs) {
            random.nextLong(config.delayMinMs, config.delayMaxMs + 1)
        } else {
            config.delayMinMs
        }

    private fun drainTaps() {
        while (taps.tryReceive().isSuccess) {
            // Discard stale taps; taps during inter-trial windows are ignored.
        }
    }

    private companion object {
        const val COUNTDOWN_TOTAL_MS = 3_000L
    }
}
