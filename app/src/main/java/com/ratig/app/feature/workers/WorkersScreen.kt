package com.ratig.app.feature.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratig.app.domain.model.Worker
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState

/** Worker directory with search, org filters and infinite scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkersRoute(
    onOpenWorker: (String) -> Unit,
    onAddWorker: () -> Unit,
) {
    val viewModel: WorkersViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pekerja") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWorker,
                icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                text = { Text("Tambah") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Cari nama atau nomor induk...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FilterChips(
                viewModel = viewModel,
                state = state,
            )

            when {
                state.workers.isEmpty() && state.error != null ->
                    ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::refresh,
                    )

                state.workers.isEmpty() && state.loading -> LoadingState()

                state.workers.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.People,
                    title = "Tidak ada pekerja",
                    description = "Tidak ada pekerja yang cocok dengan pencarian atau filter.",
                    action = {
                        Button(onClick = onAddWorker) {
                            Text("Tambah Pekerja")
                        }
                    },
                )

                else -> WorkerList(
                    state = state,
                    listState = rememberLazyListState(),
                    onOpenWorker = onOpenWorker,
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
}

@Composable
private fun FilterChips(
    viewModel: WorkersViewModel,
    state: WorkersViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.departments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedDepartmentId == null,
                        onClick = { viewModel.selectDepartment(null) },
                        label = { Text("Semua departemen") },
                    )
                }
                items(state.departments, key = { it.id }) { department ->
                    FilterChip(
                        selected = state.selectedDepartmentId == department.id,
                        onClick = {
                            viewModel.selectDepartment(
                                if (state.selectedDepartmentId == department.id) null else department.id,
                            )
                        },
                        label = { Text(department.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        if (state.shifts.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedShiftId == null,
                        onClick = { viewModel.selectShift(null) },
                        label = { Text("Semua shift") },
                    )
                }
                items(state.shifts, key = { it.id }) { shift ->
                    FilterChip(
                        selected = state.selectedShiftId == shift.id,
                        onClick = {
                            viewModel.selectShift(
                                if (state.selectedShiftId == shift.id) null else shift.id,
                            )
                        },
                        label = { Text(shift.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FilterChip(
                selected = state.activeOnly,
                onClick = viewModel::toggleActiveOnly,
                label = { Text("Aktif saja") },
            )
        }
    }
}

@Composable
private fun WorkerList(
    state: WorkersViewModel.UiState,
    listState: LazyListState,
    onOpenWorker: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 4 && info.totalItemsCount > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.workers, key = { it.id }) { worker ->
            WorkerCard(worker = worker, onClick = { onOpenWorker(worker.id) })
        }
        item(key = "footer") {
            when {
                state.loadingMore -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(28.dp))
                }

                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onLoadMore) { Text("Coba lagi") }
                }

                else -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkerCard(
    worker: Worker,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = worker.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!worker.activeStatus) {
                    WorkerStatusBadge(active = false)
                }
            }
            Text(
                text = "NIP ${worker.employeeNumber}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOfNotNull(
                    worker.departmentName ?: "—",
                    worker.shiftName ?: "—",
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            worker.jobTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
