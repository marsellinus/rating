package com.ratig.app.feature.testflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.TestResult
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.domain.usecase.ClassificationOutcome
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.MetricTile
import com.ratig.app.ui.components.SeverityLegendCard
import com.ratig.app.ui.components.SeverityBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Post-test result: metrics from the finalized session plus the server-side
 * classification. Read-only; RATIG output is decision support, not diagnosis.
 */
@HiltViewModel
class TestResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: TestSessionRepository,
    private val protocolRepository: ProtocolRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val result: TestResult? = null,
        val workerName: String? = null,
        /** Severity from the applied rule band; -1 when unknown/not applicable. */
        val severity: Int = -1,
        /** True when the label is still the organization-unvalidated placeholder. */
        val needsValidation: Boolean = false,
        val sessionDurationMs: Long? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = when (val loaded = sessionRepository.getResult(sessionId)) {
                is AppResult.Success -> loaded.value
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false, error = loaded.error.userMessage) }
                    return@launch
                }
            }
            if (result == null) {
                _uiState.update {
                    it.copy(loading = false, error = "Hasil belum tersedia untuk sesi ini.")
                }
                return@launch
            }
            val session = when (val loaded = sessionRepository.getSession(sessionId)) {
                is AppResult.Success -> loaded.value
                is AppResult.Failure -> null
            }
            val durationMs = session?.let { value ->
                val start = value.startedAt
                val end = value.completedAt
                if (start != null && end != null) end.toEpochMilli() - start.toEpochMilli() else null
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    result = result,
                    workerName = session?.workerName,
                    sessionDurationMs = durationMs,
                    severity = resolveSeverity(result, session?.protocolId),
                    needsValidation =
                    result.classificationLabel == ClassificationOutcome.UNVALIDATED_LABEL,
                )
            }
        }
    }

    /** Maps the stored classification code to the band severity of the applied rule. */
    private suspend fun resolveSeverity(result: TestResult, protocolId: String?): Int {
        val code = result.classificationCode ?: return -1
        if (code == ClassificationOutcome.INSUFFICIENT_DATA ||
            code == ClassificationOutcome.NOT_CONFIGURED
        ) {
            return -1
        }
        if (protocolId == null) return -1
        val rules = when (val loaded = protocolRepository.listRules(protocolId)) {
            is AppResult.Success -> loaded.value
            is AppResult.Failure -> return -1
        }
        val rule = rules.firstOrNull { it.ruleVersion == result.ruleVersion }
        return rule?.config?.bands?.firstOrNull { it.code == code }?.severity ?: -1
    }
}

@Composable
fun TestResultRoute(onDone: () -> Unit) {
    val viewModel: TestResultViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.loading -> LoadingState()

        uiState.error != null -> ErrorState(
            message = uiState.error.orEmpty(),
            onRetry = viewModel::refresh,
        )

        else -> {
            val result = uiState.result
            if (result == null) {
                EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "Hasil tidak tersedia",
                    description = "Hasil untuk sesi ini tidak ditemukan.",
                    action = { Button(onClick = viewModel::refresh) { Text("Muat ulang") } },
                )
            } else {
                ResultContent(uiState = uiState, result = result, onDone = onDone)
            }
        }
    }
}

@Composable
private fun ResultContent(
    uiState: TestResultViewModel.UiState,
    result: TestResult,
    onDone: () -> Unit,
) {
    val metrics = result.metrics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Hasil Tes RATIG",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        uiState.workerName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Metrik Waktu Reaksi", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Rata-rata", fmtMsDouble(metrics.meanReactionTimeMs), Modifier.weight(1f))
                    MetricTile("Median", fmtMsDouble(metrics.medianReactionTimeMs), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Minimum", fmtMsLong(metrics.minReactionTimeMs), Modifier.weight(1f))
                    MetricTile("Maksimum", fmtMsLong(metrics.maxReactionTimeMs), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        "Deviasi standar",
                        fmtMsDouble(metrics.standardDeviationMs),
                        Modifier.weight(1f),
                    )
                    MetricTile("Durasi sesi", fmtDuration(uiState.sessionDurationMs), Modifier.weight(1f))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Jumlah Percobaan", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Valid", "${metrics.validTrialCount}", Modifier.weight(1f))
                    MetricTile("Tidak valid", "${metrics.invalidTrialCount}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Terlewat", "${metrics.missedResponseCount}", Modifier.weight(1f))
                    MetricTile("False start", "${metrics.falseStartCount}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Respons lambat", "${metrics.slowResponseCount}", Modifier.weight(1f))
                    MetricTile(
                        "Artefak dikecualikan",
                        "${metrics.excludedArtifactCount}",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Klasifikasi", style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeverityBadge(
                        label = result.classificationLabel ?: "Tidak diketahui",
                        severity = uiState.severity,
                    )
                    if (uiState.needsValidation) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = "Kategori perlu validasi",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = result.classificationExplanation ?: "-",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (uiState.needsValidation) {
                    Text(
                        text = "Label kategori belum divalidasi organisasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "Aturan v${result.ruleVersion ?: "-"}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        SeverityLegendCard(modifier = Modifier.fillMaxWidth())

        Text(
            text = "Hasil RATIG adalah alat bantu keputusan, bukan diagnosis.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Selesai")
        }
    }
}

private fun fmtMsDouble(value: Double?): String =
    value?.let { "${String.format(Locale.US, "%.1f", it)} ms" } ?: "-"

private fun fmtMsLong(value: Long?): String = value?.let { "$it ms" } ?: "-"

private fun fmtDuration(durationMs: Long?): String = durationMs?.let { "${it / 1000} detik" } ?: "-"
