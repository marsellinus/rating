package com.ratig.app.feature.identification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.feature.identification.WorkerCandidate
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.MetricTile
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Which symbology the scanner overlay is opened for. */
enum class ScannerKind { BARCODE, QR }

/**
 * Worker identification entry point (examiner):
 * manual NIK input / barcode / QR -> confirm worker -> protocol + shift ->
 * session creation (TEST_INSTRUCTIONS takes over via onSessionReady).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationRoute(
    onBack: () -> Unit,
    onSessionReady: (String) -> Unit,
    onOpenWorkerEdit: () -> Unit = {},
) {
    val viewModel: IdentificationViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fresh identification after results; confirm-dialog for unsaved workers.
    DisposableEffect(Unit) {
        viewModel.onScreenResumed()
        onDispose { }
    }

    LaunchedEffect(state.sessionReady) {
        state.sessionReady?.let {
            viewModel.consumeSessionReady()
            onSessionReady(it)
        }
    }

    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissTransientMessage()
        }
    }

    var scannerKind by rememberSaveable { mutableStateOf<ScannerKind?>(null) }
    val openScanner = scannerKind
    if (openScanner != null) {
        val formats = if (openScanner == ScannerKind.QR) {
            listOf(ScannerFormat.QR_CODE)
        } else {
            listOf(ScannerFormat.CODE_128)
        }
        ScannerScreen(
            formats = formats,
            onDetected = { value, isQr ->
                scannerKind = null
                viewModel.onScanResult(value, isQr)
            },
            onClose = { scannerKind = null },
        )
    } else {
        var manualInputVisible by rememberSaveable { mutableStateOf(false) }
        IdentificationScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onOpenWorkerEdit = onOpenWorkerEdit,
            onOpenManualInput = { manualInputVisible = true },
            manualInputVisible = manualInputVisible,
            onOpenScanner = { scannerKind = it },
            onNikChange = viewModel::onNikChange,
            onClearNik = viewModel::clearNik,
            onRetrySearch = viewModel::retrySearch,
            onConfirmWorker = viewModel::confirmWorker,
            onConfirmSwitch = viewModel::confirmSwitchWorker,
            onDismissSwitch = viewModel::dismissSwitchWorker,
            onKeepCurrent = viewModel::keepCurrentWorker,
            onStartFresh = viewModel::startFreshIdentification,
            onSelectProtocol = viewModel::selectProtocol,
            onSelectShift = viewModel::selectShift,
            onRetrySetup = viewModel::retrySetup,
            onStartSession = viewModel::startSession,
            onRefreshCounters = viewModel::refreshCounters,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IdentificationScreen(
    state: IdentificationUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenWorkerEdit: () -> Unit,
    onOpenManualInput: () -> Unit,
    manualInputVisible: Boolean,
    onOpenScanner: (ScannerKind) -> Unit,
    onNikChange: (String) -> Unit,
    onClearNik: () -> Unit,
    onRetrySearch: () -> Unit,
    onConfirmWorker: () -> Unit,
    onConfirmSwitch: () -> Unit,
    onDismissSwitch: () -> Unit,
    onKeepCurrent: () -> Unit,
    onStartFresh: () -> Unit,
    onSelectProtocol: (String) -> Unit,
    onSelectShift: (String?) -> Unit,
    onRetrySetup: () -> Unit,
    onStartSession: () -> Unit,
    onRefreshCounters: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identifikasi Pekerja") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusAndCounters(state = state, onRefresh = onRefreshCounters)

            Text(
                "Mulai identifikasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            BigActionButton(
                label = "Input NIK",
                icon = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
                onClick = onOpenManualInput,
            )
            BigActionButton(
                label = "Scan Barcode",
                icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                onClick = { onOpenScanner(ScannerKind.BARCODE) },
            )
            BigActionButton(
                label = "Scan QR",
                icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                onClick = { onOpenScanner(ScannerKind.QR) },
            )

            if (manualInputVisible || state.nikInput.isNotEmpty()) {
                NikInputField(
                    value = state.nikInput,
                    error = state.nikError,
                    minLength = state.nikMinLength,
                    maxLength = state.nikMaxLength,
                    onValueChange = onNikChange,
                    onClear = onClearNik,
                )
            }

            SearchResultSection(
                state = state,
                onRetrySearch = onRetrySearch,
                onConfirmWorker = onConfirmWorker,
                onOpenWorkerEdit = onOpenWorkerEdit,
            )

            if (state.confirmedWorker != null) {
                SessionSetupSection(
                    state = state,
                    onSelectProtocol = onSelectProtocol,
                    onSelectShift = onSelectShift,
                    onRetrySetup = onRetrySetup,
                    onStartSession = onStartSession,
                )
            }

            state.switchCandidate?.let { candidate ->
                val currentName = state.confirmedWorker?.worker?.fullName.orEmpty()
                ConfirmDialog(
                    title = "Ganti pekerja?",
                    message =
                        "Pemeriksaan untuk $currentName belum diproses. " +
                            "Gunakan hasil pencarian untuk NIK ${candidate.maskedNik} " +
                            "(${candidate.worker.fullName})?",
                    confirmLabel = "Ganti Pekerja",
                    onConfirm = onConfirmSwitch,
                    onDismiss = onDismissSwitch,
                )
            }

            if (state.showResumePrompt) {
                val name = state.confirmedWorker?.worker?.fullName.orEmpty()
                ConfirmDialog(
                    title = "Lanjutkan pekerja ini?",
                    message =
                        "Masih ada pekerja terkonfirmasi ($name) yang belum memiliki sesi " +
                            "pemeriksaan. Identifikasi baru akan menghapus data tersebut dari layar.",
                    confirmLabel = "Identifikasi Baru",
                    dismissLabel = "Lanjutkan",
                    onConfirm = onStartFresh,
                    onDismiss = onKeepCurrent,
                )
            }
        }
    }
}

@Composable
private fun StatusAndCounters(
    state: IdentificationUiState,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Online/offline chip: icon + text, color is never the only cue.
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (state.isOnline) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (state.isOnline) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (state.isOnline) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (state.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Text("Segarkan")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricTile(
            label = "Pemeriksaan hari ini",
            value = state.todayExamCount?.toString() ?: "—",
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = "Sesi belum tersinkron",
            value = state.unsyncedSessions.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BigActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NikInputField(
    value: String,
    error: String?,
    minLength: Int,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nomor Induk Kependudukan (NIK)") },
            placeholder = { Text("Masukkan $minLength digit NIK") },
            isError = error != null,
            supportingText = {
                Text(
                    error ?: "Hanya angka; NIK tersimpan sebagai teks (awalan 0 aman).",
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Hapus NIK")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
    }
}

@Composable
private fun SearchResultSection(
    state: IdentificationUiState,
    onRetrySearch: () -> Unit,
    onConfirmWorker: () -> Unit,
    onOpenWorkerEdit: () -> Unit,
) {
    when (val search = state.search) {
        IdentificationSearch.Idle -> Unit

        IdentificationSearch.Searching -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Mencari pekerja...", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is IdentificationSearch.Found -> WorkerCard(
            candidate = search.candidate,
            lastExam = state.lastExam,
            confirmed = state.confirmedWorker?.worker?.id == search.candidate.worker.id,
            onConfirm = onConfirmWorker,
        )

        IdentificationSearch.NotFound -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Pekerja tidak ditemukan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Periksa kembali NIK yang dimasukkan, lalu cari lagi. " +
                        "Pekerja baru harus didaftarkan terlebih dahulu oleh petugas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetrySearch) { Text("Cari Lagi") }
                    Button(onClick = onOpenWorkerEdit) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Daftarkan Pekerja")
                    }
                }
            }
        }

        is IdentificationSearch.Failed -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    search.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                OutlinedButton(onClick = onRetrySearch) { Text("Coba Lagi") }
            }
        }
    }
}

@Composable
private fun WorkerCard(
    candidate: WorkerCandidate,
    lastExam: com.ratig.app.domain.model.TestSession?,
    confirmed: Boolean,
    onConfirm: () -> Unit,
) {
    val worker = candidate.worker
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    worker.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (worker.activeStatus) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Text(
                        if (worker.activeStatus) "Aktif" else "Tidak Aktif",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Masked NIK only — the full NIK is never re-displayed.
            Text(
                "NIK: ${candidate.maskedNik}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            DetailRow("Nomor Induk", worker.employeeNumber)
            DetailRow("Departemen", worker.departmentName ?: "-")
            DetailRow("Area Kerja", worker.workAreaName ?: "-")
            DetailRow("Jabatan", worker.jobTitle ?: "-")
            DetailRow("Shift", worker.shiftName ?: "-")

            Text(
                lastExamSummary(lastExam),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SourceBadge(candidate = candidate)

            if (!worker.activeStatus) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Pekerja tidak aktif — pemeriksaan tidak dapat dimulai. " +
                            "Hubungi administrator untuk mengaktifkan kembali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider()

            if (!confirmed) {
                Button(
                    onClick = onConfirm,
                    enabled = worker.activeStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Konfirmasi Pekerja")
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Pekerja dikonfirmasi",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(candidate: WorkerCandidate) {
    val updatedLabel = rememberCacheTime(candidate.cacheUpdatedAt)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (candidate.fromCache) Icons.Outlined.History else Icons.Outlined.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (candidate.fromCache) "Cache — diperbarui $updatedLabel" else "Data server",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun rememberCacheTime(updatedAt: Instant?): String {
    if (updatedAt == null) return "-"
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(TimeProvider.DEFAULT_ZONE)
    return formatter.format(updatedAt)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun lastExamSummary(lastExam: com.ratig.app.domain.model.TestSession?): String {
    val completedAt = lastExam?.completedAt
    if (lastExam == null || completedAt == null) return "Belum ada riwayat pemeriksaan."
    val date = TimeProvider.formatForDisplay(completedAt)
    val protocol = lastExam.protocolName?.let { " · $it" }.orEmpty()
    return "Pemeriksaan terakhir: $date$protocol"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionSetupSection(
    state: IdentificationUiState,
    onSelectProtocol: (String) -> Unit,
    onSelectShift: (String?) -> Unit,
    onRetrySetup: () -> Unit,
    onStartSession: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Pemeriksaan Baru",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text("Protokol", style = MaterialTheme.typography.titleSmall)
            if (state.loadingSetup) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                state.setupError?.let { error ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onRetrySetup) { Text("Coba Lagi") }
                    }
                }
                if (!state.protocols.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.protocols.forEach { protocol ->
                            val selected = protocol.id == state.selectedProtocolId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectProtocol(protocol.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = { onSelectProtocol(protocol.id) })
                                Spacer(Modifier.width(4.dp))
                                Column {
                                    Text(
                                        protocol.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        "v${protocol.protocolVersion} · " +
                                            "${protocol.trialCount} kali percobaan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else if (state.setupError == null) {
                    Text(
                        "Tidak ada protokol aktif. Hubungi administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Shift (opsional)", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedShiftId == null,
                    onClick = { onSelectShift(null) },
                    label = { Text("Sesuai profil pekerja") },
                )
                state.shifts.forEach { shift ->
                    FilterChip(
                        selected = state.selectedShiftId == shift.id,
                        onClick = { onSelectShift(shift.id) },
                        label = { Text(shift.name) },
                    )
                }
            }

            Button(
                onClick = onStartSession,
                enabled = state.selectedProtocolId != null &&
                    state.protocols.isNotEmpty() &&
                    !state.creatingSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (state.creatingSession) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Membuat sesi...")
                } else {
                    Text("Mulai Pemeriksaan")
                }
            }
        }
    }
}
