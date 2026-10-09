package com.ratig.app.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.WorkArea
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MasterDataUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val departments: List<Department> = emptyList(),
    val workAreas: List<WorkArea> = emptyList(),
    /** Department filter for the work-areas tab; null = all departments. */
    val areaDepartmentFilter: Department? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class MasterDataViewModel @Inject constructor(
    private val organizationRepository: OrganizationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MasterDataUiState())
    val uiState: StateFlow<MasterDataUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            organizationRepository.listDepartments(activeOnly = false)
                .onSuccess { departments -> _uiState.update { it.copy(departments = departments) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage) } }
            loadWorkAreas()
        }
    }

    fun setAreaDepartmentFilter(department: Department?) {
        _uiState.update { it.copy(areaDepartmentFilter = department) }
        viewModelScope.launch { loadWorkAreas() }
    }

    fun upsertDepartment(department: Department, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            organizationRepository.upsertDepartment(department)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Departemen disimpan.") }
                    refresh()
                    onDone()
                }
                .onFailure { onError(it.userMessage) }
        }
    }

    fun toggleDepartment(department: Department) {
        viewModelScope.launch {
            organizationRepository.upsertDepartment(department.copy(activeStatus = !department.activeStatus))
                .onSuccess {
                    _uiState.update {
                        it.copy(actionMessage = if (department.activeStatus) "Departemen dinonaktifkan." else "Departemen diaktifkan.")
                    }
                    refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun upsertWorkArea(area: WorkArea, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            organizationRepository.upsertWorkArea(area)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Area kerja disimpan.") }
                    refresh()
                    onDone()
                }
                .onFailure { onError(it.userMessage) }
        }
    }

    fun toggleWorkArea(area: WorkArea) {
        viewModelScope.launch {
            organizationRepository.upsertWorkArea(area.copy(activeStatus = !area.activeStatus))
                .onSuccess {
                    _uiState.update {
                        it.copy(actionMessage = if (area.activeStatus) "Area kerja dinonaktifkan." else "Area kerja diaktifkan.")
                    }
                    refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }

    private suspend fun loadWorkAreas() {
        organizationRepository.listWorkAreas(
            departmentId = _uiState.value.areaDepartmentFilter?.id,
            activeOnly = false,
        )
            .onSuccess { areas -> _uiState.update { it.copy(workAreas = areas) } }
            .onFailure { error -> _uiState.update { it.copy(error = error.userMessage) } }
        _uiState.update { it.copy(loading = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDataRoute(
    onBack: () -> Unit,
    viewModel: MasterDataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var editingDepartment by remember { mutableStateOf<Department?>(null) }
    var showDepartmentDialog by remember { mutableStateOf(false) }
    var togglingDepartment by remember { mutableStateOf<Department?>(null) }
    var editingArea by remember { mutableStateOf<WorkArea?>(null) }
    var showAreaDialog by remember { mutableStateOf(false) }
    var togglingArea by remember { mutableStateOf<WorkArea?>(null) }

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
                title = { Text("Data Master") },
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
                if (tab == 0) {
                    editingDepartment = null
                    showDepartmentDialog = true
                } else {
                    editingArea = null
                    showAreaDialog = true
                }
            }) {
                Icon(Icons.Rounded.Add, contentDescription = "Tambah data")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Departemen") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Area Kerja") })
            }
            when {
                uiState.loading -> LoadingState()
                uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::refresh)
                tab == 0 -> DepartmentTab(
                    uiState = uiState,
                    onEdit = { editingDepartment = it; showDepartmentDialog = true },
                    onToggle = { togglingDepartment = it },
                )
                else -> WorkAreaTab(
                    uiState = uiState,
                    onSelectFilter = viewModel::setAreaDepartmentFilter,
                    onEdit = { editingArea = it; showAreaDialog = true },
                    onToggle = { togglingArea = it },
                )
            }
        }
    }

    if (showDepartmentDialog) {
        DepartmentDialog(
            existing = editingDepartment,
            onDismiss = { showDepartmentDialog = false },
            onSave = { department, onError ->
                viewModel.upsertDepartment(
                    department,
                    onDone = { showDepartmentDialog = false },
                    onError = onError,
                )
            },
        )
    }
    togglingDepartment?.let { department ->
        ConfirmDialog(
            title = if (department.activeStatus) "Nonaktifkan departemen?" else "Aktifkan departemen?",
            message = "Departemen \"${department.name}\" akan " +
                if (department.activeStatus) " disembunyikan dari pilihan baru. Data riwayat tetap utuh."
                else " kembali tersedia untuk dipilih.",
            confirmLabel = if (department.activeStatus) "Nonaktifkan" else "Aktifkan",
            destructive = department.activeStatus,
            onConfirm = {
                viewModel.toggleDepartment(department)
                togglingDepartment = null
            },
            onDismiss = { togglingDepartment = null },
        )
    }
    if (showAreaDialog) {
        WorkAreaDialog(
            existing = editingArea,
            departments = uiState.departments,
            onDismiss = { showAreaDialog = false },
            onSave = { area, onError ->
                viewModel.upsertWorkArea(
                    area,
                    onDone = { showAreaDialog = false },
                    onError = onError,
                )
            },
        )
    }
    togglingArea?.let { area ->
        ConfirmDialog(
            title = if (area.activeStatus) "Nonaktifkan area kerja?" else "Aktifkan area kerja?",
            message = "Area kerja \"${area.name}\" akan " +
                if (area.activeStatus) " disembunyikan dari pilihan baru. Data riwayat tetap utuh."
                else " kembali tersedia untuk dipilih.",
            confirmLabel = if (area.activeStatus) "Nonaktifkan" else "Aktifkan",
            destructive = area.activeStatus,
            onConfirm = {
                viewModel.toggleWorkArea(area)
                togglingArea = null
            },
            onDismiss = { togglingArea = null },
        )
    }
}

