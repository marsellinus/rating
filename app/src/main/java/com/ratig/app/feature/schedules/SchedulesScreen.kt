package com.ratig.app.feature.schedules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.ScheduleEntry
import com.ratig.app.domain.model.ScheduleStatus
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.model.WorkerFilter
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.ScheduleRepository
import com.ratig.app.domain.repository.WorkerRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.SeverityBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchedulesUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val selectedDate: LocalDate = LocalDate.now(TimeProvider.DEFAULT_ZONE),
    val entries: List<ScheduleEntry> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val workerResults: List<Worker> = emptyList(),
    val currentUserId: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val workerRepository: WorkerRepository,
    private val organizationRepository: OrganizationRepository,
    supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SchedulesUiState(currentUserId = supabase.auth.currentUserOrNull()?.id),
    )
    val uiState: StateFlow<SchedulesUiState> = _uiState.asStateFlow()

    init {
        loadShifts()
        loadDay(_uiState.value.selectedDate)
    }

    fun refresh() = loadDay(_uiState.value.selectedDate)

    fun shiftDay(days: Long) = selectDate(_uiState.value.selectedDate.plusDays(days))

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadDay(date)
    }

    fun searchWorkers(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(workerResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            workerRepository.list(WorkerFilter(query = query, limit = 10)).onSuccess { results ->
                _uiState.update { it.copy(workerResults = results) }
            }
        }
    }

    fun createSchedule(
        worker: Worker,
        shift: Shift?,
        date: LocalDate,
        timeText: String,
        notes: String?,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
        if (time == null) {
            onError("Format jam tidak valid. Gunakan HH:mm, contoh: 09:30.")
            return
        }
        val examinerId = _uiState.value.currentUserId
        if (examinerId == null) {
            onError("Sesi tidak ditemukan. Silakan masuk kembali.")
            return
        }
        viewModelScope.launch {
            val scheduledAt = ZonedDateTime.of(date, time, TimeProvider.DEFAULT_ZONE).toInstant()
            scheduleRepository.create(
                ScheduleEntry(
                    id = "",
                    workerId = worker.id,
                    examinerId = examinerId,
                    shiftId = shift?.id,
                    scheduledAt = scheduledAt,
                    status = ScheduleStatus.SCHEDULED,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                ),
            ).onSuccess {
                _uiState.update { it.copy(actionMessage = "Jadwal dibuat.") }
                loadDay(_uiState.value.selectedDate)
                onDone()
            }.onFailure { onError(it.userMessage) }
        }
    }

    fun updateStatus(id: String, status: ScheduleStatus) {
        viewModelScope.launch {
            scheduleRepository.updateStatus(id, status)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Status jadwal diperbarui.") }
                    loadDay(_uiState.value.selectedDate)
                }
                .onFailure { error -> notifyActionError(error.userMessage) }
        }
    }

    fun updateNotes(entry: ScheduleEntry, notes: String) {
        viewModelScope.launch {
            scheduleRepository.update(entry.copy(notes = notes.trim().takeIf { it.isNotEmpty() }))
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Catatan disimpan.") }
                    loadDay(_uiState.value.selectedDate)
                }
                .onFailure { error -> notifyActionError(error.userMessage) }
        }
    }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }

    private fun notifyActionError(message: String) = _uiState.update { it.copy(actionError = message) }

    private fun loadShifts() {
        viewModelScope.launch {
            organizationRepository.listShifts(activeOnly = true).onSuccess { shifts ->
                _uiState.update { it.copy(shifts = shifts) }
            }
        }
    }

    private fun loadDay(date: LocalDate) {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val zone: ZoneId = TimeProvider.DEFAULT_ZONE
            val from: Instant = date.atStartOfDay(zone).toInstant()
            val to: Instant = date.plusDays(1).atStartOfDay(zone).toInstant()
            scheduleRepository.list(from, to).onSuccess { entries ->
                _uiState.update { it.copy(loading = false, entries = entries) }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.userMessage) }
            }
        }
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale("id", "ID"))
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun scheduleStatusLabel(status: ScheduleStatus): String = when (status) {
    ScheduleStatus.SCHEDULED -> "Terjadwal"
    ScheduleStatus.IN_PROGRESS -> "Berlangsung"
    ScheduleStatus.COMPLETED -> "Selesai"
    ScheduleStatus.CANCELLED -> "Dibatalkan"
    ScheduleStatus.NEEDS_REPEAT -> "Perlu Ulang"
}

