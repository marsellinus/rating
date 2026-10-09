package com.ratig.app.feature.admin

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
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.ratig.app.domain.model.ProtocolStatus
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.repository.ProtocolRepository
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

data class ProtocolsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val protocols: List<TestProtocol> = emptyList(),
    /** Protocol id whose activation is awaiting confirmation. */
    val pendingActivationId: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class ProtocolsViewModel @Inject constructor(
    private val protocolRepository: ProtocolRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProtocolsUiState())
    val uiState: StateFlow<ProtocolsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            protocolRepository.listAllProtocols()
                .onSuccess { protocols -> _uiState.update { it.copy(loading = false, protocols = protocols) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage) } }
        }
    }

    /**
     * Activation requires an approved rule currently effective for the
     * protocol. Checked server-authoritatively via effectiveRule before the
     * confirm dialog appears; a missing rule surfaces a blocking snackbar.
     */
    fun requestActivate(protocol: TestProtocol) {
        viewModelScope.launch {
            protocolRepository.effectiveRule(protocol.id)
                .onSuccess { rule ->
                    if (rule == null) {
                        _uiState.update {
                            it.copy(
                                actionError = "Protokol belum memiliki aturan klasifikasi yang disetujui. " +
                                    "Buat dan setujui aturan terlebih dahulu di menu Aturan.",
                            )
                        }
                    } else {
                        _uiState.update { it.copy(pendingActivationId = protocol.id) }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun confirmActivate() {
        val id = _uiState.value.pendingActivationId ?: return
        viewModelScope.launch {
            protocolRepository.setProtocolStatus(id, ProtocolStatus.ACTIVE)
                .onSuccess { _uiState.update { it.copy(actionMessage = "Protokol diaktifkan.") } }
                .onSuccess { _uiState.update { it.copy(pendingActivationId = null) } }
                .onSuccess { refresh() }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun dismissActivation() = _uiState.update { it.copy(pendingActivationId = null) }

    fun retire(protocol: TestProtocol) {
        viewModelScope.launch {
            protocolRepository.setProtocolStatus(protocol.id, ProtocolStatus.RETIRED)
                .onSuccess { _uiState.update { it.copy(actionMessage = "Protokol ditarik.") } }
                .onSuccess { refresh() }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }
}

fun protocolStatusLabel(status: ProtocolStatus): String = when (status) {
    ProtocolStatus.DRAFT -> "Draf"
    ProtocolStatus.ACTIVE -> "Aktif"
    ProtocolStatus.RETIRED -> "Ditarik"
}

fun protocolStatusSeverity(status: ProtocolStatus): Int = when (status) {
    ProtocolStatus.DRAFT -> 3
    ProtocolStatus.ACTIVE -> 1
    ProtocolStatus.RETIRED -> -1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolsRoute(
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    viewModel: ProtocolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var retiringProtocol by remember { mutableStateOf<TestProtocol?>(null) }

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
                title = { Text("Protokol Tes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Protokol baru")
            }
        },
    ) { padding ->
        when {
            uiState.loading -> LoadingState()
            uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::refresh)
            uiState.protocols.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Rule,
                title = "Belum ada protokol",
                description = "Buat protokol tes untuk menentukan cara pengukuran waktu reaksi.",
                action = {
                    TextButton(onClick = { onEdit(null) }) { Text("Buat Protokol") }
                },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.protocols, key = { it.id }) { protocol ->
                    ProtocolRowCard(
                        protocol = protocol,
                        onEdit = { onEdit(protocol.id) },
                        onActivate = { viewModel.requestActivate(protocol) },
                        onRetire = { retiringProtocol = protocol },
                    )
                }
            }
        }
    }

    uiState.pendingActivationId?.let { protocolId ->
        val protocol = uiState.protocols.firstOrNull { it.id == protocolId }
        ConfirmDialog(
            title = "Aktifkan protokol?",
            message = "Protokol \"${protocol?.name ?: protocolId}\" akan menjadi protokol aktif untuk sesi tes baru.",
            confirmLabel = "Aktifkan",
            onConfirm = viewModel::confirmActivate,
            onDismiss = viewModel::dismissActivation,
        )
    }

    retiringProtocol?.let { protocol ->
        ConfirmDialog(
            title = "Tarik protokol?",
            message = "Protokol \"${protocol.name}\" tidak akan dapat dipakai untuk sesi baru. Riwayat sesi lama tetap utuh.",
            confirmLabel = "Tarik",
            destructive = true,
            onConfirm = {
                viewModel.retire(protocol)
                retiringProtocol = null
            },
            onDismiss = { retiringProtocol = null },
        )
    }
}

@Composable
private fun ProtocolRowCard(
    protocol: TestProtocol,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onRetire: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = protocol.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "v${protocol.protocolVersion} · ${protocol.trialCount} percobaan · " +
                        "timeout ${protocol.responseTimeoutMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (protocol.approvedBy != null) {
                    Text(
                        text = "Disetujui: ${protocol.approvedBy!!.take(8)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SeverityBadgeForStatus(protocol.status)
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Aksi protokol")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (protocol.status == ProtocolStatus.DRAFT) {
                    DropdownMenuItem(
                        text = { Text("Ubah") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Aktifkan") },
                        onClick = { menuOpen = false; onActivate() },
                    )
                }
                if (protocol.status == ProtocolStatus.ACTIVE) {
                    DropdownMenuItem(
                        text = { Text("Tarik") },
                        onClick = { menuOpen = false; onRetire() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityBadgeForStatus(status: ProtocolStatus) {
    Surface(
        color = when (status) {
            ProtocolStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant
            ProtocolStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
            ProtocolStatus.RETIRED -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when (status) {
            ProtocolStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
            ProtocolStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
            ProtocolStatus.RETIRED -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = protocolStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
