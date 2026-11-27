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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.data.local.dao.HealthScreeningDao
import com.ratig.app.data.local.entity.LocalHealthScreeningEntity
import com.ratig.app.domain.model.TestResult
import com.ratig.app.domain.model.formatVersionLabel
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.SessionState
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.domain.model.FitnessRecommendation
import com.ratig.app.domain.usecase.ClassificationOutcome
import com.ratig.app.domain.usecase.ModeQualityClassifier
import com.ratig.app.domain.usecase.VitalsClassifier
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.MetricTile
import com.ratig.app.domain.model.FatigueRule
import com.ratig.app.ui.components.LegendBand
import com.ratig.app.ui.components.SeverityLegendCard
import com.ratig.app.ui.components.bandDisplayName
import com.ratig.app.ui.components.bandMeaning
import com.ratig.app.ui.components.rangeLabel
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
    private val screeningDao: HealthScreeningDao,
    private val vitalsClassifier: VitalsClassifier,
    private val modeQualityClassifier: ModeQualityClassifier,
    authRepository: AuthRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])
    /** Duration supplied by the all-modes flow; -1 means derive from the session. */
    private val navigationDurationMs: Long =
        (savedStateHandle.get<Long>("durationMs") ?: -1L)

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
        /** Paramedic screening recorded for this session, when present. */
        val screening: LocalHealthScreeningEntity? = null,
        /**
         * Fit-to-work recommendation combining the paramedic screening with the
         * fatigue classification. Null when no screening was recorded: the
         * reaction test alone does not decide fitness.
         */
        val combinedRecommendation: FitnessRecommendation? = null,
        /**
         * Response-quality indicator for the observation modes. Null for the
         * Classic mode, where the fatigue classification is the indicator.
         */
        val modeQuality: ModeQualityClassifier.Quality? = null,
        /** One person = one account: a User never moves on to another worker. */
        val isWorkerSelfService: Boolean = false,
        /**
         * Bands of the rule that was actually applied, for the legend. Null when
         * the rule cannot be resolved, in which case the legend omits ranges
         * rather than showing invented ones.
         */
        val legendBands: List<LegendBand>? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                isWorkerSelfService =
                    (authRepository.sessionState.value as? SessionState.Authenticated)
                        ?.profile?.role == UserRole.USER,
            )
        }
        refresh()
        // The screening screen writes to the database and pops back to this
        // screen. Observing the row keeps the summary and the combined verdict
        // in sync without depending on this ViewModel being recreated.
        viewModelScope.launch {
            screeningDao.observeBySession(sessionId).collect { screening ->
                _uiState.update { state ->
                    state.copy(
                        screening = screening,
                        combinedRecommendation = combineRecommendation(
                            screening = screening,
                            fatigueSeverity = state.severity,
                        ),
                    )
                }
            }
        }
    }

    /** Worst-case verdict of the paramedic screening and the fatigue result. */
    private fun combineRecommendation(
        screening: LocalHealthScreeningEntity?,
        fatigueSeverity: Int,
    ): FitnessRecommendation? = screening?.let {
        vitalsClassifier.recommend(
            vitals = vitalsClassifier.assess(it.toVitals()),
            fatigueSeverity = fatigueSeverity,
        )
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
            val durationMs = if (navigationDurationMs > 0) {
                navigationDurationMs
            } else {
                session?.let { value ->
                    val start = value.startedAt
                    val end = value.completedAt
                    if (start != null && end != null) end.toEpochMilli() - start.toEpochMilli() else null
                }
            }
            val appliedRule = resolveRule(result, session?.protocolId)
            val severityForDecision = appliedRule
                ?.config?.bands
                ?.firstOrNull { it.code == result.classificationCode }
                ?.severity
                ?: -1
            _uiState.update {
                it.copy(
                    loading = false,
                    result = result,
                    workerName = session?.workerName,
                    sessionDurationMs = durationMs,
                    severity = severityForDecision,
                    needsValidation =
                    result.classificationLabel == ClassificationOutcome.UNVALIDATED_LABEL,
                    combinedRecommendation = combineRecommendation(
                        screening = it.screening,
                        fatigueSeverity = severityForDecision,
                    ),
                    modeQuality = modeQualityClassifier.classify(result),
                    legendBands = appliedRule?.config?.bands?.map { band ->
                        LegendBand(
                            band = bandDisplayName(band.label, band.severity),
                            severity = band.severity,
                            range = band.rangeLabel(),
                            meaning = bandMeaning(band.severity),
                        )
                    },
                )
            }
        }
    }

    /** The rule version that produced this result, for severity and legend. */
    private suspend fun resolveRule(result: TestResult, protocolId: String?): FatigueRule? {
        if (protocolId == null) return null
        val rules = when (val loaded = protocolRepository.listRules(protocolId)) {
            is AppResult.Success -> loaded.value
            is AppResult.Failure -> return null
        }
        return rules.firstOrNull { it.ruleVersion == result.ruleVersion }
    }
}

