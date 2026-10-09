package com.ratig.app.feature.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.LoadingState

/**
 * Create/edit a worker. Reads the optional `workerId` nav argument via the
 * view model's [androidx.lifecycle.SavedStateHandle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerEditRoute(onDone: () -> Unit) {
    val viewModel: WorkerEditViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showReactivateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Tambah Pekerja" else "Ubah Pekerja") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            OutlinedTextField(
                value = state.employeeNumber,
                onValueChange = viewModel::onEmployeeNumberChange,
                label = { Text("Nomor Induk Pekerja") },
                isError = state.employeeNumberError != null,
                supportingText = state.employeeNumberError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = { Text("Nama Lengkap") },
                isError = state.fullNameError != null,
                supportingText = state.fullNameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email (opsional)") },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = "Departemen",
                options = state.departments.map { department ->
                    DropdownOption(value = department.id, label = department.name)
                },
                selectedValue = state.departmentId,
                onSelected = viewModel::onDepartmentSelected,
                modifier = Modifier.fillMaxWidth(),
                emptyHint = "Belum ada departemen aktif",
            )

            DropdownField(
                label = "Area Kerja",
                options = state.workAreas.map { area ->
                    DropdownOption(value = area.id, label = area.name)
                },
                selectedValue = state.workAreaId,
                onSelected = viewModel::onWorkAreaSelected,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.departmentId != null,
                supportingText = if (state.departmentId == null) "Pilih departemen terlebih dahulu" else null,
                emptyHint = if (state.workAreasLoading) "Memuat area kerja..." else "Belum ada area kerja",
            )

            DropdownField(
                label = "Shift",
                options = listOf(DropdownOption(value = null, label = "Tanpa shift")) +
                    state.shifts.map { shift ->
                        DropdownOption(value = shift.id, label = shift.name)
                    },
                selectedValue = state.shiftId,
                onSelected = viewModel::onShiftSelected,
                modifier = Modifier.fillMaxWidth(),
                emptyHint = "Belum ada shift aktif",
            )

            OutlinedTextField(
                value = state.jobTitle,
                onValueChange = viewModel::onJobTitleChange,
                label = { Text("Jabatan (opsional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Simpan")
                }
            }

            if (!state.isNew) {
                if (state.activeStatus) {
                    OutlinedButton(
                        onClick = { showDeactivateDialog = true },
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text(
                            "Nonaktifkan Pekerja",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { showReactivateDialog = true },
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("Aktifkan Kembali")
                    }
                }
            }
        }
    }

    if (showDeactivateDialog) {
        ConfirmDialog(
            title = "Nonaktifkan pekerja?",
            message = "Pekerja disembunyikan dari daftar aktif. Riwayat pemeriksaan tetap tersimpan.",
            confirmLabel = "Nonaktifkan",
            destructive = true,
            onConfirm = {
                showDeactivateDialog = false
                viewModel.deactivate()
            },
            onDismiss = { showDeactivateDialog = false },
        )
    }

    if (showReactivateDialog) {
        ConfirmDialog(
            title = "Aktifkan kembali pekerja?",
            message = "Pekerja akan muncul kembali di daftar pekerja aktif.",
            confirmLabel = "Aktifkan",
            onConfirm = {
                showReactivateDialog = false
                viewModel.reactivate()
            },
            onDismiss = { showReactivateDialog = false },
        )
    }
}
