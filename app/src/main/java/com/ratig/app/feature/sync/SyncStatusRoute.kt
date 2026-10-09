package com.ratig.app.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.core.sync.SyncSnapshot
import com.ratig.app.core.sync.displayLabel
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.sync.SessionSyncRow
import com.ratig.app.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

/**
 * CONTRACT-2 §E: full sync status screen - online/offline chip, last
 * successful sync, pending session/trial counts, failed count, safe failure
 * reasons, manual sync trigger, and the per-session list using the exact
 * Indonesian status vocabulary.
 */
@OptIn(ExperimentalMaterial3Api::class)
data class SyncStatusUiState(
    val loading: Boolean = true,
    val snapshot: SyncSnapshot = SyncSnapshot(),
    val isSyncing: Boolean = false,
    val sessions: List<SessionSyncRow> = emptyList(),
)

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val uiState: StateFlow<SyncStatusUiState> = combine(
        syncRepository.snapshot,
        syncRepository.isSyncing,
        syncRepository.observeSessionRows(),
    ) { snapshot, syncing, rows ->
        SyncStatusUiState(loading = false, snapshot = snapshot, isSyncing = syncing, sessions = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatusUiState())

    /** "Sinkronkan sekarang" - enqueues a one-shot CONNECTED-constrained sync. */
    fun syncNow() = syncRepository.requestManualSync()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusRoute(
    onBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Status Sinkronisasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        val state = uiState
        if (state.loading) {
            com.ratig.app.ui.components.LoadingState(message = "Memuat status sinkronisasi...", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        SyncStatusContent(
            state = state,
            onSyncNow = viewModel::syncNow,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun SyncStatusContent(
    state: SyncStatusUiState,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ConnectivityAndSummaryCard(snapshot = snapshot) }

        item {
            Button(
                onClick = onSyncNow,
                enabled = !state.isSyncing && snapshot.isOnline,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (state.isSyncing) "Sedang disinkronkan..." else "Sinkronkan sekarang")
            }
            if (!snapshot.isOnline) {
                Text(
                    text = "Perangkat sedang offline. Data tersimpan aman di perangkat dan akan terkirim otomatis saat koneksi kembali.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (snapshot.lastSyncError != null) {
            item { LastErrorBanner(reason = snapshot.lastSyncError) }
        }

        item {
            Text(
                text = "Riwayat sesi (${state.sessions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!state.loading && state.sessions.isEmpty()) {
            item {
                com.ratig.app.ui.components.EmptyState(
                    icon = Icons.Rounded.CloudDone,
                    title = "Belum ada sesi",
                    description = "Sesi pemeriksaan yang tersimpan di perangkat akan muncul di sini beserta status sinkronisasinya.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(state.sessions, key = { it.localId }) { row ->
            SessionSyncRowItem(row = row, isOnline = snapshot.isOnline)
        }
    }
}

@Composable
private fun ConnectivityAndSummaryCard(snapshot: SyncSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (snapshot.isOnline) "Online" else "Offline") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (snapshot.isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = if (snapshot.isOnline) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        disabledLabelColor = if (snapshot.isOnline) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sinkronisasi terakhir",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = snapshot.lastSyncAt?.let { TimeProvider.formatForDisplay(it) } ?: "Belum pernah",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryTile(label = "Menunggu sinkron", value = snapshot.pendingSessions.toString(), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SummaryTile(label = "Percobaan tertunda", value = snapshot.pendingTrials.toString(), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SummaryTile(label = "Perlu tinjauan", value = snapshot.failedCount.toString(), modifier = Modifier.weight(1f))
            }

            if (snapshot.lastSyncAt == null && snapshot.hasWork) {
                Text(
                    text = "Belum ada konfirmasi dari server. Data tidak akan hilang - perangkat mencoba kembali secara otomatis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LastErrorBanner(reason: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.SyncProblem, contentDescription = null)
            Column {
                Text("Alasan kegagalan terakhir", style = MaterialTheme.typography.labelLarge)
                Text(reason, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SessionSyncRowItem(row: SessionSyncRow, isOnline: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.workerName ?: "Pekerja",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = listOfNotNull(
                            row.workerNumber?.let { "No. $it" },
                            row.startedAt?.let { TimeProvider.formatDateForDisplay(it) },
                            row.testMode?.let { modeLabel(it) },
                        ).joinToString(" • ").ifEmpty { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = row.syncStatus, isOnline = isOnline)
            }

            if (row.lastSyncError != null && row.syncStatus != SyncStatus.SYNCED) {
                Text(
                    text = row.lastSyncError,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.syncStatus == SyncStatus.FAILED_RETRYABLE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (row.retryCount > 0 && row.syncStatus != SyncStatus.SYNCED) {
                Text(
                    text = "Percobaan: ${row.retryCount} dari ${com.ratig.app.core.sync.SYNC_MAX_RETRIES}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SyncStatus, isOnline: Boolean) {
    val severity = when (status) {
        SyncStatus.SYNCED -> 0
        SyncStatus.SYNCING, SyncStatus.PENDING -> 1
        SyncStatus.FAILED_RETRYABLE -> 3
        SyncStatus.FAILED_PERMANENT, SyncStatus.NEEDS_REVIEW -> 4
    }
    val (bg, fg) = com.ratig.app.ui.components.severityColors(severity)
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = statusIcon(status),
                contentDescription = null,
                modifier = Modifier.width(14.dp),
            )
            Text(status.displayLabel(isOnline), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun statusIcon(status: SyncStatus): ImageVector = when (status) {
    SyncStatus.SYNCED -> Icons.Rounded.CloudDone
    SyncStatus.SYNCING -> Icons.Rounded.CloudSync
    SyncStatus.PENDING -> Icons.Rounded.CloudOff
    SyncStatus.FAILED_RETRYABLE, SyncStatus.FAILED_PERMANENT, SyncStatus.NEEDS_REVIEW ->
        Icons.Rounded.SyncProblem
}

/** Bahasa Indonesia labels for the four test modes (display only). */
private fun modeLabel(raw: String): String = when (raw) {
    "classic", "CLASSIC" -> "Klasik"
    "rgb_random", "RGB_RANDOM" -> "Warna Acak"
    "random_button", "RANDOM_BUTTON" -> "Tombol Acak"
    "focus_inhibition", "FOCUS_INHIBITION" -> "Fokus & Inhibisi"
    else -> raw
}
