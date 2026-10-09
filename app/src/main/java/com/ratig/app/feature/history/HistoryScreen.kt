package com.ratig.app.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.SeverityBadge
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun HistoryRoute(onOpenSession: (String) -> Unit) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onOpenSession = onOpenSession,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onDaysChange = viewModel::setDays,
        onDepartmentChange = viewModel::setDepartment,
        onShiftChange = viewModel::setShift,
        onStatusChange = { value -> viewModel.setStatus(value?.let { SessionStatus.valueOf(it) }) },
        onClassificationChange = viewModel::setClassification,
        onQueryChange = viewModel::onQueryChange,
        onApplyQuery = viewModel::applyQuery,
        onClearQuery = viewModel::clearQuery,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryViewModel.HistoryUiState,
    onOpenSession: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDaysChange: (Int?) -> Unit,
    onDepartmentChange: (String?) -> Unit,
    onShiftChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onClassificationChange: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onApplyQuery: () -> Unit,
    onClearQuery: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Riwayat Pemeriksaan",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filters") {
                FiltersSection(
                    state = state,
                    onDaysChange = onDaysChange,
                    onDepartmentChange = onDepartmentChange,
                    onShiftChange = onShiftChange,
                    onStatusChange = onStatusChange,
                    onClassificationChange = onClassificationChange,
                    onQueryChange = onQueryChange,
                    onApplyQuery = onApplyQuery,
                    onClearQuery = onClearQuery,
                )
            }
            when {
                state.loading -> item(key = "status") { LoadingState() }
                state.error != null -> item(key = "status") { ErrorState(message = state.error, onRetry = onRetry) }
                state.items.isEmpty() -> item(key = "status") {
                    EmptyState(
                        modifier = Modifier.fillParentMaxSize(),
                        icon = Icons.Outlined.History,
                        title = "Belum ada riwayat",
                        description = "Tidak ada sesi pemeriksaan yang cocok dengan filter aktif.",
                    )
                }
                else -> {
                    itemsIndexed(state.items, key = { _, session -> session.id }) { _, session ->
                        SessionRow(session = session, onOpenSession = onOpenSession)
                    }
                    if (state.loadingMore) {
                        item(key = "more") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (state.canLoadMore) {
                        item(key = "more") {
                            TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Muat lebih banyak") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Filters

@Composable
private fun FiltersSection(
    state: HistoryViewModel.HistoryUiState,
    onDaysChange: (Int?) -> Unit,
    onDepartmentChange: (String?) -> Unit,
    onShiftChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onClassificationChange: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onApplyQuery: () -> Unit,
    onClearQuery: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayChip(
                    label = "Semua",
                    days = null,
                    selected = state.filters.days == null,
                    onSelect = onDaysChange,
                    modifier = Modifier.weight(1f),
                )
                DayChip("7 Hari", 7, state.filters.days == 7, onDaysChange, Modifier.weight(1f))
                DayChip("30 Hari", 30, state.filters.days == 30, onDaysChange, Modifier.weight(1f))
                DayChip("90 Hari", 90, state.filters.days == 90, onDaysChange, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledDropdown(
                    label = "Departemen",
                    options = listOf(FilterOption(null, "Semua")) +
                        state.departments.map { FilterOption(it.id, it.name) },
                    selectedValue = state.filters.departmentId,
                    modifier = Modifier.weight(1f),
                    onSelect = onDepartmentChange,
                )
                LabeledDropdown(
                    label = "Shift",
                    options = listOf(FilterOption(null, "Semua")) +
                        state.shifts.map { FilterOption(it.id, it.name) },
                    selectedValue = state.filters.shiftId,
                    modifier = Modifier.weight(1f),
                    onSelect = onShiftChange,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledDropdown(
                    label = "Status Sesi",
                    options = listOf(FilterOption(null, "Semua")) +
                        SessionStatus.entries.map { FilterOption(it.name, statusLabel(it)) },
                    selectedValue = state.filters.status?.name,
                    modifier = Modifier.weight(1f),
                    onSelect = onStatusChange,
                )
                LabeledDropdown(
                    label = "Kategori",
                    options = listOf(FilterOption(null, "Semua")) +
                        classificationOptions.map { FilterOption(it.first, it.second) },
                    selectedValue = state.filters.classificationCode,
                    modifier = Modifier.weight(1f),
                    onSelect = onClassificationChange,
                )
            }
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari nama pekerja...") },
                singleLine = true,
                trailingIcon = {
                    if (state.filters.query.isNotBlank()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(Icons.Rounded.Close, contentDescription = "Bersihkan pencarian")
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onApplyQuery() }),
            )
            Text(
                text = "Pencarian diterapkan saat menekan tombol cari pada keyboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    days: Int?,
    selected: Boolean,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(days) },
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
    )
}

private data class FilterOption(val value: String?, val label: String)

@Composable
private fun LabeledDropdown(
    label: String,
    options: List<FilterOption>,
    selectedValue: String?,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == selectedValue } ?: options.first()
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSelect(option.value)
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Rows

@Composable
private fun SessionRow(session: TestSession, onOpenSession: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSession(session.id) }
                .padding(12.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = buildString {
                        append(session.workerName ?: "Pekerja")
                        session.workerNumber?.let { append(" (#$it)") }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        session.protocolName ?: "Protokol",
                        session.protocolVersion?.let { "v$it" },
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = TimeProvider.formatForDisplay(
                        session.completedAt ?: session.startedAt ?: session.createdAt,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SeverityBadge(
                label = statusLabel(session.sessionStatus),
                severity = statusSeverity(session.sessionStatus),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

private val classificationOptions: List<Pair<String, String>> = listOf(
    "BAND_1" to "Band 1",
    "BAND_2" to "Band 2",
    "BAND_3" to "Band 3",
    "BAND_4" to "Band 4",
    "INSUFFICIENT_DATA" to "Data Tidak Cukup",
    "NOT_CONFIGURED" to "Aturan Belum Dikonfigurasi",
)

// ---------------------------------------------------------------- ViewModel

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val testSessionRepository: TestSessionRepository,
    private val organizationRepository: OrganizationRepository,
) : ViewModel() {

    data class HistoryFilters(
        val days: Int? = null,
        val departmentId: String? = null,
        val shiftId: String? = null,
        val status: SessionStatus? = null,
        val classificationCode: String? = null,
        /** Search box draft (not yet applied). */
        val query: String = "",
        /** Applied worker name search (server-side ilike). */
        val appliedQuery: String = "",
    )

    data class HistoryUiState(
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val items: List<TestSession> = emptyList(),
        val canLoadMore: Boolean = false,
        val filters: HistoryFilters = HistoryFilters(),
        val departments: List<Department> = emptyList(),
        val shifts: List<Shift> = emptyList(),
    )

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load(reset = true)
        viewModelScope.launch {
            val departments = organizationRepository.listDepartments(activeOnly = false)
            val shifts = organizationRepository.listShifts(activeOnly = false)
            _uiState.update {
                it.copy(
                    departments = (departments as? AppResult.Success)?.value ?: emptyList(),
                    shifts = (shifts as? AppResult.Success)?.value ?: emptyList(),
                )
            }
        }
    }

    fun setDays(days: Int?) = updateFilters { it.copy(days = days) }

    fun setDepartment(departmentId: String?) = updateFilters { it.copy(departmentId = departmentId) }

    fun setShift(shiftId: String?) = updateFilters { it.copy(shiftId = shiftId) }

    fun setStatus(status: SessionStatus?) = updateFilters { it.copy(status = status) }

    fun setClassification(code: String?) = updateFilters { it.copy(classificationCode = code) }

    fun onQueryChange(query: String) =
        _uiState.update { it.copy(filters = it.filters.copy(query = query)) }

    fun applyQuery() {
        _uiState.update { it.copy(filters = it.filters.copy(appliedQuery = it.filters.query.trim())) }
        load(reset = true)
    }

    fun clearQuery() {
        _uiState.update { it.copy(filters = it.filters.copy(query = "", appliedQuery = "")) }
        load(reset = true)
    }

    fun loadMore() {
        if (!_uiState.value.loadingMore && _uiState.value.canLoadMore) load(reset = false)
    }

    fun retry() = load(reset = true)

    private fun updateFilters(transform: (HistoryFilters) -> HistoryFilters) {
        _uiState.update { it.copy(filters = transform(it.filters)) }
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        val previous = _uiState.value
        if (reset) {
            _uiState.update { it.copy(loading = true, error = null, items = emptyList(), canLoadMore = false) }
        } else {
            _uiState.update { it.copy(loadingMore = true) }
        }
        viewModelScope.launch {
            val filters = _uiState.value.filters
            val from = filters.days?.let { days -> TimeProvider.nowUtc().minus(days.toLong(), ChronoUnit.DAYS) }
            val offset = if (reset) 0 else previous.items.size
            when (
                val result = testSessionRepository.history(
                    workerQuery = filters.appliedQuery.takeIf { it.isNotBlank() },
                    departmentId = filters.departmentId,
                    shiftId = filters.shiftId,
                    status = filters.status,
                    classificationCode = filters.classificationCode,
                    from = from,
                    limit = PAGE_SIZE,
                    offset = offset,
                )
            ) {
                is AppResult.Success -> {
                    val page = result.value
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            loadingMore = false,
                            items = if (reset) page else state.items + page,
                            canLoadMore = page.size >= PAGE_SIZE,
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = result.error.userMessage,
                        canLoadMore = false,
                    )
                }
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
