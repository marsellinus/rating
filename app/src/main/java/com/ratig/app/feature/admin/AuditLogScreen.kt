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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.json.JsonCodec
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.AuditLogEntry
import com.ratig.app.domain.repository.AuditRepository
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

/** Audit action filters shown in the dropdown (matches audit_logs.action values). */
enum class AuditActionFilter(val raw: String?, val label: String) {
    ALL(null, "Semua Aksi"),
    LOGIN("login", "Masuk (Login)"),
    APPROVE_ACCOUNT("approve_account", "Persetujuan Akun"),
    SET_ROLE("set_role", "Ubah Role"),
    PROTOCOL_UPDATE("protocol_update", "Perubahan Protokol"),
    RULE_UPDATE("rule_update", "Perubahan Aturan"),
    FINALIZE_SESSION("finalize_session", "Finalisasi Sesi"),
    FOLLOW_UP_UPDATE("follow_up_update", "Pembaruan Tindak Lanjut"),
    SCHEDULE_UPDATE("schedule_update", "Perubahan Jadwal"),
    EXPORT("export", "Ekspor Data"),
}

data class AuditLogUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val filter: AuditActionFilter = AuditActionFilter.ALL,
    val entries: List<AuditLogEntry> = emptyList(),
    val pageSize: Int = 50,
    val hasMore: Boolean = false,
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init {
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun setFilter(filter: AuditActionFilter) {
        _uiState.update { it.copy(filter = filter) }
        load(reset = true)
    }

    fun loadMore() {
        if (_uiState.value.loadingMore || !_uiState.value.hasMore) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    private fun load(reset: Boolean) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                loading = reset,
                loadingMore = !reset,
                error = if (reset) null else it.error,
            )
        }
        viewModelScope.launch {
            val offset = if (reset) 0 else state.entries.size
            auditRepository.list(
                action = state.filter.raw,
                entityType = null,
                limit = state.pageSize,
                offset = offset,
            )
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            entries = if (reset) page else it.entries + page,
                            hasMore = page.size >= it.pageSize,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, loadingMore = false, error = error.userMessage)
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogRoute(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Audit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            AuditFilterDropdown(
                selected = uiState.filter,
                onSelect = viewModel::setFilter,
            )
            when {
                uiState.loading -> LoadingState()
                uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::retry)
                uiState.entries.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Tidak ada catatan",
                    description = "Belum ada aktivitas pada filter ini.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        AuditRowCard(entry)
                    }
                    if (uiState.hasMore) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Button(onClick = viewModel::loadMore, enabled = !uiState.loadingMore) {
                                    Text(if (uiState.loadingMore) "Memuat..." else "Muat Lagi")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditFilterDropdown(
    selected: AuditActionFilter,
    onSelect: (AuditActionFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AuditActionFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label) },
                    onClick = {
                        onSelect(filter)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AuditRowCard(entry: AuditLogEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TimeProvider.formatForDisplay(entry.occurredAt),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.action,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = entry.actorName ?: entry.actorId?.take(8)?.plus("…") ?: "Sistem",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(entry.entityType)
                    entry.entityId?.let { append(" · ${it.take(8)}…") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.metadata.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = JsonCodec.encode(entry.metadata),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
