package com.ratig.app.feature.testflow.allmodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.core.timing.EngineConfig
import com.ratig.app.core.timing.EngineState
import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.core.timing.ReactionTimeEngine
import com.ratig.app.core.timing.modes.ButtonGridModeEngine
import com.ratig.app.core.timing.modes.FocusInhibitionModeEngine
import com.ratig.app.core.timing.modes.ModeConfiguration
import com.ratig.app.core.timing.modes.ModeEngineBase
import com.ratig.app.core.timing.modes.ModeEngineConfig
import com.ratig.app.core.timing.modes.ModeEngineState
import com.ratig.app.core.timing.modes.ModeTap
import com.ratig.app.core.timing.modes.RgbRandomModeEngine
import com.ratig.app.domain.repository.FinalizeRequest
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * One session, four modes, in order.
 *
 * Tester requirement: "Mulai Tes" must not ask which mode to run. A single
 * session runs Classic -> RGB Random -> Button Grid -> Focus Inhibition, with a
 * short transition screen between them, and finalizes ONCE with all trials.
 *
 * Classification stays authoritative on the CLASSIC trials only (server rule:
 * band applies only to classic mode). The three observation modes keep their
 * own per-mode metrics via [TrialRecord.isTarget]/responseType columns.
 */
@HiltViewModel
class AllModesTestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: TestSessionRepository,
    private val protocolRepository: ProtocolRepository,
    private val clock: MonotonicClock,
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    /** Ordered stages of a full examination. */
    sealed interface Stage {
        data object CountdownIntro : Stage
        data object Classic : Stage
        data object TransitionToRgb : Stage
        data object Rgb : Stage
        data object TransitionToGrid : Stage
        data object Grid : Stage
        data object TransitionToFocus : Stage
        data object Focus : Stage
        data object Saving : Stage
        data class Done(val sessionId: String) : Stage
    }

    data class UiState(
        val stage: Stage = Stage.CountdownIntro,
        val loading: Boolean = true,
        val error: String? = null,
        val progressLabel: String = "",
        val trialNumber: Int = 0,
        val trialTotal: Int = 0,
        val classicState: EngineState = EngineState.Idle,
        val modeState: ModeEngineState = ModeEngineState.Idle,
        /** What the worker must do right now. Always visible during a mode. */
        val instruction: String = "",
        /** Result of the last response, shown immediately after each trial. */
        val feedback: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var classicEngine: ReactionTimeEngine? = null
    private var modeEngine: ModeEngineBase? = null
    private val allTrials = mutableListOf<TrialRecord>()
    private var practiceCount = 0
    private var frozenConfig: ModeConfiguration = ModeConfiguration.DEFAULT
    private var seedLong: Long = 0L
    private var startedAtNanos: Long = 0L

    init { viewModelScope.launch { prepare() } }

    private suspend fun prepare() {
        val session = when (val r = sessionRepository.getSession(sessionId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> {
                _uiState.update { it.copy(loading = false, error = r.error.userMessage) }
                return
            }
        }
        if (session == null) {
            _uiState.update { it.copy(loading = false, error = "Sesi tidak ditemukan.") }
            return
        }
        val protocol = when (val r = protocolRepository.getProtocol(session.protocolId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> null
        }
        frozenConfig = ModeConfiguration.fromConfigurationJson(session.modeConfig)
            .let { if (session.modeConfig.isNullOrBlank()) it else it }
            .sanitized()
        seedLong = session.randomSeed?.fold(1125899906842597L) { acc, c -> 31 * acc + c.code }
            ?: UUID.randomUUID().hashCode().toLong()
        practiceCount = protocol?.configuration?.practiceTrialCount ?: 0
        val trials = protocol?.trialCount ?: 10
        _uiState.update {
            it.copy(
                loading = false,
                trialTotal = trials * 4,
                progressLabel = "Mode 1 dari 4 · Waktu Reaksi Klasik",
            )
        }
    }

    /** Called by the transition screen to start the next stage. */
    fun startStage() {
        when (_uiState.value.stage) {
            Stage.CountdownIntro -> startClassic()
            Stage.TransitionToRgb -> startMode(ModeStage.RGB)
            Stage.TransitionToGrid -> startMode(ModeStage.GRID)
            Stage.TransitionToFocus -> startMode(ModeStage.FOCUS)
            else -> Unit
        }
    }

    fun onTap() {
        when (val stage = _uiState.value.stage) {
            Stage.Classic -> classicEngine?.onTap()
            Stage.Rgb, Stage.Grid, Stage.Focus -> modeEngine?.onTap(ModeTap())
            else -> Unit
        }
    }

    fun onGridCellTap(position: Int) {
        if (_uiState.value.stage == Stage.Grid) modeEngine?.onTap(ModeTap(position))
    }

    fun onStimulusFrame(frameNanos: Long) {
        when (_uiState.value.stage) {
            Stage.Classic -> classicEngine?.onStimulusFrameRendered(frameNanos)
            Stage.Rgb, Stage.Grid, Stage.Focus -> modeEngine?.onStimulusFrameRendered(frameNanos)
            else -> Unit
        }
    }

    private enum class ModeStage(val mode: TestMode, val label: String) {
        RGB(TestMode.RGB_RANDOM, "Mode 2 dari 4 · Warna Acak"),
        GRID(TestMode.RANDOM_BUTTON, "Mode 3 dari 4 · Kisi Tombol Acak"),
        FOCUS(TestMode.FOCUS_INHIBITION, "Mode 4 dari 4 · Fokus & Inhibisi"),
    }

    private fun startClassic() {
        val protocol = _uiState.value.trialTotal / 4
        val engine = ReactionTimeEngine(clock, scope = viewModelScope)
        classicEngine = engine
        startedAtNanos = clock.nowNanos()
        viewModelScope.launch {
            engine.onTrialDone.collect { trial ->
                allTrials += trial.copy(trialNumber = allTrials.size + 1).toRecord(sessionId)
                _uiState.update {
                    it.copy(
                        trialNumber = allTrials.size,
                        feedback = when {
                            trial.falseStart -> "Terlalu cepat — tunggu warna hijau"
                            trial.missedResponse -> "Terlewat — tidak ada respons"
                            trial.status == TrialStatus.INVALID -> "Respons terlalu cepat untuk dihitung"
                            else -> "Benar · ${trial.reactionTimeMs} ms"
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            engine.state.collect { st ->
                _uiState.update { state ->
                    val enteringNewTrial = st is EngineState.Waiting ||
                        st is EngineState.Countdown
                    state.copy(
                        classicState = st,
                        feedback = if (enteringNewTrial) null else state.feedback,
                    )
                }
            }
        }
        viewModelScope.launch {
            engine.onFinished.firstOrNull()
            _uiState.update { it.copy(stage = Stage.TransitionToRgb) }
        }
        engine.start(
            EngineConfig(
                trialCount = protocol,
                delayMinMs = 1_500,
                delayMaxMs = 4_500,
                responseTimeoutMs = 2_000,
                interTrialDelayMs = frozenConfig.restMs,
                minPlausibleReactionMs = frozenConfig.minPlausibleReactionMs,
                practiceTrialCount = practiceCount,
            )
        )
        _uiState.update {
            it.copy(
                stage = Stage.Classic,
                instruction = "TEKAN layar secepatnya saat warna HIJAU muncul. " +
                    "Jangan menekan sebelum warna muncul.",
                feedback = null,
            )
        }
    }

    private fun startMode(which: ModeStage) {
        val protocolTrials = _uiState.value.trialTotal / 4
        val config = ModeEngineConfig(
            trialCount = protocolTrials,
            delayMinMs = 1_500,
            delayMaxMs = 4_500,
            responseTimeoutMs = 2_000,
            practiceTrialCount = 0, // practice is already covered in classic
            restMs = frozenConfig.restMs,
            falseStartRule = frozenConfig.falseStart,
            minPlausibleReactionMs = frozenConfig.minPlausibleReactionMs,
            maxPlausibleReactionMs = frozenConfig.maxPlausibleReactionMs,
        )
        val engine = when (which) {
            ModeStage.RGB -> RgbRandomModeEngine(clock, seedLong, frozenConfig, viewModelScope)
            ModeStage.GRID -> ButtonGridModeEngine(clock, seedLong, frozenConfig, viewModelScope)
            ModeStage.FOCUS -> FocusInhibitionModeEngine(clock, seedLong, frozenConfig, viewModelScope)
        }
        modeEngine = engine
        viewModelScope.launch {
            engine.onTrialDone.collect { trial ->
                allTrials += trial.copy(trialNumber = allTrials.size + 1).toRecord(sessionId)
                _uiState.update {
                    it.copy(
                        trialNumber = allTrials.size,
                        feedback = when {
                            trial.falseStart -> "Terlalu cepat — tunggu tanda muncul"
                            trial.missedResponse -> "Terlewat — tidak ada respons"
                            trial.trialStatus == TrialStatus.INVALID ->
                                "Respons terlalu cepat untuk dihitung"
                            trial.responseCorrect == true ->
                                trial.reactionTimeMs?.let { "Benar · $it ms" } ?: "Benar"
                            trial.responseType == "none" ->
                                "Benar — respons ditahan dengan tepat"
                            else -> "Salah — bukan target yang diminta"
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            engine.state.collect { st ->
                _uiState.update { state ->
                    val enteringNewTrial = st is ModeEngineState.Waiting ||
                        st is ModeEngineState.Countdown
                    state.copy(
                        modeState = st,
                        feedback = if (enteringNewTrial) null else state.feedback,
                    )
                }
            }
        }
        viewModelScope.launch {
            engine.onFinished.firstOrNull()
            _uiState.update {
                it.copy(
                    stage = when (which) {
                        ModeStage.RGB -> Stage.TransitionToGrid
                        ModeStage.GRID -> Stage.TransitionToFocus
                        ModeStage.FOCUS -> Stage.Saving
                    },
                )
            }
            if (which == ModeStage.FOCUS) finalize()
        }
        engine.start(config)
        _uiState.update {
            it.copy(
                stage = when (which) {
                    ModeStage.RGB -> Stage.Rgb
                    ModeStage.GRID -> Stage.Grid
                    ModeStage.FOCUS -> Stage.Focus
                },
                progressLabel = which.label,
                instruction = when (which) {
                    ModeStage.RGB -> {
                        val c = frozenConfig.rgbTargetColor
                        "TARGET: ${c.shape.label} ${c.label} (${c.symbol}). " +
                            "Tekan HANYA jika bentuk dan warna itu muncul. " +
                            "Yang lain JANGAN ditekan."
                    }
                    ModeStage.GRID ->
                        "TARGET: simbol \"${frozenConfig.targetSymbol}\". " +
                            "Tekan HANYA tombol yang berisi simbol itu. " +
                            "Tombol lain dan sel kosong JANGAN ditekan."
                    ModeStage.FOCUS ->
                        "TARGET: lingkaran HIJAU = TEKAN. Lingkaran MERAH = JANGAN ditekan."
                },
                feedback = null,
            )
        }
    }

    private suspend fun finalize() {
        android.util.Log.e("RATIG_ALLMODES", "finalize start: trials=${allTrials.size} session=$sessionId")
        val device = DeviceMetadata.current()
        when (val result = sessionRepository.finalizeSession(
            FinalizeRequest(
                sessionId = sessionId,
                trials = allTrials.toList(),
                appVersion = device.appVersionName,
                deviceMetadata = device,
            )
        )) {
            is AppResult.Success -> _uiState.update {
                it.copy(stage = Stage.Done(sessionId), error = null)
            }
            is AppResult.Failure -> {
                android.util.Log.e("RATIG_ALLMODES", "finalize FAILED: ${result.error.userMessage}")
                _uiState.update { it.copy(error = result.error.userMessage) }
            }
        }
    }

    fun retryFinalize() {
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch { finalize() }
    }
}

private fun com.ratig.app.core.timing.EngineTrial.toRecord(sessionId: String) = TrialRecord(
    sessionId = sessionId,
    trialNumber = trialNumber,
    stimulusAtMonotonicNs = stimulusAtMonotonicNs,
    tapAtMonotonicNs = tapAtMonotonicNs,
    reactionTimeMs = reactionTimeMs,
    trialStatus = status,
    falseStart = falseStart,
    missedResponse = missedResponse,
)

private fun com.ratig.app.core.timing.modes.ModeEngineTrial.toRecord(sessionId: String) = TrialRecord(
    sessionId = sessionId,
    trialNumber = trialNumber,
    stimulusAtMonotonicNs = stimulusAtMonotonicNs,
    tapAtMonotonicNs = tapAtMonotonicNs,
    reactionTimeMs = reactionTimeMs,
    trialStatus = trialStatus,
    falseStart = falseStart,
    missedResponse = missedResponse,
    stimulusKind = stimulusKind,
    isTarget = isTarget,
    responseType = responseType,
    responseCorrect = responseCorrect,
)
