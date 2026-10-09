package com.ratig.app.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.domain.model.ProtocolConfiguration
import com.ratig.app.domain.model.ProtocolStatus
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.ui.components.ErrorState
import com.ratig.app.ui.components.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProtocolEditUiState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val saving: Boolean = false,
    val existingId: String? = null,
    val existingStatus: ProtocolStatus? = null,
    val name: String = "",
    val protocolVersion: String = "",
    val trialCount: String = "20",
    val delayMinMs: String = "1500",
    val delayMaxMs: String = "3500",
    val responseTimeoutMs: String = "2000",
    val interTrialDelayMs: String = "800",
    val practiceTrialCount: String = "0",
    val minPlausibleReactionMs: String = "80",
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
)

@HiltViewModel
class ProtocolEditViewModel @Inject constructor(
    private val protocolRepository: ProtocolRepository,
    supabase: SupabaseClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val protocolId: String? = savedStateHandle.get<String>("protocolId")
    private val currentUserId: String? = supabase.auth.currentUserOrNull()?.id

    private val _uiState = MutableStateFlow(ProtocolEditUiState(existingId = protocolId))
    val uiState: StateFlow<ProtocolEditUiState> = _uiState.asStateFlow()

    init {
        protocolId?.let { id -> load(id) }
    }

    fun retry() = protocolId?.let(::load)

    private fun load(id: String) {
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            protocolRepository.getProtocol(id)
                .onSuccess { protocol ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            name = protocol.name,
                            protocolVersion = protocol.protocolVersion,
                            trialCount = protocol.trialCount.toString(),
                            delayMinMs = protocol.stimulusDelayMinMs.toString(),
                            delayMaxMs = protocol.stimulusDelayMaxMs.toString(),
                            responseTimeoutMs = protocol.responseTimeoutMs.toString(),
                            interTrialDelayMs = protocol.configuration.interTrialDelayMs.toString(),
                            practiceTrialCount = protocol.configuration.practiceTrialCount.toString(),
                            minPlausibleReactionMs = protocol.configuration.minPlausibleReactionMs.toString(),
                            existingStatus = protocol.status,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, loadError = error.userMessage) }
                }
        }
    }

    fun setName(value: String) = updateField { it.copy(name = value) }
    fun setProtocolVersion(value: String) = updateField { it.copy(protocolVersion = value) }
    fun setTrialCount(value: String) = updateField { it.copy(trialCount = value.filter(Char::isDigit)) }
    fun setDelayMinMs(value: String) = updateField { it.copy(delayMinMs = value.filter(Char::isDigit)) }
    fun setDelayMaxMs(value: String) = updateField { it.copy(delayMaxMs = value.filter(Char::isDigit)) }
    fun setResponseTimeoutMs(value: String) = updateField { it.copy(responseTimeoutMs = value.filter(Char::isDigit)) }
    fun setInterTrialDelayMs(value: String) = updateField { it.copy(interTrialDelayMs = value.filter(Char::isDigit)) }
    fun setPracticeTrialCount(value: String) = updateField { it.copy(practiceTrialCount = value.filter(Char::isDigit)) }
    fun setMinPlausibleReactionMs(value: String) = updateField { it.copy(minPlausibleReactionMs = value.filter(Char::isDigit)) }

    private fun updateField(transform: (ProtocolEditUiState) -> ProtocolEditUiState) {
        _uiState.update { current ->
            val next = transform(current)
            next.copy(fieldErrors = emptyMap(), formError = null)
        }
    }

    /** Validates per CONTRACT constraints and upserts the protocol as a draft. */
    fun save(onDone: () -> Unit) {
        val state = _uiState.value
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, formError = "Periksa kembali isian yang ditandai.") }
            return
        }
        val existingStatus = state.existingStatus ?: ProtocolStatus.DRAFT
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val protocol = TestProtocol(
                id = state.existingId ?: UUID.randomUUID().toString(),
                name = state.name.trim(),
                protocolVersion = state.protocolVersion.trim(),
                trialCount = state.trialCount.toInt(),
                stimulusDelayMinMs = state.delayMinMs.toLong(),
                stimulusDelayMaxMs = state.delayMaxMs.toLong(),
                responseTimeoutMs = state.responseTimeoutMs.toLong(),
                configuration = ProtocolConfiguration(
                    interTrialDelayMs = state.interTrialDelayMs.toLong(),
                    practiceTrialCount = state.practiceTrialCount.toInt(),
                    minPlausibleReactionMs = state.minPlausibleReactionMs.toLong(),
                ),
                status = existingStatus,
                createdBy = currentUserId,
            )
            protocolRepository.upsertProtocol(protocol)
                .onSuccess {
                    _uiState.update { it.copy(saving = false) }
                    onDone()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(saving = false, formError = error.userMessage) }
                }
        }
    }
}