@Composable
fun TestResultRoute(
    onDone: () -> Unit,
    onNextWorker: () -> Unit = onDone,
    onOpenScreening: () -> Unit = {},
) {
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
                ResultContent(
                    uiState = uiState,
                    result = result,
                    onDone = onDone,
                    onNextWorker = onNextWorker,
                    onOpenScreening = onOpenScreening,
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    uiState: TestResultViewModel.UiState,
    result: TestResult,
    onDone: () -> Unit,
    onNextWorker: () -> Unit,
    onOpenScreening: () -> Unit,
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

        uiState.modeQuality?.let { quality ->
            // Observation modes do not produce a fatigue band, so they get their
            // own indicator on the same colour scale.
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Kualitas Respons", style = MaterialTheme.typography.labelLarge)
                    SeverityBadge(label = quality.label, severity = quality.severity)
                    Text(
                        text = quality.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Mode pengamatan: indikator ini menilai ketepatan respons, " +
                            "bukan tingkat kelelahan.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = "Aturan ${result.ruleVersion?.let(::formatVersionLabel) ?: "-"}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        uiState.screening?.let { screening ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Skrining Kesehatan", style = MaterialTheme.typography.labelLarge)
                    val vitals = screening.toVitals()
                    Text(
                        text = buildString {
                            vitals.systolic?.let { append("TD $it") }
                            vitals.diastolic?.let { append("/$it mmHg") }
                            vitals.pulse?.let {
                                if (isNotEmpty()) append(" · ")
                                append("Nadi $it bpm")
                            }
                            vitals.alcoholPercent?.let {
                                if (isNotEmpty()) append(" · ")
                                append("Alkohol $it %")
                            }
                            if (isEmpty()) append("Belum ada pengukuran")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val screeningOnly = screening.recommendationValue()
                    val combined = uiState.combinedRecommendation ?: screeningOnly
                    Text(
                        text = "Rekomendasi kelayakan kerja",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SeverityBadge(
                        label = combined.label,
                        severity = combined.severity,
                    )
                    if (combined != screeningOnly) {
                        // Explain why the combined verdict is stricter than the
                        // screening alone: the fatigue result raised it.
                        Text(
                            text = "Ditingkatkan oleh hasil tes waktu reaksi " +
                                "(skrining saja: ${screeningOnly.label.lowercase()}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    screening.note?.let {
                        Text(
                            text = "Catatan: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SeverityLegendCard(
            modifier = Modifier.fillMaxWidth(),
            bands = uiState.legendBands,
        )

        Text(
            text = "Hasil RATIG adalah alat bantu keputusan, bukan diagnosis.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onOpenScreening,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.Rounded.HealthAndSafety, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Skrining Kesehatan (Paramedis)")
        }

        // Multi-worker operational flow: the examiner normally continues
        // straight to the next worker, so that action is primary and the
        // return-to-home action is secondary.
        if (!uiState.isWorkerSelfService) {
            Button(
                onClick = onNextWorker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Outlined.PersonSearch, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Periksa Pekerja Berikutnya")
            }
        }
        OutlinedButton(
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
