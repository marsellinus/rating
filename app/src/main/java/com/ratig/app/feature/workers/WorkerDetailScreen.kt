package com.ratig.app.feature.workers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.Worker
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState

/**
 * Worker profile: header + organization info + active status, "Mulai Tes"
 * examiner flow (protocol + optional shift) and the examination history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStartTest: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val viewModel: WorkerDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStartTestDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.worker?.fullName ?: "Detail Pekerja", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    state.worker?.let { worker ->
                        IconButton(onClick = { onEdit(worker.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Ubah")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))

            state.worker == null && state.error != null -> ErrorState(
                message = state.error.orEmpty(),
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding),
            )

            else -> {
                val worker = state.worker ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileCard(worker)

                    OrganizationCard(worker)

                    Button(
                        onClick = {
                            viewModel.loadTestOptions()
                            showStartTestDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mulai Tes")
                    }

                    HistorySection(
                        state = state,
                        onOpenSession = onOpenSession,
                    )
                }
            }
        }
    }

    if (showStartTestDialog) {
        StartTestDialog(
            state = state,
            onDismiss = { showStartTestDialog = false },
            onConfirm = { protocolId, shiftId, testMode ->
                viewModel.startTest(protocolId, shiftId, testMode)
            },
        )
        LaunchedEffect(state.startTest.sessionId) {
            state.startTest.sessionId?.let { sessionId ->
                showStartTestDialog = false
                onStartTest(sessionId)
            }
        }
    }
}

@Composable
private fun ProfileCard(worker: Worker) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = worker.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                WorkerStatusBadge(active = worker.activeStatus)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Badge,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "NIP ${worker.employeeNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            worker.jobTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            worker.email?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrganizationCard(worker: Worker) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Informasi Organisasi",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()
            InfoRow(label = "Departemen", value = worker.departmentName ?: "—")
            InfoRow(label = "Area Kerja", value = worker.workAreaName ?: "—")
            InfoRow(label = "Shift", value = worker.shiftName ?: "—")
            InfoRow(label = "Jabatan", value = worker.jobTitle ?: "—")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HistorySection(
    state: WorkerDetailViewModel.UiState,
    onOpenSession: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Riwayat Pemeriksaan",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            when {
                state.historyLoading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                state.history.isEmpty() -> Text(
                    text = "Belum ada riwayat pemeriksaan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.history.forEach { row ->
                    HistoryRow(row = row, onClick = { onOpenSession(row.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    row: WorkerDetailViewModel.HistoryRowUi,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = row.dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.classificationLabel ?: row.statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (row.classificationLabel != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Protocol + optional shift picker shown before a session row is created.
 * The mode picker uses plain-language labels so non-technical users can choose.
 */
private enum class ModeOption(val mode: TestMode, val title: String, val description: String) {
    CLASSIC(TestMode.CLASSIC, "Tes Kelelahan Standar", "Reaksi terhadap warna. Hasil diklasifikasi kelelahan."),
    RGB_RANDOM(TestMode.RGB_RANDOM, "Warna Acak", "Tekan saat muncul warna/shape target."),
    RANDOM_BUTTON(TestMode.RANDOM_BUTTON, "Tombol Acak", "Cari dan tekan tombol dengan lambang target."),
    FOCUS_INHIBITION(TestMode.FOCUS_INHIBITION, "Fokus (Go/No-Go)", "Tekan saat hijau, tahan saat merah."),
}

@Composable
private fun StartTestDialog(
    state: WorkerDetailViewModel.UiState,
    onDismiss: () -> Unit,
    onConfirm: (protocolId: String, shiftId: String?, testMode: TestMode) -> Unit,
) {
    var selectedProtocolId by remember(state.protocols) {
        mutableStateOf(state.protocols.firstOrNull()?.id)
    }
    var selectedShiftId by remember(state.shifts) { mutableStateOf<String?>(null) }
    var selectedMode by remember { mutableStateOf(TestMode.CLASSIC) }
    val creating = state.startTest.creating

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("Mulai Tes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.optionsLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Memuat protokol...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    // Mode picker: plain-language cards so any user can choose.
                    Text("Jenis tes", style = MaterialTheme.typography.titleSmall)
                    ModeOption.entries.forEach { option ->
                        val selected = selectedMode == option.mode
                        Surface(
                            onClick = { selectedMode = option.mode },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = { selectedMode = option.mode })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(option.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    DropdownField(
                        label = "Protokol",
                        options = state.protocols.map { protocol ->
                            DropdownOption(
                                value = protocol.id,
                                label = "${protocol.name} (v${protocol.protocolVersion})",
                            )
                        },
                        selectedValue = selectedProtocolId,
                        onSelected = { selectedProtocolId = it },
                        modifier = Modifier.fillMaxWidth(),
                        emptyHint = "Tidak ada protokol aktif",
                    )
                    DropdownField(
                        label = "Shift",
                        options = listOf(DropdownOption(value = null, label = "Tanpa shift")) +
                            state.shifts.map { shift ->
                                DropdownOption(value = shift.id, label = shift.name)
                            },
                        selectedValue = selectedShiftId,
                        onSelected = { selectedShiftId = it },
                        modifier = Modifier.fillMaxWidth(),
                        emptyHint = "Belum ada shift aktif",
                    )
                    state.startTest.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !creating && !state.optionsLoading && selectedProtocolId != null,
                onClick = {
                    val protocolId = selectedProtocolId ?: return@Button
                    onConfirm(protocolId, selectedShiftId, selectedMode)
                },
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Mulai")
                }
            }
        },
        dismissButton = {
            OutlinedButton(enabled = !creating, onClick = onDismiss) {
                Text("Batal")
            }
        },
    )
}
