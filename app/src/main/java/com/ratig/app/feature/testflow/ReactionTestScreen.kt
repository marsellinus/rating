package com.ratig.app.feature.testflow

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.timing.EngineConfig
import com.ratig.app.core.timing.EngineState
import com.ratig.app.core.timing.EngineTrial
import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.core.timing.ReactionTimeEngine
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import com.ratig.app.domain.repository.FinalizeRequest
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the [ReactionTimeEngine]. All network access happens either before the
 * first stimulus (protocol load) or after the last trial (finalize) - never
 * during active measurement.
 */
@HiltViewModel
class ReactionTestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: TestSessionRepository,
    private val protocolRepository: ProtocolRepository,
    private val clock: MonotonicClock,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val engine = ReactionTimeEngine(
        clock = clock,
        random = Random.Default,
        scope = viewModelScope,
    )

    data class UiState(
        val loading: Boolean = true,
        val loadError: String? = null,
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

    private var engineConfig: EngineConfig? = null
    private var runStartNanos: Long? = null

    init {
        viewModelScope.launch {
            engine.onFinished.collect { finalizeResult() }
        }
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
        val current = engine.state.value
        if (current is EngineState.Idle || current is EngineState.Finished) return
        engine.interrupt("Aplikasi berpindah ke latar")
        _uiState.update { it.copy(interrupted = true) }
    }

    /** "Ulangi dari awal": session stays in_progress, the engine restarts. */
    fun restartTest() {
        val config = engineConfig ?: return
        runStartNanos = clock.nowNanos()
        _uiState.update { it.copy(interrupted = false) }
        engine.start(config)
    }

    /** "Batalkan sesi": stop the engine and mark the session interrupted. */
    suspend fun abortSession(reason: String) {
        engine.cancel()
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
        val configuration = protocol.configuration
        val config = EngineConfig(
            trialCount = protocol.trialCount,
            delayMinMs = protocol.stimulusDelayMinMs,
            delayMaxMs = protocol.stimulusDelayMaxMs,
            responseTimeoutMs = protocol.responseTimeoutMs,
            interTrialDelayMs = configuration.interTrialDelayMs,
            minPlausibleReactionMs = configuration.minPlausibleReactionMs,
            practiceTrialCount = configuration.practiceTrialCount,
        )
        engineConfig = config
        runStartNanos = clock.nowNanos()
        _uiState.update {
            it.copy(
                loading = false,
                trialCount = config.trialCount,
                practiceTrialCount = config.practiceTrialCount,
            )
        }
        engine.start(config)
    }

    private suspend fun finalizeInternal() {
        if (_uiState.value.finalizing) return
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
                    trialStatus = trial.status,
                    falseStart = trial.falseStart,
                    missedResponse = trial.missedResponse,
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

/** Neutral test backdrop; stimulus phase switches to an unambiguous bright green. */
private val TestBackdrop = Color(0xFF101010)
private val WaitCueColor = Color(0xFF232323)
private val StimulusGreen = Color(0xFF22C55E)

@Composable
fun ReactionTestRoute(onFinished: (String) -> Unit, onAborted: () -> Unit) {
    val viewModel: ReactionTestViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engineState by viewModel.engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var feedback by remember { mutableStateOf<EngineTrial?>(null) }
    var practiceCompleted by remember { mutableIntStateOf(0) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.finished.collect { sessionId -> onFinished(sessionId) }
    }
    LaunchedEffect(Unit) {
        viewModel.engine.onTrialDone.collect { trial ->
            feedback = trial
            if (trial.trialNumber < 0) practiceCompleted += 1
            delay(300)
            feedback = null
        }
    }

    // Lock to sensor portrait for the whole measurement; restore on exit.
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    // Losing focus (call, home screen) interrupts the running measurement.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onLifecycleStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            uiState.loading -> {
                Box(Modifier.fillMaxSize().background(TestBackdrop)) {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
            }

            uiState.loadError != null -> ErrorState(
                message = uiState.loadError.orEmpty(),
                onRetry = viewModel::retryLoad,
            )

            uiState.finalizeError != null -> ErrorState(
                message = "Hasil tes tidak dapat disimpan: ${uiState.finalizeError}. " +
                    "Finalisasi dapat diulang tanpa menggandakan data.",
                onRetry = viewModel::finalizeResult,
            )

            uiState.finalizing -> {
                Box(Modifier.fillMaxSize().background(TestBackdrop), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = "Tes selesai - menyimpan hasil...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        uiState.completedDurationMs?.let { duration ->
                            Text(
                                text = "Durasi sesi: ${duration / 1000} detik",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            else -> TestPhaseArea(
                viewModel = viewModel,
                engineState = engineState,
                uiState = uiState,
                feedback = feedback,
                practiceCompleted = practiceCompleted,
                onCancelRequest = { showCancelDialog = true },
            )
        }
    }

    if (showCancelDialog) {
        ConfirmDialog(
            title = "Batalkan sesi?",
            message = "Pengukuran akan dihentikan dan sesi ditandai terganggu.",
            confirmLabel = "Ya, batalkan",
            destructive = true,
            onConfirm = {
                showCancelDialog = false
                scope.launch {
                    viewModel.abortSession("Dibatalkan petugas")
                    onAborted()
                }
            },
            onDismiss = { showCancelDialog = false },
        )
    }

    if (uiState.interrupted) {
        AlertDialog(
            onDismissRequest = { /* must choose: restart or abort */ },
            title = { Text("Tes terganggu") },
            text = {
                Text(
                    "Aplikasi berpindah ke latar saat tes berjalan. " +
                        "Ulangi tes dari awal atau batalkan sesi.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::restartTest) { Text("Ulangi dari awal") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.abortSession("Tes terganggu: aplikasi berpindah ke latar")
                        onAborted()
                    }
                }) { Text("Batalkan sesi") }
            },
        )
    }
}

@Composable
private fun TestPhaseArea(
    viewModel: ReactionTestViewModel,
    engineState: EngineState,
    uiState: ReactionTestViewModel.UiState,
    feedback: EngineTrial?,
    practiceCompleted: Int,
    onCancelRequest: () -> Unit,
) {
    val stimulus = engineState is EngineState.StimulusVisible
    val backdrop = if (stimulus) StimulusGreen else TestBackdrop
    val contentColor = if (stimulus) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
            .pointerInput(Unit) { detectTapGestures { viewModel.engine.onTap() } },
    ) {
        when (val state = engineState) {
            is EngineState.Countdown -> {
                BigText("${state.remainingMs / 1000}", Modifier.align(Alignment.Center), contentColor)
            }

            is EngineState.Waiting -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(28.dp))
                        .background(WaitCueColor)
                        .padding(horizontal = 48.dp, vertical = 28.dp),
                ) {
                    Text(
                        text = "TUNGGU...",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            is EngineState.StimulusVisible -> {
                // Report the actual rendered frame time once per stimulus.
                LaunchedEffect(Unit) {
                    val frameTime = withFrameNanos { it }
                    viewModel.engine.onStimulusFrameRendered(frameTime)
                }
                BigText("TAP!", Modifier.align(Alignment.Center), Color.Black)
            }

            is EngineState.TrialDone -> {
                TrialFeedbackText(feedback, Modifier.align(Alignment.Center))
            }

            EngineState.Idle, EngineState.Finished -> Unit
        }

        Text(
            text = progressLabel(viewModel, uiState, practiceCompleted),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )

        TextButton(
            onClick = onCancelRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding(),
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        ) {
            Text("Batalkan")
        }

        Text(
            text = bottomHint(engineState),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            color = contentColor.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun progressLabel(
    viewModel: ReactionTestViewModel,
    uiState: ReactionTestViewModel.UiState,
    practiceCompleted: Int,
): String = when {
    uiState.practiceTrialCount > 0 && viewModel.engine.trials.isEmpty() ->
        "Latihan ${minOf(practiceCompleted + 1, uiState.practiceTrialCount)} " +
            "dari ${uiState.practiceTrialCount}"

    else ->
        "Percobaan ${minOf(viewModel.engine.trials.size + 1, uiState.trialCount)} " +
            "dari ${uiState.trialCount}"
}

private fun bottomHint(engineState: EngineState): String = when (engineState) {
    is EngineState.Waiting -> "Tunggu kotak hijau muncul, lalu ketuk secepat mungkin"
    is EngineState.StimulusVisible -> "TAP SEKARANG"
    else -> ""
}

@Composable
private fun BigText(text: String, modifier: Modifier, color: Color) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 72.sp,
        lineHeight = 80.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun TrialFeedbackText(trial: EngineTrial?, modifier: Modifier) {
    if (trial == null) return
    val message = when {
        trial.falseStart -> "Terlalu cepat!"
        trial.status == TrialStatus.MISSED -> "Terlewat!"
        trial.status == TrialStatus.INVALID -> "Sinyal tidak valid"
        trial.status == TrialStatus.VALID -> "${trial.reactionTimeMs} ms"
        else -> ""
    }
    if (message.isEmpty()) return
    Text(
        text = message,
        modifier = modifier,
        color = if (trial.falseStart || trial.status != TrialStatus.VALID) {
            Color(0xFFFFC107)
        } else {
            Color.White
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}
