package com.ratig.app.core.timing.modes

import com.ratig.app.core.timing.MonotonicClock
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
import kotlin.math.floor
import kotlin.random.Random

/** Execution parameters of one mode run (mirrors the active protocol). */
data class ModeEngineConfig(
    val trialCount: Int,
    val delayMinMs: Long,
    val delayMaxMs: Long,
    val responseTimeoutMs: Long,
    val practiceTrialCount: Int = 0,
    val restMs: Long = 300L,
    val falseStartRule: FalseStartRule = FalseStartRule.CONSUME_TRIAL,
    val minPlausibleReactionMs: Long = 80L,
    val maxPlausibleReactionMs: Long = 2000L,
)

/**
 * Outcome of one mode trial. Carries the classic columns plus the mode
 * columns (stimulus_kind / is_target / response_type / response_correct) so
 * the ViewModel can map it 1:1 into `domain.model.TrialRecord`.
 *
 * [trialNumber] is 1-based for counted trials; practice trials use negative
 * numbers and are never appended to [ModeEngineBase.trials].
 */
data class ModeEngineTrial(
    val trialNumber: Int,
    val stimulusKind: String,
    val isTarget: Boolean,
    val responseType: String,
    val responseCorrect: Boolean,
    val trialStatus: TrialStatus,
    val reactionTimeMs: Long? = null,
    val falseStart: Boolean = false,
    val missedResponse: Boolean = false,
    val stimulusAtMonotonicNs: Long? = null,
    val tapAtMonotonicNs: Long? = null,
)

/** Phases of a mode run. All timestamps use the [MonotonicClock] time base. */
sealed interface ModeEngineState {
    data object Idle : ModeEngineState

    /** Decorative 3-2-1 countdown. */
    data class Countdown(val remainingMs: Long) : ModeEngineState

    /** Random pre-stimulus window; a tap here is scored by the false-start rule. */
    data class Waiting(val stimulusInMs: Long, val sinceNanos: Long) : ModeEngineState

    /** Stimulus on screen; [shownAtNanos] is the rendered frame time (clock base). */
    data class StimulusVisible(val shownAtNanos: Long, val stimulus: ModeStimulus) : ModeEngineState

    /** Brief inter-trial window; taps are ignored (double-response prevention). */
    data object TrialDone : ModeEngineState

    data object Finished : ModeEngineState
}

/**
 * Pure-Kotlin state machine shared by all test-mode engines. No Android
 * imports: fully unit-testable with a fake [MonotonicClock] and a test
 * [CoroutineScope].
 *
 * Guarantees (CONTRACT-2 test modes):
 *  - The stimulus sequence is fully determined by [seed]: every `start()`
 *    recreates `Random(seed)`, so rerunning with the same seed reproduces the
 *    exact stimulus/position sequence for audit.
 *  - Each stimulus is drawn ONCE before the pre-stimulus window opens; there
 *    is NO re-randomization after a response is recorded.
 *  - One response per trial: the first accepted tap resolves the trial;
 *    subsequent taps are ignored.
 *  - The next stimulus is presented only after the previous trial resolves.
 *
 * Measurement rules mirror `ReactionTimeEngine`: reaction time =
 * (tapNanos - shownAtNanos) / 1e6 in the monotonic clock base; delays use
 * coroutine scheduling; the stimulus timestamp is refined to the actually
 * rendered frame time reported by [onStimulusFrameRendered].
 *
 * Threading: all public API is synchronous and MUST be called from the same
 * main thread; pass a main-confined [scope] (e.g. `viewModelScope`).
 */