private fun validate(state: ProtocolEditUiState): Map<String, String> {
    val errors = mutableMapOf<String, String>()
    if (state.name.isBlank()) errors["name"] = "Nama protokol wajib diisi."
    if (state.protocolVersion.isBlank()) errors["protocolVersion"] = "Versi protokol wajib diisi."
    val trialCount = state.trialCount.toIntOrNull()
    when {
        trialCount == null -> errors["trialCount"] = "Jumlah percobaan harus berupa angka."
        trialCount !in 1..50 -> errors["trialCount"] = "Jumlah percobaan harus antara 1 dan 50."
    }
    val delayMin = state.delayMinMs.toLongOrNull()
    val delayMax = state.delayMaxMs.toLongOrNull()
    when {
        delayMin == null || delayMin <= 0 -> errors["delayMin"] = "Delay minimum harus lebih dari 0 ms."
        delayMax == null || delayMax <= 0 -> errors["delayMax"] = "Delay maksimum harus lebih dari 0 ms."
        delayMax <= delayMin -> errors["delayMax"] = "Delay maksimum harus lebih besar dari delay minimum."
    }
    val timeout = state.responseTimeoutMs.toLongOrNull()
    when {
        timeout == null || timeout <= 0 -> errors["responseTimeout"] = "Batas respons harus lebih dari 0 ms."
    }
    val interTrial = state.interTrialDelayMs.toLongOrNull()
    if (interTrial == null || interTrial < 0) errors["interTrial"] = "Jeda antar percobaan tidak valid."
    val practice = state.practiceTrialCount.toIntOrNull()
    if (practice == null || practice !in 0..50) errors["practice"] = "Percobaan latihan harus antara 0 dan 50."
    val minPlausible = state.minPlausibleReactionMs.toLongOrNull()
    if (minPlausible == null || minPlausible <= 0) errors["minPlausible"] = "Reaksi minimum wajar harus lebih dari 0 ms."
    return errors
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolEditRoute(
    onDone: () -> Unit,
    viewModel: ProtocolEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.formError) {
        uiState.formError?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.existingId == null) "Protokol Baru" else "Ubah Protokol") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.loading -> LoadingState()
            uiState.loadError != null -> ErrorState(message = uiState.loadError!!, onRetry = viewModel::retry)
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.existingStatus != null && uiState.existingStatus != ProtocolStatus.DRAFT) {
                    Text(
                        text = "Perhatian: protokol ini berstatus " +
                            "${protocolStatusLabel(uiState.existingStatus!!)}. Perubahan akan tersimpan pada versi yang sama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nama protokol") },
                    supportingText = uiState.fieldErrorText("name"),
                    isError = uiState.fieldErrors.containsKey("name"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.protocolVersion,
                    onValueChange = viewModel::setProtocolVersion,
                    label = { Text("Versi protokol") },
                    supportingText = uiState.fieldErrorText("protocolVersion"),
                    isError = uiState.fieldErrors.containsKey("protocolVersion"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumericField(
                    label = "Jumlah percobaan (1–50)",
                    value = uiState.trialCount,
                    onValueChange = viewModel::setTrialCount,
                    error = uiState.fieldErrorText("trialCount"),
                )
                NumericField(
                    label = "Delay stimulus minimum (ms)",
                    value = uiState.delayMinMs,
                    onValueChange = viewModel::setDelayMinMs,
                    error = uiState.fieldErrorText("delayMin"),
                )
                NumericField(
                    label = "Delay stimulus maksimum (ms)",
                    value = uiState.delayMaxMs,
                    onValueChange = viewModel::setDelayMaxMs,
                    error = uiState.fieldErrorText("delayMax"),
                )
                NumericField(
                    label = "Batas waktu respons (ms)",
                    value = uiState.responseTimeoutMs,
                    onValueChange = viewModel::setResponseTimeoutMs,
                    error = uiState.fieldErrorText("responseTimeout"),
                )

                Text("Konfigurasi tambahan", style = MaterialTheme.typography.titleSmall)
                NumericField(
                    label = "Jeda antar percobaan (ms)",
                    value = uiState.interTrialDelayMs,
                    onValueChange = viewModel::setInterTrialDelayMs,
                    error = uiState.fieldErrorText("interTrial"),
                )
                NumericField(
                    label = "Jumlah percobaan latihan",
                    value = uiState.practiceTrialCount,
                    onValueChange = viewModel::setPracticeTrialCount,
                    error = uiState.fieldErrorText("practice"),
                )
                NumericField(
                    label = "Reaksi minimum wajar (ms)",
                    value = uiState.minPlausibleReactionMs,
                    onValueChange = viewModel::setMinPlausibleReactionMs,
                    error = uiState.fieldErrorText("minPlausible"),
                )

                Button(
                    onClick = { viewModel.save(onDone = onDone) },
                    enabled = !uiState.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.saving) "Menyimpan..." else "Simpan sebagai Draf")
                }
            }
        }
    }
}

@Composable
private fun ProtocolEditUiState.fieldErrorText(key: String): (@Composable () -> Unit)? =
    fieldErrors[key]?.let { message -> { Text(message) } }

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = error,
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
