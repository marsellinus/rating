package com.ratig.app.feature.followup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.FollowUp
import com.ratig.app.domain.model.FollowUpStatus
import com.ratig.app.domain.repository.FollowUpRepository
import com.ratig.app.ui.components.EmptyState
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import com.ratig.app.ui.components.SeverityBadge
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** List filter chips. OPEN is enforced server-side; COMPLETED filters the loaded page client-side. */
enum class FollowUpFilter { ALL, OPEN, COMPLETED }

@Composable
fun FollowUpsRoute(onOpenSession: (String) -> Unit) {
    val viewModel: FollowUpsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowUpsScreen(
        state = state,
        onOpenSession = onOpenSession,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onFilterChange = viewModel::setFilter,
    )
}

@Composable
private fun FollowUpsScreen(
    state: FollowUpsViewModel.FollowUpsUiState,
    onOpenSession: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onFilterChange: (FollowUpFilter) -> Unit,
) {
    val visible = if (state.filter == FollowUpFilter.COMPLETED) {
        state.items.filter { it.status == FollowUpStatus.COMPLETED }
    } else {
        state.items
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Tindak Lanjut",
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.filter == FollowUpFilter.ALL,
                        onClick = { onFilterChange(FollowUpFilter.ALL) },
                        label = { Text("Semua") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.filter == FollowUpFilter.OPEN,
                        onClick = { onFilterChange(FollowUpFilter.OPEN) },
                        label = { Text("Terbuka") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.filter == FollowUpFilter.COMPLETED,
                        onClick = { onFilterChange(FollowUpFilter.COMPLETED) },
                        label = { Text("Selesai") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when {
                state.loading -> item(key = "status") { LoadingState() }
                state.error != null -> item(key = "status") {
                    ErrorState(message = state.error, onRetry = onRetry)
                }
                visible.isEmpty() -> item(key = "status") {
                    EmptyState(
                        modifier = Modifier.fillParentMaxSize(),
                        icon = Icons.Outlined.Assignment,
                        title = "Belum ada tindak lanjut",
                        description = "Tidak ada tindak lanjut yang cocok dengan filter ini.",
                    )
                }
                else -> {
                    itemsIndexed(visible, key = { _, followUp -> followUp.id }) { _, followUp ->
                        FollowUpRowCard(followUp = followUp, onOpenSession = onOpenSession)
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
                            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                                Text("Muat lebih banyak")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUpRowCard(followUp: FollowUp, onOpenSession: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSession(followUp.sessionId) }
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
                    text = followUp.workerName ?: "Pekerja",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = FollowUpLabels.actionType(followUp.actionType),
                    style = MaterialTheme.typography.bodyMedium,
                )
                followUp.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = followUp.dueAt
                        ?.let { "Tenggat: ${TimeProvider.formatDateForDisplay(it)}" }
                        ?: "Tanpa tenggat",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SeverityBadge(
                label = FollowUpLabels.statusLabel(followUp.status),
                severity = FollowUpLabels.statusSeverity(followUp.status),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- ViewModel

@HiltViewModel
class FollowUpsViewModel @Inject constructor(
    private val followUpRepository: FollowUpRepository,
) : ViewModel() {

    data class FollowUpsUiState(
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val items: List<FollowUp> = emptyList(),
        val canLoadMore: Boolean = false,
        val filter: FollowUpFilter = FollowUpFilter.ALL,
    )

    private val _uiState = MutableStateFlow(FollowUpsUiState())
    val uiState: StateFlow<FollowUpsUiState> = _uiState.asStateFlow()

    init {
        load(reset = true)
    }

    fun setFilter(filter: FollowUpFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        load(reset = true)
    }

    fun loadMore() {
        if (!_uiState.value.loadingMore && _uiState.value.canLoadMore) load(reset = false)
    }

    fun retry() = load(reset = true)

    private fun load(reset: Boolean) {
        val previous = _uiState.value
        if (reset) {
            _uiState.update {
                it.copy(loading = true, error = null, items = emptyList(), canLoadMore = false)
            }
        } else {
            _uiState.update { it.copy(loadingMore = true) }
        }
        viewModelScope.launch {
            val offset = if (reset) 0 else previous.items.size
            when (
                val result = followUpRepository.list(
                    onlyOpen = _uiState.value.filter == FollowUpFilter.OPEN,
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
