package com.ratig.app.feature.testflow.modes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratig.app.core.timing.modes.ModeEngineState
import com.ratig.app.core.timing.modes.ModeStimulus
import com.ratig.app.core.timing.modes.ModeTap
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import kotlinx.coroutines.launch

/**
 * Measurement screen for the non-classic modes (RGB_RANDOM, RANDOM_BUTTON,
 * FOCUS_INHIBITION). Same navigation contract as the classic test screen:
 * `onFinished(sessionId)` / `onAborted()`.
 */
@Composable
fun ModeTestRoute(onFinished: (String) -> Unit, onAborted: () -> Unit) {
    val viewModel: ModeTestViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showCancelDialog by remember { mutableStateOf(false) }

    // The test must not be left by accident (see classic test screen).
    BackHandler(enabled = !uiState.finalizing) { showCancelDialog = true }

    LaunchedEffect(Unit) {
        viewModel.finished.collect { sessionId -> onFinished(sessionId) }
    }

    // Losing focus interrupts the running measurement (same policy as classic).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onLifecycleStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(ModeBackdrop)) {
        when {
            uiState.loading -> LoadingState()

            uiState.loadError != null -> ErrorState(
                message = uiState.loadError.orEmpty(),
                onRetry = viewModel::retryLoad,
            )

            uiState.finalizeError != null -> ErrorState(
                message = "Hasil tes tidak dapat disimpan: ${uiState.finalizeError}. " +
                    "Finalisasi dapat diulang tanpa menggandakan data.",
                onRetry = viewModel::finalizeResult,
            )

            uiState.finalizing -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Tes selesai - menyimpan hasil...", color = Color.White)
                }
            }

            else -> ModeTestArea(
                viewModel = viewModel,
                engineState = engineState,
                targetHint = uiState.targetHint,
                trialCount = uiState.trialCount,
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
            onDismissRequest = { },
            title = { Text("Tes terganggu") },
            text = {
                Text("Aplikasi berpindah ke latar saat tes berjalan. " +
                    "Ulangi tes dari awal atau batalkan sesi.")
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
private fun ModeTestArea(
    viewModel: ModeTestViewModel,
    engineState: ModeEngineState,
    targetHint: String,
    trialCount: Int,
    onCancelRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { viewModel.requireEngine().onTap() }
            },
    ) {
        when (val state = engineState) {
            is ModeEngineState.Countdown -> {
                BigModeText("${state.remainingMs / 1000}", Modifier.align(Alignment.Center))
            }

            is ModeEngineState.Waiting -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(ModeCueColor, RoundedCornerShape(28.dp))
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

            is ModeEngineState.StimulusVisible -> {
                LaunchedEffect(state.stimulus) {
                    val frameTime = withFrameNanos { it }
                    viewModel.requireEngine().onStimulusFrameRendered(frameTime)
                }
                when (val stimulus = state.stimulus) {
                    is ModeStimulus.Rgb -> RgbStimulusView(stimulus, Modifier.fillMaxSize())
                    is ModeStimulus.Grid -> GridStimulusView(
                        stimulus = stimulus,
                        enabled = true,
                        onCellTap = { position ->
                            viewModel.requireEngine().onTap(ModeTap(position))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    is ModeStimulus.FocusCircle ->
                        FocusStimulusView(stimulus, Modifier.fillMaxSize())
                }
            }

            else -> Unit
        }

        // Persistent bottom hint + a clear exit button.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (targetHint.isNotBlank()) {
                Surface(
                    color = ModeCueColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = targetHint,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            TextButton(onClick = onCancelRequest) {
                Text("Batalkan sesi", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun BigModeText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Color.White,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.displayLarge,
    )
}
