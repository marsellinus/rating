package com.ratig.app.feature.testflow.modes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.core.timing.modes.ButtonGridModeEngine
import com.ratig.app.core.timing.modes.FocusInhibitionModeEngine
import com.ratig.app.core.timing.modes.ModeConfiguration
import com.ratig.app.core.timing.modes.ModeEngineBase
import com.ratig.app.core.timing.modes.ModeEngineConfig
import com.ratig.app.core.timing.modes.RgbColor
import com.ratig.app.core.timing.modes.RgbRandomModeEngine
import com.ratig.app.core.timing.modes.modeLabel
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.repository.FinalizeRequest
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the mode engine for non-classic sessions (RGB_RANDOM, RANDOM_BUTTON,
 * FOCUS_INHIBITION). Same contract as [com.ratig.app.feature.testflow.ReactionTestViewModel]:
 * all network access happens before the first stimulus or after the last
 * trial - never during active measurement.
 */
@HiltViewModel
class ModeTestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: TestSessionRepository,
    private val protocolRepository: ProtocolRepository,
    private val clock: MonotonicClock,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    data class UiState(
        val loading: Boolean = true,
        val loadError: String? = null,
        val testMode: TestMode = TestMode.RGB_RANDOM,
        /** One-line bottom hint for the current mode, e.g. the target rule. */
        val targetHint: String = "",
        val trialCount: Int = 0,
        val practiceTrialCount: Int = 0,
        val interrupted: Boolean = false,
        val finalizing: Boolean = false,
        val finalizeError: String? = null,
        val completedDurationMs: Long? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val finished: SharedFlow<String> = _finished.asSharedFlow()

    /** Created in [loadAndStart] once session + protocol are resolved. */
    private var engineInstance: ModeEngineBase? = null
    private var engineConfig: ModeEngineConfig? = null
    private var finishedJob: kotlinx.coroutines.Job? = null
    private var runStartNanos: Long? = null

    val engineReady: Boolean get() = engineInstance != null

    fun requireEngine(): ModeEngineBase = checkNotNull(engineInstance) { "Mesin tes belum siap" }

    init {
        viewModelScope.launch { loadAndStart() }
    }

    fun retryLoad() {
        viewModelScope.launch { loadAndStart() }
    }

    /** Finalizes (or retries finalizing - the RPC is idempotent server-side). */
    fun finalizeResult() {
        viewModelScope.launch { finalizeInternal() }
    }

    fun onLifecycleStop() {
        val engine = engineInstance ?: return
        val current = engine.state.value
        if (current is com.ratig.app.core.timing.modes.ModeEngineState.Idle ||
            current is com.ratig.app.core.timing.modes.ModeEngineState.Finished
        ) {
            return
        }
        engine.interrupt("Aplikasi berpindah ke latar")
        _uiState.update { it.copy(interrupted = true) }
    }

    /** "Ulangi dari awal": the engine restarts with the SAME seed, so the stimulus sequence is reproduced. */
    fun restartTest() {
        val config = engineConfig ?: return
        val engine = engineInstance ?: return
        runStartNanos = clock.nowNanos()
        _uiState.update { it.copy(interrupted = false) }
        engine.start(config)
    }

    /** "Batalkan sesi": stop the engine and mark the session interrupted. */
    suspend fun abortSession(reason: String) {
        engineInstance?.cancel()
        sessionRepository.markInterrupted(sessionId, reason)
    }

    private suspend fun loadAndStart() {
        _uiState.update { it.copy(loading = true, loadError = null) }
        val session = when (val result = sessionRepository.getSession(sessionId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                _uiState.update { it.copy(loading = false, loadError = result.error.userMessage) }
                return
            }
        }
        val protocol = when (val result = protocolRepository.getProtocol(session.protocolId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                _uiState.update { it.copy(loading = false, loadError = result.error.userMessage) }
                return
            }
        }
        val mode = session.testMode
        if (mode == TestMode.CLASSIC) {
            _uiState.update { it.copy(loading = false, loadError = "Sesi ini bukan sesi mode khusus.") }
            return
        }

        // Config priority: frozen session copy -> protocol -> defaults (approved order).
        val modeConfig = (
            session.modeConfig?.takeIf { it.isNotBlank() }
                ?.let { ModeConfiguration.fromConfigurationJson(it) }
                ?: protocol.configuration.mode
                ?: ModeConfiguration.DEFAULT
            ).sanitized()

        // Seeded reproducibility: the seed is saved on the session at creation;
        // a missing seed (older sessions) is generated once per VM and used for
        // every restart of this run so the sequence stays reproducible.
        val seedString = session.randomSeed ?: UUID.randomUUID().toString()
        val seedLong = seedString.fold(1125899906842597L) { acc, c -> 31 * acc + c.code }

        val config = ModeEngineConfig(
            trialCount = protocol.trialCount,
            delayMinMs = protocol.stimulusDelayMinMs,
            delayMaxMs = protocol.stimulusDelayMaxMs,
            responseTimeoutMs = protocol.responseTimeoutMs,
            practiceTrialCount = protocol.configuration.practiceTrialCount,
            restMs = modeConfig.restMs,
            falseStartRule = modeConfig.falseStart,
            minPlausibleReactionMs = modeConfig.minPlausibleReactionMs,
            maxPlausibleReactionMs = modeConfig.maxPlausibleReactionMs,
        )
        engineConfig = config
        engineInstance = createEngine(mode, seedLong, modeConfig)
        observeEngineFinished()
        runStartNanos = clock.nowNanos()
        _uiState.update {
            it.copy(
                loading = false,
                testMode = mode,
                targetHint = targetHint(mode, modeConfig),
                trialCount = config.trialCount,
                practiceTrialCount = config.practiceTrialCount,
            )
        }
        requireEngine().start(config)
    }

    private fun createEngine(mode: TestMode, seedLong: Long, modeConfig: ModeConfiguration): ModeEngineBase =
        when (mode) {
            TestMode.RGB_RANDOM -> RgbRandomModeEngine(clock, seedLong, modeConfig, viewModelScope)
            TestMode.RANDOM_BUTTON -> ButtonGridModeEngine(clock, seedLong, modeConfig, viewModelScope)
            TestMode.FOCUS_INHIBITION -> FocusInhibitionModeEngine(clock, seedLong, modeConfig, viewModelScope)
            TestMode.CLASSIC -> throw IllegalStateException("Mode klasik tidak memakai mesin mode")
        }

    /** Single collector on the engine's finish event; retries never duplicate it. */
    private fun observeEngineFinished() {
        if (finishedJob?.isActive == true) return
        val engine = engineInstance ?: return
        finishedJob = viewModelScope.launch {
            engine.onFinished.collect { finalizeResult() }
        }
    }

    private fun targetHint(mode: TestMode, modeConfig: ModeConfiguration): String = when (mode) {
        TestMode.RGB_RANDOM -> {
            val color = modeConfig.rgbTargetColor
            "Tekan saat ${color.shape.label} ${color.label} + ${color.symbol}"
        }
        TestMode.RANDOM_BUTTON -> "Tekan tombol berlambang \"${modeConfig.targetSymbol}\""
        TestMode.FOCUS_INHIBITION -> "HIJAU = tekan, MERAH = diam"
        TestMode.CLASSIC -> ""
    }

    private suspend fun finalizeInternal() {
        if (_uiState.value.finalizing) return
        val engine = engineInstance ?: return
        _uiState.update { it.copy(finalizing = true, finalizeError = null) }
        val durationMs = runStartNanos?.let { (clock.nowNanos() - it) / 1_000_000 }
        val device = DeviceMetadata.current()
        val request = FinalizeRequest(
            sessionId = sessionId,
            trials = engine.trials.map { trial ->
                TrialRecord(
                    sessionId = sessionId,
                    trialNumber = trial.trialNumber,
                    stimulusAtMonotonicNs = trial.stimulusAtMonotonicNs,
                    tapAtMonotonicNs = trial.tapAtMonotonicNs,
                    reactionTimeMs = trial.reactionTimeMs,
                    trialStatus = trial.trialStatus,
                    falseStart = trial.falseStart,
                    missedResponse = trial.missedResponse,
                    stimulusKind = trial.stimulusKind,
                    isTarget = trial.isTarget,
                    responseType = trial.responseType,
                    responseCorrect = trial.responseCorrect,
                )
            },
            appVersion = device.appVersionName,
            deviceMetadata = device,
        )
        when (val result = sessionRepository.finalizeSession(request)) {
            is AppResult.Success -> {
                engine.cancel()
                _uiState.update { it.copy(finalizing = false, completedDurationMs = durationMs) }
                _finished.tryEmit(sessionId)
            }
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    finalizing = false,
                    finalizeError = result.error.userMessage,
                    completedDurationMs = durationMs,
                )
            }
        }
    }
}
