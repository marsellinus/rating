package com.ratig.app.feature.testflow.allmodes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ratig.app.core.timing.EngineState
import com.ratig.app.core.timing.modes.ModeEngineState
import com.ratig.app.core.timing.modes.ModeStimulus
import com.ratig.app.core.timing.modes.RgbColor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect

/**
 * Single-session, four-mode test screen. The transition stages exist so the
 * worker can breathe between modes and so the examiner can see which mode is
 * about to start.
 */
@Composable
fun AllModesTestRoute(
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AllModesTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = true) { onCancel() }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when (val stage = state.stage) {
        is AllModesTestViewModel.Stage.Done -> {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Selesai", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Keempat mode telah dijalankan dalam satu sesi.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Button(onClick = { onDone(stage.sessionId) }, Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Lihat Hasil")
                }
            }
        }

        is AllModesTestViewModel.Stage.Saving -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.error == null) {
                        CircularProgressIndicator()
                        Text("Menyimpan hasil...")
                    } else {
                        Text("Gagal menyimpan", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error)
                        Text(state.error!!, color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center)
                        Button(onClick = viewModel::retryFinalize) { Text("Coba Lagi") }
                    }
                }
            }
        }

        AllModesTestViewModel.Stage.CountdownIntro,
        is AllModesTestViewModel.Stage.TransitionToRgb,
        is AllModesTestViewModel.Stage.TransitionToGrid,
        is AllModesTestViewModel.Stage.TransitionToFocus -> {
            val title = when (stage) {
                AllModesTestViewModel.Stage.CountdownIntro -> "Pemeriksaan Siap Dimulai"
                is AllModesTestViewModel.Stage.TransitionToRgb -> "Berikutnya: Warna Acak"
                is AllModesTestViewModel.Stage.TransitionToGrid -> "Berikutnya: Kisi Tombol Acak"
                is AllModesTestViewModel.Stage.TransitionToFocus -> "Berikutnya: Fokus & Inhibisi"
                else -> ""
            }
            val hint = when (stage) {
                AllModesTestViewModel.Stage.CountdownIntro ->
                    "Empat pemeriksaan akan dijalankan berurutan dalam satu sesi."
                is AllModesTestViewModel.Stage.TransitionToRgb ->
                    "Tekan hanya saat warna target muncul."
                is AllModesTestViewModel.Stage.TransitionToGrid ->
                    "Cari dan tekan tombol berlambang target."
                is AllModesTestViewModel.Stage.TransitionToFocus ->
                    "Tekan hijau, tahan diri pada merah."
                else -> ""
            }
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = viewModel::startStage, Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Lanjut")
                }
            }
        }

        AllModesTestViewModel.Stage.Classic -> {
            TestSurface(onTap = viewModel::onTap, onFrame = viewModel::onStimulusFrame, state = state) {
                val s = state.classicState
                when (s) {
                    is EngineState.Countdown -> BigText("${s.remainingMs / 1000}")
                    is EngineState.Waiting -> BigText("TUNGGU...", cue = true)
                    is EngineState.StimulusVisible -> BigText("TAP!", green = true)
                    else -> Unit
                }
            }
        }

        AllModesTestViewModel.Stage.Rgb -> {
            TestSurface(onTap = viewModel::onTap, onFrame = viewModel::onStimulusFrame, state = state) {
                val s = state.modeState
                if (s is ModeEngineState.StimulusVisible) {
                    val st = s.stimulus
                    if (st is com.ratig.app.core.timing.modes.ModeStimulus.Rgb) {
                        Box(
                            Modifier.size(140.dp).clip(RoundedCornerShape(20.dp))
                                .background(rgbColor(st.color)),
                            contentAlignment = Alignment.Center,
                        ) { Text(st.color.symbol, fontSize = androidx.compose.ui.unit.TextUnit(40f,
                            androidx.compose.ui.unit.TextUnitType.Sp), color = Color.Black) }
                    }
                } else if (s is ModeEngineState.Waiting) {
                    BigText("TUNGGU...", cue = true)
                }
            }
        }

        AllModesTestViewModel.Stage.Grid -> {
            // The grid cells own their taps. Keeping the whole-surface tap here
            // would score a positionless (always wrong) response first.
            TestSurface(
                onTap = null,
                onFrame = viewModel::onStimulusFrame,
                state = state,
            ) {
                val s = state.modeState
                if (s is ModeEngineState.StimulusVisible &&
                    s.stimulus is com.ratig.app.core.timing.modes.ModeStimulus.Grid
                ) {
                    val grid = s.stimulus as com.ratig.app.core.timing.modes.ModeStimulus.Grid
                    val n = kotlin.math.sqrt(grid.symbols.size.toDouble()).toInt()
                    Column(Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center) {
                        for (row in 0 until n) {
                            Row(Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (col in 0 until n) {
                                    val idx = row * n + col
                                    val symbol = grid.symbols[idx]
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (symbol == null) Color(0xFF37474F)
                                                else MaterialTheme.colorScheme.primaryContainer
                                            )
                                            .pointerInput(idx) {
                                                detectTapGestures {
                                                    viewModel.onGridCellTap(idx)
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (symbol != null) {
                                            Text(symbol, style = MaterialTheme.typography.headlineMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (s is ModeEngineState.Waiting) {
                    BigText("TUNGGU...", cue = true)
                }
            }
        }

        AllModesTestViewModel.Stage.Focus -> {
            TestSurface(onTap = viewModel::onTap, onFrame = viewModel::onStimulusFrame, state = state) {
                val s = state.modeState
                if (s is ModeEngineState.StimulusVisible &&
                    s.stimulus is com.ratig.app.core.timing.modes.ModeStimulus.FocusCircle
                ) {
                    val circle = s.stimulus as com.ratig.app.core.timing.modes.ModeStimulus.FocusCircle
                    Box(
                        Modifier.size(180.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (circle.isGo) Color(0xFF2E7D32) else Color(0xFFC62828)),
                    )
                } else if (s is ModeEngineState.Waiting) {
                    BigText("TUNGGU...", cue = true)
                }
            }
        }
    }
}

@Composable
private fun TestSurface(
    onTap: (() -> Unit)?,
    onFrame: (Long) -> Unit,
    state: AllModesTestViewModel.UiState,
    stimulus: @Composable () -> Unit,
) {
    val backdrop = MaterialTheme.colorScheme.surface
    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(Unit) { detectTapGestures { onTap() } }
    } else Modifier
    Box(Modifier.fillMaxSize().background(backdrop).then(tapModifier)) {
        // Stimulus is centred in its own layer so it can never push the header.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { stimulus() }

        // Header is one Column, laid out top-down: no overlapping text.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${state.progressLabel} · ${state.trialNumber}/${state.trialTotal}",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Instruction(state)
            Feedback(state.feedback)
        }
    }
}

@Composable
private fun BigText(text: String, cue: Boolean = false, green: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (green) Color(0xFF1B5E20)
                    else if (cue) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun rgbColor(c: com.ratig.app.core.timing.modes.RgbColor): Color = when (c) {
    com.ratig.app.core.timing.modes.RgbColor.RED -> Color(0xFFE53935)
    com.ratig.app.core.timing.modes.RgbColor.GREEN -> Color(0xFF43A047)
    com.ratig.app.core.timing.modes.RgbColor.BLUE -> Color(0xFF1E88E5)
}

@Composable
private fun Instruction(state: AllModesTestViewModel.UiState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(
            text = state.instruction,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun Feedback(text: String?) {
    if (text == null) return
    val good = text.startsWith("Benar")
    Surface(
        color = if (good) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        contentColor = if (good) Color(0xFF1B5E20) else Color(0xFFB71C1C),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
        )
    }
}
