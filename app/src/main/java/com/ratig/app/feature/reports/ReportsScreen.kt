package com.ratig.app.feature.reports

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.ratig.app.core.export.CsvBuilder
import com.ratig.app.core.export.PdfBuilder
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.reports.ReportFilter
import com.ratig.app.data.reports.ReportRepository
import com.ratig.app.data.reports.ReportRow
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val presetDays: Int? = 30,
    val fromDate: LocalDate = LocalDate.now(TimeProvider.DEFAULT_ZONE).minusDays(29),
    val toDate: LocalDate = LocalDate.now(TimeProvider.DEFAULT_ZONE),
    val departments: List<Department> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val selectedDepartment: Department? = null,
    val selectedShift: Shift? = null,
    val rows: List<ReportRow> = emptyList(),
    val previewLoaded: Boolean = false,
    val exporting: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val organizationRepository: OrganizationRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadFilters()
    }

    fun applyPreset(days: Int) {
        val today = LocalDate.now(TimeProvider.DEFAULT_ZONE)
        _uiState.update {
            it.copy(
                presetDays = days,
                fromDate = today.minusDays((days - 1).toLong()),
                toDate = today,
                previewLoaded = false,
            )
        }
    }

    /** Sets an explicit range (also selected when the user moves the custom pickers). */
    fun setCustomRange(from: LocalDate, to: LocalDate) {
        _uiState.update { it.copy(presetDays = null, fromDate = from, toDate = to, previewLoaded = false) }
    }

    fun selectDepartment(department: Department?) {
        _uiState.update { it.copy(selectedDepartment = department, previewLoaded = false) }
    }

    fun selectShift(shift: Shift?) {
        _uiState.update { it.copy(selectedShift = shift, previewLoaded = false) }
    }

    /** Loads up to 2000 report rows for the chosen period + filters. */
    fun preview() {
        val state = _uiState.value
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val filter = ReportFilter(
                from = state.fromDate.atStartOfDay(TimeProvider.DEFAULT_ZONE).toInstant(),
                to = state.toDate.plusDays(1).atStartOfDay(TimeProvider.DEFAULT_ZONE).toInstant(),
                departmentId = state.selectedDepartment?.id,
                shiftId = state.selectedShift?.id,
            )
            reportRepository.rows(filter)
                .onSuccess { rows ->
                    _uiState.update { it.copy(loading = false, rows = rows, previewLoaded = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.userMessage) }
                }
        }
    }

    fun exportCsv() {
        export(
            mimeType = "text/csv",
            shareTitle = "Bagikan laporan CSV",
            fileNameBase = "laporan-reaksi-waktu",
            extension = "csv",
        ) { rows ->
            val csv = CsvBuilder()
            csv.header(
                "Tanggal Selesai", "Nama Pekerja", "No. Induk", "Departemen", "Area Kerja", "Shift",
                "Pemeriksa", "Valid", "Mean (ms)", "Median (ms)", "Min (ms)", "Max (ms)", "SD (ms)",
                "Slow Count", "Kategori (Kode)", "Kategori (Label)", "Versi Aturan", "Tindak Lanjut",
            )
            rows.forEach { row ->
                csv.row(
                    listOf(
                        formatDate(row.sessionCompletedAt),
                        row.workerName,
                        row.workerNumber,
                        row.departmentName.orEmpty(),
                        row.workAreaName.orEmpty(),
                        row.shiftName.orEmpty(),
                        row.examinerName.orEmpty(),
                        row.validTrialCount,
                        formatNumber(row.meanReactionTimeMs),
                        formatNumber(row.medianReactionTimeMs),
                        row.minReactionTimeMs?.toString().orEmpty(),
                        row.maxReactionTimeMs?.toString().orEmpty(),
                        formatNumber(row.standardDeviationMs),
                        row.slowResponseCount,
                        row.classificationCode.orEmpty(),
                        row.classificationLabel.orEmpty(),
                        row.ruleVersion.orEmpty(),
                        row.followUpStatus.orEmpty(),
                    ),
                )
            }
            csv.build().toByteArray(Charsets.UTF_8)
        }
    }

    fun exportPdf() {
        export(
            mimeType = "application/pdf",
            shareTitle = "Bagikan laporan PDF",
            fileNameBase = "laporan-reaksi-waktu",
            extension = "pdf",
        ) { rows ->
            val state = _uiState.value
            val pdf = PdfBuilder()
            pdf.addTitle("Laporan Pemeriksaan Waktu Reaksi")
            pdf.addMetaLine("Periode: ${formatDate(state.fromDate)} s.d. ${formatDate(state.toDate)}")
            pdf.addMetaLine(
                "Departemen: ${state.selectedDepartment?.name ?: "Semua"} · Shift: ${state.selectedShift?.name ?: "Semua"}",
            )
            pdf.addMetaLine("Jumlah baris: ${rows.size}")
            pdf.addMetaLine(
                "Dibuat: ${dateTimeFormatter.format(Instant.now())} · RATIG",
            )
            pdf.addSpacer(10f)
            pdf.addTable(
                headers = listOf("Tanggal", "Pekerja", "No. Induk", "Departemen", "Shift", "Mean (ms)", "Median (ms)", "Kategori"),
                rows = rows.map { row ->
                    listOf(
                        formatDate(row.sessionCompletedAt),
                        row.workerName,
                        row.workerNumber,
                        row.departmentName.orEmpty(),
                        row.shiftName.orEmpty(),
                        formatNumber(row.meanReactionTimeMs),
                        formatNumber(row.medianReactionTimeMs),
                        row.classificationLabel ?: row.classificationCode.orEmpty(),
                    )
                },
                columnWidthsPt = listOf(56f, 104f, 56f, 78f, 52f, 48f, 52f, 60f),
            )
            pdf.build()
        }
    }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }

    private fun export(
        mimeType: String,
        shareTitle: String,
        fileNameBase: String,
        extension: String,
        buildBytes: (List<ReportRow>) -> ByteArray,
    ) {
        val rows = _uiState.value.rows
        if (rows.isEmpty()) {
            _uiState.update { it.copy(actionError = "Tidak ada data untuk diekspor. Jalankan pratinjau terlebih dahulu.") }
            return
        }
        _uiState.update { it.copy(exporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val bytes = buildBytes(rows)
                val file = writeExportFile(fileNameBase, extension, bytes)
                shareFile(file, mimeType, shareTitle)
                file.name
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { name ->
                        _uiState.update { it.copy(exporting = false, actionMessage = "Berkas dibuat: $name (dialog berbagi dibuka)") }
                    },
                    onFailure = {
                        _uiState.update { it.copy(exporting = false, actionError = "Gagal membuat berkas ekspor.") }
                    },
                )
            }
        }
    }

    /**
     * Writes to app-external storage `getExternalFilesDir(null)/reports/`
     * (fallback `filesDir/reports/`), matching res/xml/file_paths.xml.
     */
    private fun writeExportFile(baseName: String, extension: String, bytes: ByteArray): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "reports")
        if (!dir.exists()) dir.mkdirs()
        val stamp = fileStampFormatter.format(Instant.now())
        val file = File(dir, "$baseName-$stamp.$extension")
        file.writeBytes(bytes)
        return file
    }

    private fun shareFile(file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, FILE_AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    private fun loadFilters() {
        viewModelScope.launch {
            organizationRepository.listDepartments(activeOnly = false).onSuccess { departments ->
                _uiState.update { it.copy(departments = departments) }
            }
            organizationRepository.listShifts(activeOnly = false).onSuccess { shifts ->
                _uiState.update { it.copy(shifts = shifts) }
            }
        }
    }

    companion object {
        /** FileProvider authority registered in AndroidManifest.xml. */
        const val FILE_AUTHORITY = "com.ratig.app.fileprovider"
    }
}

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale("id", "ID"))
    .withZone(TimeProvider.DEFAULT_ZONE)