fun scheduleStatusSeverity(status: ScheduleStatus): Int = when (status) {
    ScheduleStatus.SCHEDULED -> 1
    ScheduleStatus.IN_PROGRESS -> 2
    ScheduleStatus.COMPLETED -> 0
    ScheduleStatus.CANCELLED -> -1
    ScheduleStatus.NEEDS_REPEAT -> 3
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesRoute(
    onOpenWorker: (String) -> Unit,
    onManageShifts: () -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    // Schedule entry + target status awaiting confirmation for destructive transitions.
    var pendingStatusChange by remember { mutableStateOf<Pair<ScheduleEntry, ScheduleStatus>?>(null) }
    var editingNotesEntry by remember { mutableStateOf<ScheduleEntry?>(null) }

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
                title = { Text("Jadwal Pemeriksaan") },
                actions = {
                    TextButton(onClick = onManageShifts) { Text("Kelola Shift") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Buat jadwal")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.shiftDay(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Hari sebelumnya")
                }
                Text(
                    text = dayFormatter.format(uiState.selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { viewModel.shiftDay(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Hari berikutnya")
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Pilih")
                }
            }

            when {
                uiState.loading -> LoadingState()
                uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::refresh)
                uiState.entries.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "Belum ada jadwal",
                    description = "Tidak ada jadwal pemeriksaan pada tanggal ini. Tekan tombol + untuk membuat jadwal.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        ScheduleRowCard(
                            entry = entry,
                            onOpenWorker = onOpenWorker,
                            onStatusSelected = { status ->
                                if (status == ScheduleStatus.COMPLETED || status == ScheduleStatus.CANCELLED) {
                                    pendingStatusChange = entry to status
                                } else {
                                    viewModel.updateStatus(entry.id, status)
                                }
                            },
                            onEditNotes = { editingNotesEntry = entry },
                        )
                    }
                }
            }
        }
    }

    // Confirmation + dialog overlays.
    pendingStatusChange?.let { (entry, status) ->
        ConfirmDialog(
            title = if (status == ScheduleStatus.CANCELLED) "Batalkan jadwal?" else "Tandai jadwal selesai?",
            message = if (status == ScheduleStatus.CANCELLED) {
                "Jadwal untuk ${entry.workerName ?: "pekerja"} akan dibatalkan. Tindakan ini tercatat di sistem."
            } else {
                "Jadwal untuk ${entry.workerName ?: "pekerja"} akan ditandai selesai."
            },
            confirmLabel = if (status == ScheduleStatus.CANCELLED) "Batalkan Jadwal" else "Ya, Selesai",
            destructive = status == ScheduleStatus.CANCELLED,
            onConfirm = {
                viewModel.updateStatus(entry.id, status)
                pendingStatusChange = null
            },
            onDismiss = { pendingStatusChange = null },
        )
    }

    editingNotesEntry?.let { entry ->
        EditNotesDialog(
            entry = entry,
            onDismiss = { editingNotesEntry = null },
            onSave = { notes ->
                viewModel.updateNotes(entry, notes)
                editingNotesEntry = null
            },
        )
    }

    if (showCreateDialog) {
        CreateScheduleDialog(
            uiState = uiState,
            onDismiss = {
                showCreateDialog = false
                viewModel.searchWorkers("")
            },
            onSearchWorkers = viewModel::searchWorkers,
            onConfirm = { worker, shift, timeText, notes, onDone, onError ->
                viewModel.createSchedule(worker, shift, uiState.selectedDate, timeText, notes, onDone, onError)
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate.atStartOfDay(TimeProvider.DEFAULT_ZONE).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDate(Instant.ofEpochMilli(millis).atZone(TimeProvider.DEFAULT_ZONE).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ScheduleRowCard(
    entry: ScheduleEntry,
    onOpenWorker: (String) -> Unit,
    onStatusSelected: (ScheduleStatus) -> Unit,
    onEditNotes: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenWorker(entry.workerId) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeFormatter.format(TimeProvider.toOperational(entry.scheduledAt)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(56.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.workerName ?: "Pekerja",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        append(entry.shiftName ?: "Tanpa shift")
                        entry.examinerName?.let { append(" · Pemeriksa: $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!entry.notes.isNullOrBlank()) {
                    Text(
                        text = "Catatan: ${entry.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            SeverityBadge(
                label = scheduleStatusLabel(entry.status),
                severity = scheduleStatusSeverity(entry.status),
            )
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Aksi jadwal")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.status == ScheduleStatus.SCHEDULED) {
                    DropdownMenuItem(
                        text = { Text("Tandai Berlangsung") },
                        onClick = { menuOpen = false; onStatusSelected(ScheduleStatus.IN_PROGRESS) },
                    )
                }
                if (entry.status == ScheduleStatus.SCHEDULED ||
                    entry.status == ScheduleStatus.IN_PROGRESS ||
                    entry.status == ScheduleStatus.NEEDS_REPEAT
                ) {
                    DropdownMenuItem(
                        text = { Text("Tandai Selesai") },
                        onClick = { menuOpen = false; onStatusSelected(ScheduleStatus.COMPLETED) },
                    )
                }
                if (entry.status == ScheduleStatus.IN_PROGRESS || entry.status == ScheduleStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text("Perlu Ulang") },
                        onClick = { menuOpen = false; onStatusSelected(ScheduleStatus.NEEDS_REPEAT) },
                    )
                }
                if (entry.status != ScheduleStatus.CANCELLED && entry.status != ScheduleStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text("Batalkan") },
                        onClick = { menuOpen = false; onStatusSelected(ScheduleStatus.CANCELLED) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Ubah Catatan") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEditNotes() },
                )
            }
        }
    }
}

@Composable
private fun CreateScheduleDialog(
    uiState: SchedulesUiState,
    onDismiss: () -> Unit,
    onSearchWorkers: (String) -> Unit,
    onConfirm: (Worker, Shift?, String, String?, () -> Unit, (String) -> Unit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedWorker by remember { mutableStateOf<Worker?>(null) }
    var selectedShift by remember { mutableStateOf<Shift?>(null) }
    var shiftMenuOpen by remember { mutableStateOf(false) }
    var timeText by remember { mutableStateOf("08:00") }
    var notes by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Jadwal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tanggal: ${dayFormatter.format(uiState.selectedDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedWorker == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onSearchWorkers(it)
                        },
                        label = { Text("Cari pekerja") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.workerResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                        ) {
                            uiState.workerResults.forEach { worker ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedWorker = worker
                                            query = worker.fullName
                                            formError = null
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(worker.fullName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            worker.employeeNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val worker = selectedWorker!!
                    Text(
                        text = "Pekerja: ${worker.fullName} (${worker.employeeNumber})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        selectedWorker = null
                        query = ""
                    }) { Text("Ganti pekerja") }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { shiftMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selectedShift?.name ?: "Pilih Shift (opsional)",
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                    DropdownMenu(expanded = shiftMenuOpen, onDismissRequest = { shiftMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Tanpa shift") },
                            onClick = {
                                selectedShift = null
                                shiftMenuOpen = false
                            },
                        )
                        uiState.shifts.forEach { shift ->
                            DropdownMenuItem(
                                text = { Text(shift.name) },
                                onClick = {
                                    selectedShift = shift
                                    shiftMenuOpen = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Jam (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (formError != null) {
                    Text(
                        text = formError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val worker = selectedWorker
                if (worker == null) {
                    formError = "Pilih pekerja terlebih dahulu."
                    return@TextButton
                }
                onConfirm(
                    worker,
                    selectedShift,
                    timeText,
                    notes,
                    onDismiss,
                    { formError = it },
                )
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun EditNotesDialog(
    entry: ScheduleEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var notes by remember { mutableStateOf(entry.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Catatan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Jadwal ${entry.workerName ?: "-"} · ${timeFormatter.format(TimeProvider.toOperational(entry.scheduledAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(notes) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
