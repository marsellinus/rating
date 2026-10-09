package com.ratig.app.feature.testflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
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

/**
 * Pre-test briefing. The examiner walks the worker through the rules; the test
 * can only start after the understanding checklist is confirmed.
 */
@HiltViewModel
class TestInstructionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: TestSessionRepository,
    private val protocolRepository: ProtocolRepository,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val session: TestSession? = null,
        val protocol: TestProtocol? = null,
        val starting: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val session = when (val result = sessionRepository.getSession(sessionId)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false, error = result.error.userMessage) }
                    return@launch
                }
            }
            val protocol = when (val result = protocolRepository.getProtocol(session.protocolId)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false, error = result.error.userMessage) }
                    return@launch
                }
            }
            _uiState.update { it.copy(loading = false, session = session, protocol = protocol) }
        }
    }

    /** Marks the session in progress, then navigates on to the measurement screen. */
    fun startTest(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(starting = true, error = null) }
            when (val result = sessionRepository.markInProgress(sessionId)) {
                is AppResult.Success -> onReady(sessionId)
                is AppResult.Failure -> _uiState.update {
                    it.copy(starting = false, error = result.error.userMessage)
                }
            }
        }
    }

    /** Best-effort failure marking; navigation proceeds regardless. */
    fun abort(onAborted: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.markFailed(sessionId, "Dibatalkan petugas")
            onAborted()
        }
    }
}

@Composable
fun TestInstructionsRoute(onReady: (String) -> Unit, onAborted: () -> Unit) {
    val viewModel: TestInstructionsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var showAbortDialog by rememberSaveable { mutableStateOf(false) }

    when {
        uiState.loading -> LoadingState()

        uiState.error != null -> ErrorState(
            message = uiState.error.orEmpty(),
            onRetry = viewModel::refresh,
        )

        uiState.session == null || uiState.protocol == null -> EmptyState(
            icon = Icons.Outlined.Assignment,
            title = "Data sesi tidak lengkap",
            description = "Sesi atau protokol tidak dapat dimuat.",
            action = {
                Button(onClick = viewModel::refresh) { Text("Muat ulang") }
            },
        )

        else -> {
            val session = uiState.session ?: return
            val protocol = uiState.protocol ?: return
            TestInstructionsContent(
                session = session,
                protocol = protocol,
                confirmed = confirmed,
                starting = uiState.starting,
                onConfirmedChange = { confirmed = it },
                onStart = { viewModel.startTest(onReady) },
                onCancel = { showAbortDialog = true },
            )
            if (showAbortDialog) {
                ConfirmDialog(
                    title = "Batalkan sesi?",
                    message = "Sesi akan ditandai gagal dan tidak dapat dilanjutkan.",
                    confirmLabel = "Ya, batalkan",
                    destructive = true,
                    onConfirm = {
                        showAbortDialog = false
                        viewModel.abort(onAborted)
                    },
                    onDismiss = { showAbortDialog = false },
                )
            }
        }
    }
}

@Composable
private fun TestInstructionsContent(
    session: TestSession,
    protocol: TestProtocol,
    confirmed: Boolean,
    starting: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Persiapan Tes RATIG",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                    Text("Pekerja", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = session.workerName ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                session.workerNumber?.let {
                    Text("Nomor induk: $it", style = MaterialTheme.typography.bodySmall)
                }
                session.examinerName?.let {
                    Text("Pemeriksa: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Assignment, contentDescription = null)
                    Text("Protokol", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = protocol.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Versi protokol: ${protocol.protocolVersion}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Jumlah percobaan: ${protocol.trialCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Rule, contentDescription = null)
                    Text("Aturan Tes", style = MaterialTheme.typography.labelLarge)
                }
                listOf(
                    "Duduk nyaman, pegang perangkat dengan stabil, dan fokus ke layar.",
                    "Ketuk layar secepat mungkin begitu area tes berubah HIJAU.",
                    "Jangan ketuk sebelum stimulus muncul - ketukan terlalu dini dihitung sebagai false start dan percobaan tidak sah.",
                    "Jika tidak sempat mengetuk hingga waktu habis, percobaan dihitung sebagai respons terlewat.",
                    "Ikuti seluruh ${protocol.trialCount} percobaan tanpa berbicara atau bergerak berlebihan.",
                    "Segera beri tahu petugas jika ingin berhenti.",
                ).forEach { rule ->
                    Text("• $rule", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onConfirmedChange(!confirmed) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = confirmed, onCheckedChange = { onConfirmedChange(it) })
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Pekerja memahami instruksi",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = onStart,
            enabled = confirmed && !starting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(if (starting) "Memulai..." else "Mulai Tes")
        }

        OutlinedButton(
            onClick = onCancel,
            enabled = !starting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Batal")
        }
    }
}