private val fileStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(TimeProvider.DEFAULT_ZONE)

private fun formatDate(date: LocalDate?): String = date?.let(displayDateFormatter::format) ?: "-"
private fun formatDate(instant: Instant?): String =
    instant?.let { displayDateFormatter.withZone(TimeProvider.DEFAULT_ZONE).format(it) } ?: "-"

private fun formatNumber(value: Double?): String =
    value?.let { String.format(Locale("id", "ID"), "%.0f", it) } ?: "-"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsRoute(viewModel: ReportsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

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
        topBar = { TopAppBar(title = { Text("Laporan") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Periode", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.presetDays == 7,
                    onClick = { viewModel.applyPreset(7) },
                    label = { Text("7 Hari") },
                )
                FilterChip(
                    selected = uiState.presetDays == 30,
                    onClick = { viewModel.applyPreset(30) },
                    label = { Text("30 Hari") },
                )
                FilterChip(
                    selected = uiState.presetDays == 90,
                    onClick = { viewModel.applyPreset(90) },
                    label = { Text("90 Hari") },
                )
                FilterChip(
                    selected = uiState.presetDays == null,
                    onClick = { viewModel.setCustomRange(uiState.fromDate, uiState.toDate) },
                    label = { Text("Kustom") },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showFromPicker = true }) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Dari: ${formatDate(uiState.fromDate)}")
                }
                OutlinedButton(onClick = { showToPicker = true }) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("s.d. ${formatDate(uiState.toDate)}")
                }
            }

            DropdownSelector(
                label = uiState.selectedDepartment?.name ?: "Semua Departemen",
                items = listOf<Department?>(null) + uiState.departments,
                itemLabel = { it?.name ?: "Semua Departemen" },
                onSelected = viewModel::selectDepartment,
            )
            DropdownSelector(
                label = uiState.selectedShift?.name ?: "Semua Shift",
                items = listOf<Shift?>(null) + uiState.shifts,
                itemLabel = { it?.name ?: "Semua Shift" },
                onSelected = viewModel::selectShift,
            )

            Button(onClick = viewModel::preview, modifier = Modifier.fillMaxWidth()) {
                Text("Pratinjau")
            }

            when {
                uiState.loading -> LoadingState()
                uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::preview)
                uiState.previewLoaded && uiState.rows.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Description,
                    title = "Tidak ada data",
                    description = "Tidak ada hasil pemeriksaan untuk filter yang dipilih.",
                )
                uiState.previewLoaded -> ReportPreviewSection(
                    uiState = uiState,
                    onExportCsv = viewModel::exportCsv,
                    onExportPdf = viewModel::exportPdf,
                )
            }

            Text(
                text = "Berkas ekspor disimpan di folder reports pada penyimpanan khusus aplikasi " +
                    "(Android/data/com.ratig.app/files/reports) dan langsung dibuka melalui dialog berbagi.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showFromPicker) {
        ReportDatePickerDialog(
            initial = uiState.fromDate,
            onPicked = { picked ->
                viewModel.setCustomRange(picked, uiState.toDate)
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        ReportDatePickerDialog(
            initial = uiState.toDate,
            onPicked = { picked ->
                viewModel.setCustomRange(uiState.fromDate, picked)
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }
}

@Composable
private fun ReportDatePickerDialog(
    initial: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(TimeProvider.DEFAULT_ZONE).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(TimeProvider.DEFAULT_ZONE).toLocalDate())
                }
            }) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Filter:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(label, modifier = Modifier.width(200.dp), textAlign = TextAlign.Start)
            }
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(itemLabel(item)) },
                        onClick = {
                            onSelected(item)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportPreviewSection(
    uiState: ReportsUiState,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "${uiState.rows.size} baris ditemukan" +
                if (uiState.rows.size >= 2000) " (batas 2000 baris tercapai)" else "",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportCsv, enabled = !uiState.exporting) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Ekspor CSV")
            }
            OutlinedButton(onClick = onExportPdf, enabled = !uiState.exporting) {
                Icon(Icons.Rounded.Description, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Ekspor PDF")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Ringkasan 20 baris pertama", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(4.dp))
                val scroll = rememberScrollState()
                Row(Modifier.horizontalScroll(scroll)) {
                    Column {
                        PreviewRow(
                            cells = listOf("Tanggal", "Pekerja", "No. Induk", "Departemen", "Mean (ms)", "Median (ms)", "Kategori", "Tindak Lanjut"),
                            header = true,
                        )
                        uiState.rows.take(20).forEach { row ->
                            PreviewRow(
                                cells = listOf(
                                    formatDate(row.sessionCompletedAt),
                                    row.workerName,
                                    row.workerNumber,
                                    row.departmentName ?: "-",
                                    formatNumber(row.meanReactionTimeMs),
                                    formatNumber(row.medianReactionTimeMs),
                                    row.classificationLabel ?: row.classificationCode ?: "-",
                                    row.followUpStatus ?: "-",
                                ),
                                header = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(cells: List<String>, header: Boolean) {
    Surface(
        color = if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = 6.dp, horizontal = 8.dp)) {
            cells.forEachIndexed { index, cell ->
                Text(
                    text = cell,
                    style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(if (index == 1) 120.dp else 88.dp),
                )
            }
        }
    }
}
