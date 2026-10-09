package com.ratig.app.feature.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShiftsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val shifts: List<Shift> = emptyList(),
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class ShiftsViewModel @Inject constructor(
    private val organizationRepository: OrganizationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftsUiState())
    val uiState: StateFlow<ShiftsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            organizationRepository.listShifts(activeOnly = false)
                .onSuccess { shifts -> _uiState.update { it.copy(loading = false, shifts = shifts) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage) } }
        }
    }

    fun upsertShift(shift: Shift, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            organizationRepository.upsertShift(shift)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Shift disimpan.") }
                    refresh()
                    onDone()
                }
                .onFailure { onError(it.userMessage) }
        }
    }

    fun toggleActive(shift: Shift) {
        viewModelScope.launch {
            organizationRepository.upsertShift(shift.copy(activeStatus = !shift.activeStatus))
                .onSuccess {
                    _uiState.update {
                        it.copy(actionMessage = if (shift.activeStatus) "Shift dinonaktifkan." else "Shift diaktifkan.")
                    }
                    refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }
}

private val timeRangeFormatter: (LocalTime, LocalTime) -> String = { start, end -> "${start}–$end" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsRoute(
    onBack: () -> Unit,
    viewModel: ShiftsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }
    var togglingShift by remember { mutableStateOf<Shift?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Shift") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingShift = null
                showEditDialog = true
            }) {
                Icon(Icons.Rounded.Add, contentDescription = "Tambah shift")
            }
        },
    ) { padding ->
        when {
            uiState.loading -> LoadingState()
            uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::refresh)
            uiState.shifts.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Schedule,
                title = "Belum ada shift",
                description = "Tambahkan shift kerja untuk pengelompokan jadwal dan hasil pemeriksaan.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.shifts, key = { it.id }) { shift ->
                    ShiftRowCard(
                        shift = shift,
                        onEdit = {
                            editingShift = shift
                            showEditDialog = true
                        },
                        onToggleActive = { togglingShift = shift },
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        ShiftEditDialog(
            existing = editingShift,
            onDismiss = { showEditDialog = false },
            onSave = { shift, onError ->
                viewModel.upsertShift(
                    shift,
                    onDone = { showEditDialog = false },
                    onError = onError,
                )
            },
        )
    }

    togglingShift?.let { shift ->
        ConfirmDialog(
            title = if (shift.activeStatus) "Nonaktifkan shift?" else "Aktifkan shift?",
            message = if (shift.activeStatus) {
                "Shift \"${shift.name}\" tidak akan muncul sebagai pilihan saat membuat jadwal baru. Riwayat tetap utuh."
            } else {
                "Shift \"${shift.name}\" akan kembali tersedia untuk jadwal baru."
            },
            confirmLabel = if (shift.activeStatus) "Nonaktifkan" else "Aktifkan",
            destructive = shift.activeStatus,
            onConfirm = {
                viewModel.toggleActive(shift)
                togglingShift = null
            },
            onDismiss = { togglingShift = null },
        )
    }
}

@Composable
private fun ShiftRowCard(
    shift: Shift,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = shift.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = timeRangeFormatter(shift.startTime, shift.endTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = shift.timezone,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = if (shift.activeStatus) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (shift.activeStatus) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = if (shift.activeStatus) "Aktif" else "Nonaktif",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Aksi shift")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Ubah") },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(if (shift.activeStatus) "Nonaktifkan" else "Aktifkan") },
                    onClick = { menuOpen = false; onToggleActive() },
                )
            }
        }
    }
}

@Composable
private fun ShiftEditDialog(
    existing: Shift?,
    onDismiss: () -> Unit,
    onSave: (Shift, (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var startTime by remember { mutableStateOf(existing?.startTime?.toString()?.take(5) ?: "07:00") }
    var endTime by remember { mutableStateOf(existing?.endTime?.toString()?.take(5) ?: "15:00") }
    var timezone by remember { mutableStateOf(existing?.timezone ?: "Asia/Jakarta") }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(): Shift? {
        if (name.isBlank()) {
            error = "Nama shift wajib diisi."
            return null
        }
        val start = runCatching { LocalTime.parse(startTime.trim()) }.getOrNull()
        if (start == null) {
            error = "Jam mulai tidak valid. Gunakan HH:mm, contoh: 07:00."
            return null
        }
        val end = runCatching { LocalTime.parse(endTime.trim()) }.getOrNull()
        if (end == null) {
            error = "Jam selesai tidak valid. Gunakan HH:mm, contoh: 15:00."
            return null
        }
        if (timezone.isBlank()) {
            error = "Timezone wajib diisi, contoh: Asia/Jakarta."
            return null
        }
        return Shift(
            id = existing?.id ?: "",
            name = name.trim(),
            startTime = start,
            endTime = end,
            timezone = timezone.trim(),
            activeStatus = existing?.activeStatus ?: true,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Tambah Shift" else "Ubah Shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama shift") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Jam mulai (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("Jam selesai (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    label = { Text("Timezone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                validate()?.let { shift -> onSave(shift) { message -> error = message } }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
