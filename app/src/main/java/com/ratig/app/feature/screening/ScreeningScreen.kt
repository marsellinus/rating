package com.ratig.app.feature.screening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.data.local.dao.HealthScreeningDao
import com.ratig.app.data.local.entity.LocalHealthScreeningEntity
import com.ratig.app.domain.model.FitnessRecommendation
import com.ratig.app.domain.model.Vitals
import com.ratig.app.domain.model.VitalsAssessment
import com.ratig.app.domain.repository.SessionState
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.usecase.VitalsClassifier
import com.ratig.app.ui.components.SeverityBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Paramedic screening screen: blood pressure, pulse and breath alcohol, with
 * findings and a fit-to-work recommendation computed as the examiner types.
 *
 * This is the Android counterpart of the desktop RATIG screening block. It is
 * deliberately NOT a diagnosis: the recommendation is derived from the numbers
 * the examiner entered, and the examiner remains the decision maker.
 */
@HiltViewModel
class ScreeningViewModel @Inject constructor(
    private val dao: HealthScreeningDao,
    private val classifier: VitalsClassifier,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    data class UiState(
        val systolic: String = "",
        val diastolic: String = "",
        val pulse: String = "",
        val alcohol: String = "",
        val note: String = "",
        val assessment: VitalsAssessment = VitalsAssessment(),
        val recommendation: FitnessRecommendation = FitnessRecommendation.FIT,
        val saved: Boolean = false,
        val loading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = runCatching { dao.findBySession(sessionId) }.getOrNull()
            _uiState.update { state ->
                if (existing == null) {
                    state.copy(loading = false)
                } else {
                    val vitals = existing.toVitals()
                    state.copy(
                        systolic = existing.systolic?.toString().orEmpty(),
                        diastolic = existing.diastolic?.toString().orEmpty(),
                        pulse = existing.pulse?.toString().orEmpty(),
                        alcohol = existing.alcoholPercent?.toString().orEmpty(),
                        note = existing.note.orEmpty(),
                        assessment = classifier.assess(vitals),
                        recommendation = existing.recommendationValue(),
                        loading = false,
                    )
                }
            }
        }
    }

    fun onSystolic(value: String) = update(vitals = { it.copy(systolic = value.toIntOrNull()) }) {
        it.copy(systolic = value.filter(Char::isDigit).take(3))
    }

    fun onDiastolic(value: String) = update(vitals = { it.copy(diastolic = value.toIntOrNull()) }) {
        it.copy(diastolic = value.filter(Char::isDigit).take(3))
    }

    fun onPulse(value: String) = update(vitals = { it.copy(pulse = value.toIntOrNull()) }) {
        it.copy(pulse = value.filter(Char::isDigit).take(3))
    }

    fun onAlcohol(value: String) = update(
        vitals = { it.copy(alcoholPercent = value.replace(',', '.').toDoubleOrNull()) },
    ) {
        it.copy(alcohol = value.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5))
    }

    fun onNote(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    /** Recomputes findings from the current field values. */
    private fun update(vitals: (Vitals) -> Vitals, fields: (UiState) -> UiState) {
        _uiState.update { state ->
            val next = fields(state)
            val measured = vitals(currentVitals(next))
            val assessment = classifier.assess(measured)
            next.copy(
                assessment = assessment,
                // Fatigue severity is not known on this screen; the result
                // screen recombines both sides. Here the screening stands alone.
                recommendation = classifier.recommend(assessment, fatigueSeverity = -1),
            )
        }
    }

    private fun currentVitals(state: UiState) = Vitals(
        systolic = state.systolic.toIntOrNull(),
        diastolic = state.diastolic.toIntOrNull(),
        pulse = state.pulse.toIntOrNull(),
        alcoholPercent = state.alcohol.replace(',', '.').toDoubleOrNull(),
    )

    /** Persists the screening for this session; idempotent per session. */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val vitals = currentVitals(state)
        viewModelScope.launch {
            val recordedBy = (authRepository.sessionState.value as? SessionState.Authenticated)
                ?.profile?.id
                .orEmpty()
            val now = System.currentTimeMillis()
            dao.upsert(
                LocalHealthScreeningEntity.from(
                    sessionLocalId = sessionId,
                    vitals = vitals,
                    assessment = state.assessment,
                    recommendation = state.recommendation,
                    note = state.note,
                    recordedBy = recordedBy,
                    nowEpochMs = now,
                )
            )
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreeningRoute(
    onBack: () -> Unit,
    viewModel: ScreeningViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skrining Kesehatan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Isi hasil pemeriksaan fisik oleh paramedis. Kosongkan bila tidak diukur.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Sistolik (mmHg)",
                    value = state.systolic,
                    onValueChange = viewModel::onSystolic,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "Diastolik (mmHg)",
                    value = state.diastolic,
                    onValueChange = viewModel::onDiastolic,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Nadi (bpm)",
                    value = state.pulse,
                    onValueChange = viewModel::onPulse,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "Alkohol (%)",
                    value = state.alcohol,
                    onValueChange = viewModel::onAlcohol,
                    modifier = Modifier.weight(1f),
                    decimal = true,
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text("Catatan pemeriksa (opsional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            RecommendationCard(state.recommendation)

            if (state.assessment.hasFindings) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Temuan", style = MaterialTheme.typography.titleSmall)
                        state.assessment.findings.forEach { finding ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SeverityBadge(label = finding.label, severity = finding.severity)
                                Text(
                                    text = finding.kind.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Rounded.HealthAndSafety, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Simpan Skrining")
            }

            Text(
                text = "Rekomendasi dihitung dari angka yang Anda masukkan. Keputusan " +
                    "kelayakan kerja tetap berada pada pemeriksa yang berwenang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: FitnessRecommendation) {
    val (bg, fg) = when (recommendation) {
        FitnessRecommendation.FIT ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        FitnessRecommendation.FIT_WITH_NOTE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        FitnessRecommendation.NOT_FIT ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Rekomendasi",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = recommendation.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