@Composable
private fun DepartmentTab(
    uiState: MasterDataUiState,
    onEdit: (Department) -> Unit,
    onToggle: (Department) -> Unit,
) {
    if (uiState.departments.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Apartment,
            title = "Belum ada departemen",
            description = "Tambahkan departemen untuk mengelompokkan pekerja dan area kerja.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.departments, key = { it.id }) { department ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = department.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (!department.description.isNullOrBlank()) {
                            Text(
                                text = department.description!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ActiveBadge(active = department.activeStatus)
                    IconButton(onClick = { onEdit(department) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Ubah departemen")
                    }
                    TextButton(onClick = { onToggle(department) }) {
                        Text(if (department.activeStatus) "Nonaktifkan" else "Aktifkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkAreaTab(
    uiState: MasterDataUiState,
    onSelectFilter: (Department?) -> Unit,
    onEdit: (WorkArea) -> Unit,
    onToggle: (WorkArea) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DepartmentFilterDropdown(
            selected = uiState.areaDepartmentFilter,
            departments = uiState.departments,
            onSelect = onSelectFilter,
        )
        if (uiState.workAreas.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Place,
                title = "Belum ada area kerja",
                description = "Tambahkan area kerja pada departemen terpilih.",
            )
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.workAreas, key = { it.id }) { area ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = area.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            val departmentName = uiState.departments
                                .firstOrNull { it.id == area.departmentId }?.name
                            Text(
                                text = departmentName ?: "Departemen tidak dikenal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!area.description.isNullOrBlank()) {
                                Text(
                                    text = area.description!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ActiveBadge(active = area.activeStatus)
                        IconButton(onClick = { onEdit(area) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Ubah area kerja")
                        }
                        TextButton(onClick = { onToggle(area) }) {
                            Text(if (area.activeStatus) "Nonaktifkan" else "Aktifkan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveBadge(active: Boolean) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = if (active) "Aktif" else "Nonaktif",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DepartmentFilterDropdown(
    selected: Department?,
    departments: List<Department>,
    onSelect: (Department?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selected?.name ?: "Semua Departemen",
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Semua Departemen") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            departments.forEach { department ->
                DropdownMenuItem(
                    text = { Text(department.name) },
                    onClick = {
                        onSelect(department)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DepartmentDialog(
    existing: Department?,
    onDismiss: () -> Unit,
    onSave: (Department, (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Tambah Departemen" else "Ubah Departemen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama departemen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Nama departemen wajib diisi."
                    return@TextButton
                }
                onSave(
                    Department(
                        id = existing?.id ?: "",
                        name = name.trim(),
                        description = description.trim().takeIf { it.isNotEmpty() },
                        activeStatus = existing?.activeStatus ?: true,
                    ),
                ) { message -> error = message }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun WorkAreaDialog(
    existing: WorkArea?,
    departments: List<Department>,
    onDismiss: () -> Unit,
    onSave: (WorkArea, (String) -> Unit) -> Unit,
) {
    var selectedDepartment by remember { mutableStateOf(existing?.let { area -> departments.firstOrNull { it.id == area.departmentId } }) }
    var departmentMenuOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Tambah Area Kerja" else "Ubah Area Kerja") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { departmentMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = selectedDepartment?.name ?: "Pilih Departemen",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(expanded = departmentMenuOpen, onDismissRequest = { departmentMenuOpen = false }) {
                        departments.forEach { department ->
                            DropdownMenuItem(
                                text = { Text(department.name) },
                                onClick = {
                                    selectedDepartment = department
                                    departmentMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama area kerja") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val department = selectedDepartment
                when {
                    department == null -> error = "Pilih departemen terlebih dahulu."
                    name.isBlank() -> error = "Nama area kerja wajib diisi."
                    else -> onSave(
                        WorkArea(
                            id = existing?.id ?: "",
                            departmentId = department.id,
                            name = name.trim(),
                            description = description.trim().takeIf { it.isNotEmpty() },
                            activeStatus = existing?.activeStatus ?: true,
                        ),
                    ) { message -> error = message }
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