abstract class ModeEngineBase(
    private val clock: MonotonicClock,
    private val seed: Long,
    private val scope: CoroutineScope,
) {

    /** Recreated from [seed] on every [start] - reruns are reproducible. */
    private var runRandom: Random = Random(seed)

    private val _state = MutableStateFlow<ModeEngineState>(ModeEngineState.Idle)
    val state: StateFlow<ModeEngineState> = _state.asStateFlow()

    private val countedTrials = mutableListOf<ModeEngineTrial>()

    /** Counted trials recorded so far, in order (practice trials excluded). */
    val trials: List<ModeEngineTrial> get() = countedTrials.toList()

    private val _onTrialDone = MutableSharedFlow<ModeEngineTrial>(extraBufferCapacity = 16)
    val onTrialDone: SharedFlow<ModeEngineTrial> = _onTrialDone.asSharedFlow()

    private val _onFinished = MutableSharedFlow<List<ModeEngineTrial>>(extraBufferCapacity = 4)
    val onFinished: SharedFlow<List<ModeEngineTrial>> = _onFinished.asSharedFlow()

    private var worker: Job? = null
    private val taps = Channel<ModeTap>(Channel.CONFLATED)

    private var currentShownAtNanos: Long? = null
    private var pendingTapNanos: Long? = null

    /**
     * Starts a fresh run: cancels any previous run, clears recorded trials,
    and launches the worker coroutine (countdown -> trials -> finished). */
    fun start(config: ModeEngineConfig) {
        require(config.trialCount > 0) { "trialCount harus lebih dari 0" }
        require(config.delayMinMs >= 0 && config.delayMaxMs >= config.delayMinMs) {
            "delayMinMs harus 0..delayMaxMs"
        }
        require(config.responseTimeoutMs > 0) { "Konfigurasi durasi tidak valid" }
        require(config.maxPlausibleReactionMs > config.minPlausibleReactionMs) {
            "Aturan validitas reaksi tidak valid"
        }
        worker?.cancel()
        runRandom = Random(seed)
        countedTrials.clear()
        drainTaps()
        currentShownAtNanos = null
        pendingTapNanos = null
        _state.value = ModeEngineState.Countdown(COUNTDOWN_TOTAL_MS)
        worker = scope.launch { runSession(config) }
    }

    /**
     * Screen tap (whole-screen modes) or grid-button tap ([ModeTap.position]).
     * Only the first tap in Waiting/StimulusVisible is accepted; taps during
     * Countdown/TrialDone/inter-trial are ignored.
     */
    fun onTap(tap: ModeTap = ModeTap()) {
        when (_state.value) {
            is ModeEngineState.Waiting, is ModeEngineState.StimulusVisible -> {
                pendingTapNanos = clock.nowNanos()
                taps.trySend(tap)
            }
            else -> Unit
        }
    }

    /**
     * Called by the UI right after the stimulus frame is composed, passing the
     * actual `withFrameNanos {}` frame time. Refines the provisional stimulus
     * timestamp; ignored outside the stimulus phase.
     */
    fun onStimulusFrameRendered(frameTimeNanos: Long) {
        if (_state.value !is ModeEngineState.StimulusVisible) return
        // frameTimeNanos uses the System.nanoTime() base (Choreographer); the
        // frame's age converts it into the MonotonicClock base.
        val frameLagNanos = (System.nanoTime() - frameTimeNanos).coerceAtLeast(0)
        val shownAt = clock.nowNanos() - frameLagNanos
        val previous = currentShownAtNanos
        if (previous == null || shownAt > previous) {
            currentShownAtNanos = shownAt
            val stimulus = (_state.value as? ModeEngineState.StimulusVisible)?.stimulus
            if (stimulus != null) {
                _state.value = ModeEngineState.StimulusVisible(shownAtNanos = shownAt, stimulus = stimulus)
            }
        }
    }

    /** Stops the run and discards all recorded trials (hard reset to Idle). */
    fun cancel() {
        worker?.cancel()
        worker = null
        countedTrials.clear()
        drainTaps()
        currentShownAtNanos = null
        _state.value = ModeEngineState.Idle
    }

    /**
     * Stops the run without discarding recorded trials (audit) and returns to
     * Idle; used e.g. when the app loses focus mid-test.
     */
    fun interrupt(reason: String?) {
        worker?.cancel()
        worker = null
        currentShownAtNanos = null
        _state.value = ModeEngineState.Idle
    }

    /** Draws the stimulus for one trial from the seeded RNG (called once). */
    protected abstract fun drawStimulus(): ModeStimulus

    /** Classifies an in-time response for the shown stimulus. */
    protected abstract fun classifyInTime(
        stimulus: ModeStimulus,
        tap: ModeTap,
        reactionTimeMs: Long,
        shownAtNanos: Long,
        tapAtNanos: Long,
    ): ModeEngineTrial

    /**
     * Trial outcome when the window expires without an accepted in-time
     * response. Default: target -> omission (none + missed); non-target ->
     * correct rejection (none, correct, not a miss).
     */
    protected open fun classifyNoResponse(
        stimulus: ModeStimulus,
        shownAtNanos: Long,
    ): ModeEngineTrial = if (stimulus.isTarget) {
        ModeEngineTrial(
            trialNumber = 0,
            stimulusKind = stimulus.kind,
            isTarget = true,
            responseType = ModeResponseType.NONE,
            responseCorrect = false,
            trialStatus = TrialStatus.MISSED,
            missedResponse = true,
            stimulusAtMonotonicNs = shownAtNanos,
        )
    } else {
        ModeEngineTrial(
            trialNumber = 0,
            stimulusKind = stimulus.kind,
            isTarget = false,
            responseType = ModeResponseType.NONE,
            responseCorrect = true,
            trialStatus = TrialStatus.VALID,
            stimulusAtMonotonicNs = shownAtNanos,
        )
    }

    private suspend fun runSession(config: ModeEngineConfig) {
        runCountdown()
        repeat(config.practiceTrialCount) { index -> runTrial(trialNumber = -(index + 1), config) }
        for (number in 1..config.trialCount) {
            runTrial(trialNumber = number, config)
            if (number < config.trialCount) delay(config.restMs)
        }
        _state.value = ModeEngineState.Finished
        _onFinished.tryEmit(countedTrials.toList())
    }

    private suspend fun runCountdown() {
        for (second in 3 downTo 1) {
            _state.value = ModeEngineState.Countdown(remainingMs = second * 1_000L)
            delay(1_000)
        }
    }

    private suspend fun runTrial(trialNumber: Int, config: ModeEngineConfig) {
        drainTaps()
        currentShownAtNanos = null
        pendingTapNanos = null

        // Draw ONCE, before the wait opens; never re-randomized afterwards.
        val stimulus = drawStimulus()
        val stimulusInMs = randomDelay(config)
        _state.value = ModeEngineState.Waiting(stimulusInMs = stimulusInMs, sinceNanos = clock.nowNanos())

        if (config.falseStartRule == FalseStartRule.CONSUME_TRIAL) {
            val earlyTap = withTimeoutOrNull(stimulusInMs) { taps.receive() }
            if (earlyTap != null) {
                record(
                    ModeEngineTrial(
                        trialNumber = trialNumber,
                        stimulusKind = stimulus.kind,
                        isTarget = stimulus.isTarget,
                        responseType = ModeResponseType.EARLY,
                        responseCorrect = false,
                        trialStatus = TrialStatus.FALSE_START,
                        falseStart = true,
                        stimulusAtMonotonicNs = null,
                        tapAtMonotonicNs = pendingTapNanos,
                    ),
                )
                return
            }
        } else {
            delay(stimulusInMs)
            drainTaps() // IGNORE_TAP rule: pre-stimulus taps never score.
        }

        // Stimulus due: provisional monotonic timestamp now; refined to the
        // actual rendered frame time by onStimulusFrameRendered().
        val provisionalShownAt = clock.nowNanos()
        currentShownAtNanos = provisionalShownAt
        _state.value = ModeEngineState.StimulusVisible(shownAtNanos = provisionalShownAt, stimulus = stimulus)

        val tap = withTimeoutOrNull(config.responseTimeoutMs) { taps.receive() }
        val trial = if (tap != null) {
            val tapAt = pendingTapNanos ?: clock.nowNanos()
            val shownAt = currentShownAtNanos ?: provisionalShownAt
            val reactionMs = (tapAt - shownAt) / 1_000_000
            val classified = classifyInTime(stimulus, tap, reactionMs, shownAt, tapAt)
            val plausible = reactionMs >= config.minPlausibleReactionMs &&
                reactionMs <= config.maxPlausibleReactionMs
            if (plausible) classified else classified.copy(trialStatus = TrialStatus.INVALID)
        } else {
            // Race drain: a tap dispatched just past the deadline is a LATE
            // response (false alarm on distractors; omission on targets).
            val lateTap = taps.tryReceive().getOrNull()
            val tapAtNanos = pendingTapNanos
            if (lateTap != null && tapAtNanos != null) {
                classifyLate(stimulus, tapAtNanos)
            } else {
                classifyNoResponse(stimulus, currentShownAtNanos ?: provisionalShownAt)
                    .copy(trialNumber = trialNumber)
            }
        }
        record(trial)
    }

    /** Too-late response: omission on targets, 'late' false alarm on distractors. */
    private fun classifyLate(stimulus: ModeStimulus, tapAtNanos: Long): ModeEngineTrial =
        if (stimulus.isTarget) {
            ModeEngineTrial(
                trialNumber = 0,
                stimulusKind = stimulus.kind,
                isTarget = true,
                responseType = ModeResponseType.NONE,
                responseCorrect = false,
                trialStatus = TrialStatus.MISSED,
                missedResponse = true,
                stimulusAtMonotonicNs = currentShownAtNanos,
                tapAtMonotonicNs = tapAtNanos,
            )
        } else {
            ModeEngineTrial(
                trialNumber = 0,
                stimulusKind = stimulus.kind,
                isTarget = false,
                responseType = ModeResponseType.LATE,
                responseCorrect = false,
                trialStatus = TrialStatus.VALID,
                reactionTimeMs = null,
                stimulusAtMonotonicNs = currentShownAtNanos,
                tapAtMonotonicNs = tapAtNanos,
            )
        }

    private fun record(trial: ModeEngineTrial) {
        if (trial.trialNumber > 0) countedTrials += trial
        _state.value = ModeEngineState.TrialDone
        _onTrialDone.tryEmit(trial)
    }

    private fun randomDelay(config: ModeEngineConfig): Long =
        if (config.delayMaxMs > config.delayMinMs) {
            runRandom.nextLong(config.delayMinMs, config.delayMaxMs + 1)
        } else {
            config.delayMinMs
        }

    private fun drainTaps() {
        while (taps.tryReceive().isSuccess) {
            // Discard stale taps; taps outside the response window are ignored.
        }
    }

    protected fun nextRandom(bound: Int): Int = runRandom.nextInt(bound)

    protected fun nextRandomBoolean(probability: Double): Boolean = runRandom.nextDouble() < probability

    protected fun shuffledIndices(size: Int): List<Int> {
        val indices = (0 until size).toMutableList()
        for (i in size - 1 downTo 1) {
            val j = runRandom.nextInt(i + 1)
            val tmp = indices[i]
            indices[i] = indices[j]
            indices[j] = tmp
        }
        return indices
    }

    /** Distractor cell count from the configured ratio of non-target cells. */
    protected fun distractorCount(gridSize: Int, ratio: Double): Int {
        val nonTargetCells = gridSize * gridSize - 1
        return floor(ratio.coerceIn(0.0, 1.0) * nonTargetCells).toInt().coerceIn(0, nonTargetCells)
    }

    private companion object {
        const val COUNTDOWN_TOTAL_MS = 3_000L
    }
}
