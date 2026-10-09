package com.ratig.app.feature.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.FollowUp
import com.ratig.app.domain.model.FollowUpStatus
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.TestMetrics
import com.ratig.app.domain.model.TestResult
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import com.ratig.app.domain.model.UserRole
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.FollowUpRepository
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.SessionState
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.feature.followup.FollowUpLabels
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.SeverityBadge
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun SessionDetailRoute(onBack: () -> Unit, onOpenWorker: (String) -> Unit) {
    val viewModel: SessionDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SessionDetailScreen(
        state = state,
        onBack = onBack,
        onOpenWorker = onOpenWorker,
        onRetry = viewModel::reload,
        onCreateFollowUp = viewModel::createFollowUp,
        onUpdateFollowUpStatus = viewModel::updateFollowUpStatus,
    )
}

@Composable
private fun SessionDetailScreen(
    state: SessionDetailViewModel.SessionDetailUiState,
    onBack: () -> Unit,
    onOpenWorker: (String) -> Unit,
    onRetry: () -> Unit,
    onCreateFollowUp: (String, String?, Instant?) -> Unit,
    onUpdateFollowUpStatus: (String, FollowUpStatus) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                }
                Text(
                    text = "Detail Sesi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(message = state.error, onRetry = onRetry)
                else -> {
                    val session = state.session
                    if (session == null) {
                        ErrorState(message = "Sesi tidak ditemukan.")
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SessionInfoCard(
                                session = session,
                                shiftName = state.shiftName,
                                onOpenWorker = onOpenWorker,
                            )
                            state.result?.let { result -> ResultCard(result) }
                            TrialsCard(trials = state.trials)
                            FollowUpsSection(
                                items = state.followUps,
                                canManage = state.role != null,
                                saving = state.saving,
                                actionError = state.actionError,
                                onCreate = onCreateFollowUp,
                                onUpdateStatus = onUpdateFollowUpStatus,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Session info

@Composable
private fun SessionInfoCard(
    session: TestSession,
    shiftName: String?,
    onOpenWorker: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Informasi Sesi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            InfoRow(
                label = "Pekerja",
                value = buildString {
                    append(session.workerName ?: "Pekerja")
                    session.workerNumber?.let { append(" (#$it)") }
                },
                onClick = { onOpenWorker(session.workerId) },
            )
            InfoRow("Pemeriksa", session.examinerName ?: "-")
            InfoRow("Shift", shiftName ?: "-")
            InfoRow(
                "Protokol",
                listOfNotNull(
                    session.protocolName ?: "-",
                    session.protocolVersion?.let { "v$it" },
                ).joinToString(" "),
            )
            InfoRow("Dimulai", TimeProvider.formatForDisplay(session.startedAt))
            InfoRow("Selesai", TimeProvider.formatForDisplay(session.completedAt))
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp),
                )
                SeverityBadge(
                    label = statusLabel(session.sessionStatus),
                    severity = statusSeverity(session.sessionStatus),
                )
            }
            session.interruptionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Alasan interupsi: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            if (session.deviceMetadata.isNotEmpty()) {
                Text(
                    text = "Metadata Perangkat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.deviceMetadata.forEach { (key, value) ->
                    Text(
                        text = "${deviceLabel(key)}: $value",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Text(
                text = "Lihat",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun deviceLabel(key: String): String = when (key) {
    "manufacturer" -> "Pabrikan"
    "model" -> "Model"
    "androidSdkInt" -> "API Level"
    "androidRelease" -> "Versi Android"
    "appVersionName" -> "Versi Aplikasi"
    "appVersionCode" -> "Kode Versi"
    else -> key
}

// ---------------------------------------------------------------- Result

@Composable
private fun ResultCard(result: TestResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Hasil Pemeriksaan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeverityBadge(
                    label = result.classificationLabel ?: result.classificationCode ?: "Tanpa Kategori",
                    severity = severityForCode(result.classificationCode),
                )
                result.ruleVersion?.let { version ->
                    Text(
                        text = "Aturan: v$version",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            result.classificationExplanation?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            MetricGrid(metrics = result.metrics)
        }
    }
}

@Composable
private fun MetricGrid(metrics: TestMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetricRow("Rata-rata (mean)", formatMsNullable(metrics.meanReactionTimeMs))
        MetricRow("Median", formatMsNullable(metrics.medianReactionTimeMs))
        MetricRow("Terpendek", formatLongNullable(metrics.minReactionTimeMs))
        MetricRow("Terpanjang", formatLongNullable(metrics.maxReactionTimeMs))
        MetricRow("Simpangan Baku", formatMsNullable(metrics.standardDeviationMs))
        MetricRow("Percobaan Valid", metrics.validTrialCount.toString())
        MetricRow("Percobaan Tidak Valid", metrics.invalidTrialCount.toString())
        MetricRow("Tanpa Respons", metrics.missedResponseCount.toString())
        MetricRow("Start Dini", metrics.falseStartCount.toString())
        MetricRow("Respons Lambat", metrics.slowResponseCount.toString())
        MetricRow("Artefak Dikecualikan", metrics.excludedArtifactCount.toString())
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Classification codes carry no severity in [TestResult]; derive from band code. */
private fun severityForCode(code: String?): Int = when (code) {
    "BAND_1" -> 1
    "BAND_2" -> 2
    "BAND_3" -> 3
    "BAND_4" -> 4
    else -> -1
}

private fun formatMsNullable(value: Double?): String =
    value?.let { String.format(java.util.Locale.US, "%.1f ms", it) } ?: "-"

private fun formatLongNullable(value: Long?): String = value?.let { "$it ms" } ?: "-"

// ---------------------------------------------------------------- Trials

@Composable
private fun TrialsCard(trials: List<TrialRecord>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Percobaan (${trials.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (trials.isEmpty()) {
                Text(
                    text = "Tidak ada data percobaan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                trials.sortedBy { it.trialNumber }.forEach { trial -> TrialRow(trial) }
            }
        }
    }
}

@Composable
private fun TrialRow(trial: TrialRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "#${trial.trialNumber}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(32.dp),
        )
        Icon(
            imageVector = trialIcon(trial.trialStatus),
            contentDescription = trialStatusLabel(trial.trialStatus),
            tint = trialColor(trial.trialStatus),
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trialStatusLabel(trial.trialStatus),
                style = MaterialTheme.typography.bodyMedium,
            )
            val flags = buildList {
                if (trial.falseStart) add("Start Dini")
                if (trial.missedResponse) add("Tanpa Respons")
            }
            if (flags.isNotEmpty()) {
                Text(
                    text = flags.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = trial.reactionTimeMs?.let { "$it ms" } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun trialStatusLabel(status: TrialStatus): String = when (status) {
    TrialStatus.VALID -> "Valid"
    TrialStatus.FALSE_START -> "Start Dini"
    TrialStatus.MISSED -> "Tidak Merespons"
    TrialStatus.INVALID -> "Tidak Valid"
}

private fun trialIcon(status: TrialStatus): ImageVector = when (status) {
    TrialStatus.VALID -> Icons.Rounded.CheckCircle
    TrialStatus.FALSE_START -> Icons.Rounded.Block
    TrialStatus.MISSED -> Icons.Rounded.HourglassEmpty
    TrialStatus.INVALID -> Icons.Rounded.HelpOutline
}

@Composable
private fun trialColor(status: TrialStatus): Color = when (status) {
    TrialStatus.VALID -> MaterialTheme.colorScheme.primary
    TrialStatus.FALSE_START, TrialStatus.MISSED -> MaterialTheme.colorScheme.error
    TrialStatus.INVALID -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ---------------------------------------------------------------- Follow-ups

@Composable
private fun FollowUpsSection(
    items: List<FollowUp>,
    canManage: Boolean,
    saving: Boolean,
    actionError: String?,
    onCreate: (String, String?, Instant?) -> Unit,
    onUpdateStatus: (String, FollowUpStatus) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingCompleteId by remember { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Tindak Lanjut",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (canManage) {
                OutlinedButton(
                    onClick = { showCreate = true },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah Tindak Lanjut")
                }
            }
            actionError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (items.isEmpty()) {
                Text(
                    text = "Belum ada tindak lanjut untuk sesi ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEach { followUp ->
                    FollowUpRow(
                        followUp = followUp,
                        canManage = canManage,
                        onStatusSelect = { status ->
                            if (status == FollowUpStatus.COMPLETED) {
                                pendingCompleteId = followUp.id
                            } else {
                                onUpdateStatus(followUp.id, status)
                            }
                        },
                    )
                }
            }
        }
    }
    if (showCreate) {
        CreateFollowUpDialog(
            saving = saving,
            onDismiss = { showCreate = false },
            onSubmit = { actionType, notes, dueAt ->
                showCreate = false
                onCreate(actionType, notes, dueAt)
            },
        )
    }
    pendingCompleteId?.let { id ->
        ConfirmDialog(
            title = "Tandai selesai?",
            message = "Tindak lanjut ini akan ditandai sebagai selesai.",
            confirmLabel = "Selesai",
            onConfirm = {
                pendingCompleteId = null
                onUpdateStatus(id, FollowUpStatus.COMPLETED)
            },
            onDismiss = { pendingCompleteId = null },
        )
    }
}

@Composable
private fun FollowUpRow(
    followUp: FollowUp,
    canManage: Boolean,
    onStatusSelect: (FollowUpStatus) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = FollowUpLabels.actionType(followUp.actionType),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                followUp.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    followUp.dueAt?.let { add("Tenggat: ${TimeProvider.formatDateForDisplay(it)}") }
                    followUp.assignedToName?.let { add("Ditugaskan: $it") }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canManage) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(FollowUpLabels.statusLabel(followUp.status))
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = "Ubah status tindak lanjut",
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        FollowUpStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(FollowUpLabels.statusLabel(status)) },
                                onClick = {
                                    expanded = false
                                    onStatusSelect(status)
                                },
                            )
                        }
                    }
                }
            } else {
                SeverityBadge(
                    label = FollowUpLabels.statusLabel(followUp.status),
                    severity = FollowUpLabels.statusSeverity(followUp.status),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun CreateFollowUpDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (actionType: String, notes: String?, dueAt: Instant?) -> Unit,
) {
    val actionTypes = listOf(
        com.ratig.app.domain.model.FollowUpAction.RECHECK to "Pemeriksaan Ulang",
        com.ratig.app.domain.model.FollowUpAction.REST to "Istirahat",
        com.ratig.app.domain.model.FollowUpAction.EVALUATION to "Evaluasi Petugas",
        com.ratig.app.domain.model.FollowUpAction.ESCALATION to "Eskalasi Fit-to-Work",
        com.ratig.app.domain.model.FollowUpAction.OTHER to "Lainnya",
    )
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(actionTypes.first()) }
    var notes by remember { mutableStateOf("") }
    var dueText by remember { mutableStateOf("") }
    var dueError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Tindak Lanjut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = "Jenis Tindakan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(selectedType.second, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            actionTypes.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        expanded = false
                                        selectedType = code to label
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = {
                        dueText = it
                        dueError = null
                    },
                    label = { Text("Tenggat (opsional, YYYY-MM-DD)") },
                    isError = dueError != null,
                    supportingText = { dueError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val trimmed = dueText.trim()
                    val parsed: Instant? = if (trimmed.isEmpty()) {
                        null
                    } else {
                        runCatching {
                            LocalDate.parse(trimmed)
                                .atStartOfDay(TimeProvider.DEFAULT_ZONE)
                                .toInstant()
                        }.getOrNull()
                    }
                    if (trimmed.isNotEmpty() && parsed == null) {
                        dueError = "Format tanggal tidak valid (contoh: 2026-10-15)"
                    } else {
                        onSubmit(selectedType.first, notes.trim().ifBlank { null }, parsed)
                    }
                },
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

private fun statusLabel(status: SessionStatus): String = when (status) {
    SessionStatus.CREATED -> "Dibuat"
    SessionStatus.IN_PROGRESS -> "Berlangsung"
    SessionStatus.INTERRUPTED -> "Terputus"
    SessionStatus.PENDING_SYNC -> "Menunggu Sinkron"
    SessionStatus.FINALIZED -> "Selesai"
    SessionStatus.FAILED -> "Gagal"
}

private fun statusSeverity(status: SessionStatus): Int = when (status) {
    SessionStatus.FINALIZED -> 1
    SessionStatus.CREATED, SessionStatus.IN_PROGRESS, SessionStatus.PENDING_SYNC -> 2
    SessionStatus.INTERRUPTED -> 3
    SessionStatus.FAILED -> 4
}

// ---------------------------------------------------------------- ViewModel

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testSessionRepository: TestSessionRepository,
    private val followUpRepository: FollowUpRepository,
    private val organizationRepository: OrganizationRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    data class SessionDetailUiState(
        val loading: Boolean = true,
        val error: String? = null,
        val session: TestSession? = null,
        val shiftName: String? = null,
        val trials: List<TrialRecord> = emptyList(),
        val result: TestResult? = null,
        val followUps: List<FollowUp> = emptyList(),
        val role: UserRole? = null,
        val saving: Boolean = false,
        val actionError: String? = null,
    )

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                role = (authRepository.sessionState.value as? SessionState.Authenticated)?.profile?.role,
            )
        }
        if (sessionId.isBlank()) {
            _uiState.update { it.copy(loading = false, error = "Sesi tidak ditemukan.") }
        } else {
            load()
        }
    }

    fun reload() = load()

    fun createFollowUp(actionType: String, notes: String?, dueAt: Instant?) {
        _uiState.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            val followUp = FollowUp(
                id = "",
                sessionId = sessionId,
                actionType = actionType,
                notes = notes,
                dueAt = dueAt,
            )
            when (val result = followUpRepository.create(followUp)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(saving = false) }
                    refreshFollowUps()
                }
                is AppResult.Failure ->
                    _uiState.update { it.copy(saving = false, actionError = result.error.userMessage) }
            }
        }
    }

    fun updateFollowUpStatus(id: String, status: FollowUpStatus) {
        _uiState.update { it.copy(saving = true, actionError = null) }
        viewModelScope.launch {
            when (val result = followUpRepository.updateStatus(id, status)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(saving = false) }
                    refreshFollowUps()
                }
                is AppResult.Failure ->
                    _uiState.update { it.copy(saving = false, actionError = result.error.userMessage) }
            }
        }
    }

    private suspend fun refreshFollowUps() {
        when (val res = followUpRepository.listForSession(sessionId)) {
            is AppResult.Success -> _uiState.update { it.copy(followUps = res.value) }
            is AppResult.Failure -> Unit
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val sessionRes = testSessionRepository.getSession(sessionId)) {
                is AppResult.Failure ->
                    _uiState.update { it.copy(loading = false, error = sessionRes.error.userMessage) }
                is AppResult.Success -> {
                    val session = sessionRes.value
                    var shiftName: String? = null
                    session.shiftId?.let { shiftId ->
                        when (val shiftsRes = organizationRepository.listShifts(activeOnly = false)) {
                            is AppResult.Success ->
                                shiftName = shiftsRes.value.firstOrNull { it.id == shiftId }?.name
                            is AppResult.Failure -> Unit
                        }
                    }
                    val trialsRes = testSessionRepository.getTrials(sessionId)
                    val resultRes = testSessionRepository.getResult(sessionId)
                    val followUpsRes = followUpRepository.listForSession(sessionId)
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            session = session,
                            shiftName = shiftName,
                            trials = (trialsRes as? AppResult.Success)?.value ?: current.trials,
                            result = (resultRes as? AppResult.Success)?.value ?: current.result,
                            followUps = (followUpsRes as? AppResult.Success)?.value ?: current.followUps,
                        )
                    }
                }
            }
        }
    }
}
