package com.ratig.app.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.ApprovalStatus
import com.ratig.app.domain.model.ClassificationLogic
import com.ratig.app.domain.model.FatigueRule
import com.ratig.app.domain.model.RuleBand
import com.ratig.app.domain.model.RuleConfig
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.ui.components.ConfirmDialog
import com.ratig.app.ui.components.EmptyState
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

/** Editable text representation of one classification band row. */
data class BandField(
    val minMs: String,
    val maxMsExclusive: String, // blank = open-ended top band
    val code: String,
    val label: String,
    val severity: String,
)

data class RulesUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val protocols: List<TestProtocol> = emptyList(),
    val selectedProtocolId: String? = null,
    val rules: List<FatigueRule> = emptyList(),
    /** null = a new, not-yet-saved draft is being edited. */
    val selectedRuleId: String? = null,
    val version: String = "",
    val bands: List<BandField> = emptyList(),
    val slowThreshold: String = "580",
    val logic: ClassificationLogic = ClassificationLogic.MEAN_MS_BAND,
    val minValidTrials: String = "4",
    val excludeFalseStarts: Boolean = true,
    val excludeAbove: String = "2000",
    val jsonExpanded: Boolean = false,
    val saving: Boolean = false,
    val formError: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val pendingApprovalRuleId: String? = null,
    val pendingRetireRuleId: String? = null,
) {
    /** True while any band label is blank or still the unvalidated placeholder. */
    val labelsNeedValidation: Boolean
        get() = bands.any {
            it.label.isBlank() || it.label.trim().equals("Perlu validasi", ignoreCase = true)
        }

    val selectedRule: FatigueRule? get() = rules.firstOrNull { it.id == selectedRuleId }
}

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val protocolRepository: ProtocolRepository,
    supabase: SupabaseClient,
) : ViewModel() {

    private val currentUserId: String? = supabase.auth.currentUserOrNull()?.id

    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    init {
        loadProtocols()
    }

    fun retry() {
        loadProtocols()
    }

    private fun loadProtocols() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            protocolRepository.listAllProtocols()
                .onSuccess { protocols ->
                    _uiState.update { it.copy(loading = false, protocols = protocols) }
                    val current = _uiState.value.selectedProtocolId
                    val target = protocols.firstOrNull { it.id == current } ?: protocols.firstOrNull()
                    if (target != null) {
                        selectProtocol(target.id)
                    } else {
                        _uiState.update { it.copy(rules = emptyList(), selectedProtocolId = null) }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.userMessage) }
                }
        }
    }

    fun selectProtocol(protocolId: String) {
        _uiState.update { it.copy(selectedProtocolId = protocolId) }
        viewModelScope.launch {
            protocolRepository.listRules(protocolId)
                .onSuccess { rules ->
                    _uiState.update { it.copy(rules = rules) }
                    val draft = rules.firstOrNull { it.approvalStatus == ApprovalStatus.DRAFT }
                    fillEditor(draft ?: rules.firstOrNull(), select = true)
                }
                .onFailure { error -> _uiState.update { it.copy(actionError = error.userMessage) } }
        }
    }

    fun selectRule(rule: FatigueRule) = fillEditor(rule, select = true)

    /** Starts a fresh unsaved draft with the reference (unvalidated) bands. */
    fun newDraft() {
        val nextVersion = "v" + (_uiState.value.rules.size + 1)
        fillEditor(null, select = false)
        _uiState.update { it.copy(selectedRuleId = null, version = nextVersion) }
    }

    private fun fillEditor(rule: FatigueRule?, select: Boolean) {
        if (rule == null) {
            val defaults = RuleConfig.defaultUnvalidated()
            _uiState.update {
                it.copy(
                    selectedRuleId = if (select) null else it.selectedRuleId,
                    version = it.version.ifBlank { "v1" },
                    bands = defaults.bands.map(::toBandField),
                    slowThreshold = defaults.slowResponseThresholdMs.toString(),
                    logic = defaults.logic,
                    minValidTrials = defaults.minValidTrials.toString(),
                    excludeFalseStarts = defaults.excludeFalseStarts,
                    excludeAbove = defaults.excludeReactionAboveMs?.toString() ?: "",
                    formError = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedRuleId = if (select) rule.id else it.selectedRuleId,
                version = rule.ruleVersion,
                bands = rule.config.bands.map(::toBandField),
                slowThreshold = rule.config.slowResponseThresholdMs.toString(),
                logic = rule.config.logic,
                minValidTrials = rule.config.minValidTrials.toString(),
                excludeFalseStarts = rule.config.excludeFalseStarts,
                excludeAbove = rule.config.excludeReactionAboveMs?.toString() ?: "",
                formError = null,
            )
        }
    }

    private fun toBandField(band: RuleBand): BandField = BandField(
        minMs = band.minMs.toString(),
        maxMsExclusive = band.maxMsExclusive?.toString() ?: "",
        code = band.code,
        label = band.label,
        severity = band.severity.toString(),
    )

    fun setVersion(value: String) = _uiState.update { it.copy(version = value, formError = null) }
    fun setSlowThreshold(value: String) =
        _uiState.update { it.copy(slowThreshold = value.filter(Char::isDigit), formError = null) }

    fun setLogic(value: ClassificationLogic) =
        _uiState.update { it.copy(logic = value, formError = null) }

    fun setMinValidTrials(value: String) =
        _uiState.update { it.copy(minValidTrials = value.filter(Char::isDigit), formError = null) }

    fun setExcludeFalseStarts(value: Boolean) =
        _uiState.update { it.copy(excludeFalseStarts = value, formError = null) }

    fun setExcludeAbove(value: String) =
        _uiState.update { it.copy(excludeAbove = value.filter(Char::isDigit), formError = null) }

    fun toggleJson() = _uiState.update { it.copy(jsonExpanded = !it.jsonExpanded) }

    fun updateBand(index: Int, band: BandField) {
        _uiState.update { state ->
            if (index !in state.bands.indices) state else state.copy(
                bands = state.bands.toMutableList().apply { set(index, band) },
                formError = null,
            )
        }
    }

    fun addBand() {
        _uiState.update { state ->
            val last = state.bands.lastOrNull()
            val nextMin = last?.maxMsExclusive?.takeIf { it.isNotBlank() } ?: "0"
            state.copy(bands = state.bands + BandField(minMs = nextMin, maxMsExclusive = "", code = "", label = "", severity = "1"))
        }
    }

    fun removeBand(index: Int) {
        _uiState.update { state ->
            if (index !in state.bands.indices || state.bands.size <= 1) state else state.copy(
                bands = state.bands.toMutableList().apply { removeAt(index) },
                formError = null,
            )
        }
    }

    private fun buildConfig(state: RulesUiState): RuleConfig? {
        val bands = mutableListOf<RuleBand>()
        for ((index, field) in state.bands.withIndex()) {
            val min = field.minMs.toLongOrNull()
            if (min == null || min < 0) return null
            val max = field.maxMsExclusive.toLongOrNull()
            if (!field.maxMsExclusive.isBlank() && (max == null || max <= min)) return null
            if (field.code.isBlank()) return null
            val severity = field.severity.toIntOrNull()
            if (severity == null || severity < 0) return null
            val previous = bands.getOrNull(index - 1)
            if (previous != null && (previous.maxMsExclusive ?: Long.MAX_VALUE) != min) return null
            bands.add(
                RuleBand(
                    minMs = min,
                    maxMsExclusive = if (field.maxMsExclusive.isBlank()) null else max,
                    code = field.code.trim(),
                    label = field.label.trim(),
                    severity = severity,
                ),
            )
        }
        val threshold = state.slowThreshold.toLongOrNull() ?: return null
        val minValid = state.minValidTrials.toIntOrNull() ?: return null
        if (minValid <= 0) return null
        return RuleConfig(
            bands = bands,
            slowResponseThresholdMs = threshold,
            logic = state.logic,
            slowCountBands = _uiState.value.selectedRule?.config?.slowCountBands ?: emptyList(),
            minValidTrials = minValid,
            excludeFalseStarts = state.excludeFalseStarts,
            excludeReactionAboveMs = state.excludeAbove.toLongOrNull(),
        )
    }

    /** Saves the editor content as a DRAFT rule (insert or update). */
    fun saveDraft() {
        val state = _uiState.value
        val protocolId = state.selectedProtocolId
        if (protocolId == null) {
            _uiState.update { it.copy(formError = "Pilih protokol terlebih dahulu.") }
            return
        }
        if (state.version.isBlank()) {
            _uiState.update { it.copy(formError = "Versi aturan wajib diisi.") }
            return
        }
        val config = buildConfig(state)
        if (config == null) {
            _uiState.update {
                it.copy(
                    formError = "Isian pita tidak valid. Pastikan batas min < maks, berurutan, " +
                        "kode dan label terisi, serta severity berupa angka.",
                )
            }
            return
        }
        val existing = state.selectedRule
        if (existing != null && existing.approvalStatus != ApprovalStatus.DRAFT) {
            _uiState.update { it.copy(formError = "Hanya aturan berstatus draf yang dapat diubah.") }
            return
        }
        _uiState.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val rule = FatigueRule(
                id = existing?.id ?: UUID.randomUUID().toString(),
                protocolId = protocolId,
                ruleVersion = state.version.trim(),
                config = config,
                effectiveFrom = existing?.effectiveFrom ?: TimeProvider.nowUtc(),
                effectiveUntil = existing?.effectiveUntil,
                approvalStatus = ApprovalStatus.DRAFT,
                createdBy = existing?.createdBy ?: currentUserId,
            )
            protocolRepository.upsertRule(rule)
                .onSuccess { saved ->
                    _uiState.update { it.copy(saving = false, actionMessage = "Draf aturan disimpan.") }
                    selectProtocol(protocolId)
                    fillEditor(saved, select = true)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(saving = false, formError = error.userMessage) }
                }
        }
    }

    fun requestApproval(ruleId: String) = _uiState.update { it.copy(pendingApprovalRuleId = ruleId) }

    fun confirmApproval() {
        val ruleId = _uiState.value.pendingApprovalRuleId ?: return
        viewModelScope.launch {
            protocolRepository.approveRule(ruleId)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Aturan disetujui dan siap digunakan.") }
                    _uiState.update { it.copy(pendingApprovalRuleId = null) }
                    _uiState.value.selectedProtocolId?.let(::selectProtocol)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(actionError = error.userMessage, pendingApprovalRuleId = null) }
                }
        }
    }

    fun dismissApproval() = _uiState.update { it.copy(pendingApprovalRuleId = null) }

    fun requestRetire(ruleId: String) = _uiState.update { it.copy(pendingRetireRuleId = ruleId) }

    fun confirmRetire() {
        val state = _uiState.value
        val ruleId = state.pendingRetireRuleId ?: return
        val rule = state.rules.firstOrNull { it.id == ruleId } ?: return
        viewModelScope.launch {
            protocolRepository.upsertRule(
                rule.copy(approvalStatus = ApprovalStatus.RETIRED, effectiveUntil = TimeProvider.nowUtc()),
            )
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Aturan ditarik.") }
                    _uiState.update { it.copy(pendingRetireRuleId = null) }
                    state.selectedProtocolId?.let(::selectProtocol)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(actionError = error.userMessage, pendingRetireRuleId = null) }
                }
        }
    }

    fun dismissRetire() = _uiState.update { it.copy(pendingRetireRuleId = null) }

    fun consumeActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun consumeActionError() = _uiState.update { it.copy(actionError = null) }
}

fun approvalStatusLabel(status: ApprovalStatus): String = when (status) {
    ApprovalStatus.DRAFT -> "Draf"
    ApprovalStatus.APPROVED -> "Disetujui"
    ApprovalStatus.RETIRED -> "Ditarik"
}

fun approvalStatusSeverity(status: ApprovalStatus): Int = when (status) {
    ApprovalStatus.DRAFT -> 3
    ApprovalStatus.APPROVED -> 1
    ApprovalStatus.RETIRED -> -1
}

fun logicLabel(logic: ClassificationLogic): String = when (logic) {
    ClassificationLogic.MEAN_MS_BAND -> "Rata-rata (mean)"
    ClassificationLogic.MEDIAN_MS_BAND -> "Median"
    ClassificationLogic.SLOW_COUNT_BAND -> "Jumlah respons lambat"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesRoute(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
    LaunchedEffect(uiState.formError) {
        uiState.formError?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aturan Klasifikasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.loading -> LoadingState()
            uiState.error != null -> ErrorState(message = uiState.error!!, onRetry = viewModel::retry)
            uiState.protocols.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Code,
                title = "Belum ada protokol",
                description = "Aturan klasifikasi terikat pada protokol. Buat protokol terlebih dahulu.",
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProtocolSelector(uiState = uiState, onSelect = viewModel::selectProtocol)

                RuleVersionList(
                    uiState = uiState,
                    onSelect = viewModel::selectRule,
                    onNewDraft = viewModel::newDraft,
                )

                if (uiState.selectedProtocolId != null) {
                    RuleEditorSection(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }

    uiState.pendingApprovalRuleId?.let {
        ConfirmDialog(
            title = "Setujui aturan?",
            message = "Menyetujui aturan klasifikasi berarti label kategori telah divalidasi organisasi. " +
                "Aturan yang disetujui digunakan server untuk mengklasifikasikan hasil tes.",
            confirmLabel = "Setujui",
            onConfirm = viewModel::confirmApproval,
            onDismiss = viewModel::dismissApproval,
        )
    }
    uiState.pendingRetireRuleId?.let { ruleId ->
        val rule = uiState.rules.firstOrNull { r -> r.id == ruleId }
        ConfirmDialog(
            title = "Tarik aturan?",
            message = "Aturan ${rule?.ruleVersion ?: ""} tidak akan lagi menjadi aturan efektif. " +
                "Hasil yang sudah diklasifikasikan tetap tersimpan.",
            confirmLabel = "Tarik",
            destructive = true,
            onConfirm = viewModel::confirmRetire,
            onDismiss = viewModel::dismissRetire,
        )
    }
}

@Composable
private fun ProtocolSelector(uiState: RulesUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = uiState.protocols.firstOrNull { it.id == uiState.selectedProtocolId }
    Text("Protokol", style = MaterialTheme.typography.titleSmall)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selected?.let { "${it.name} (v${it.protocolVersion})" } ?: "Pilih protokol",
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.protocols.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text("${protocol.name} (v${protocol.protocolVersion})") },
                    onClick = {
                        onSelect(protocol.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleVersionList(
    uiState: RulesUiState,
    onSelect: (FatigueRule) -> Unit,
    onNewDraft: () -> Unit,
) {
    Text("Versi aturan", style = MaterialTheme.typography.titleSmall)
    if (uiState.rules.isEmpty()) {
        Text(
            "Belum ada aturan untuk protokol ini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    uiState.rules.forEach { rule ->
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(rule) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rule.ruleVersion,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Berlaku dari ${TimeProvider.formatDateForDisplay(rule.effectiveFrom)}" +
                            (rule.effectiveUntil?.let { " s.d. ${TimeProvider.formatDateForDisplay(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (rule.approvedBy != null) {
                        Text(
                            text = "Disetujui: ${rule.approvedBy!!.take(8)}…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    color = when (rule.approvalStatus) {
                        ApprovalStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant
                        ApprovalStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
                        ApprovalStatus.RETIRED -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when (rule.approvalStatus) {
                        ApprovalStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
                        ApprovalStatus.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
                        ApprovalStatus.RETIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = approvalStatusLabel(rule.approvalStatus),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
    OutlinedButton(onClick = onNewDraft, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Aturan Baru")
    }
}

@Composable
private fun RuleEditorSection(uiState: RulesUiState, viewModel: RulesViewModel) {
    val editable = uiState.selectedRule == null || uiState.selectedRule?.approvalStatus == ApprovalStatus.DRAFT

    if (uiState.labelsNeedValidation) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Label kategori masih \"Perlu validasi\". Label harus divalidasi organisasi sebelum aturan disetujui.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (!editable) {
        Text(
            text = "Aturan berstatus ${approvalStatusLabel(uiState.selectedRule?.approvalStatus ?: ApprovalStatus.DRAFT)} tidak dapat diubah. " +
                "Buat versi baru untuk revisi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedTextField(
        value = uiState.version,
        onValueChange = viewModel::setVersion,
        label = { Text("Versi aturan") },
        enabled = editable,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Pita kategori (urut dari reaksi tercepat)", style = MaterialTheme.typography.titleSmall)
    uiState.bands.forEachIndexed { index, band ->
        BandEditorRow(
            index = index,
            band = band,
            enabled = editable,
            canRemove = uiState.bands.size > 1,
            onChange = { viewModel.updateBand(index, it) },
            onRemove = { viewModel.removeBand(index) },
        )
    }
    OutlinedButton(onClick = viewModel::addBand, enabled = editable) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Text("Tambah Pita")
    }

    NumericEditorField(
        label = "Ambang respons lambat (ms)",
        value = uiState.slowThreshold,
        enabled = editable,
        onValueChange = viewModel::setSlowThreshold,
    )
    LogicDropdown(
        logic = uiState.logic,
        enabled = editable,
        onSelect = viewModel::setLogic,
    )
    NumericEditorField(
        label = "Minimal percobaan valid",
        value = uiState.minValidTrials,
        enabled = editable,
        onValueChange = viewModel::setMinValidTrials,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = uiState.excludeFalseStarts,
            onCheckedChange = { viewModel.setExcludeFalseStarts(it) },
            enabled = editable,
        )
        Text("Keluarkan false start dari statistik", style = MaterialTheme.typography.bodyMedium)
    }
    NumericEditorField(
        label = "Keluarkan reaksi di atas (ms, kosong = tanpa batas)",
        value = uiState.excludeAbove,
        enabled = editable,
        onValueChange = viewModel::setExcludeAbove,
    )

    // Collapsed JSON preview of the rule configuration payload.
    OutlinedButton(onClick = viewModel::toggleJson) {
        Icon(Icons.Rounded.Code, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(if (uiState.jsonExpanded) "Sembunyikan Pratinjau JSON" else "Pratinjau JSON")
    }
    if (uiState.jsonExpanded) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val previewConfig = buildPreviewConfig(uiState)
            Text(
                text = previewConfig?.toJson() ?: "(Perbaiki isian pita untuk melihat pratinjau JSON)",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::saveDraft, enabled = editable && !uiState.saving) {
            Text(if (uiState.saving) "Menyimpan..." else "Simpan Draft")
        }
        val selected = uiState.selectedRule
        if (selected?.approvalStatus == ApprovalStatus.DRAFT) {
            Button(onClick = { viewModel.requestApproval(selected.id) }) {
                Text("Setujui")
            }
        }
        if (selected?.approvalStatus == ApprovalStatus.APPROVED) {
            OutlinedButton(onClick = { viewModel.requestRetire(selected.id) }) {
                Text("Tarik")
            }
        }
    }
}

private fun buildPreviewConfig(uiState: RulesUiState): RuleConfig? {
    val bands = uiState.bands.mapNotNull { field ->
        val min = field.minMs.toLongOrNull() ?: return@mapNotNull null
        val max = if (field.maxMsExclusive.isBlank()) null else field.maxMsExclusive.toLongOrNull()
        val severity = field.severity.toIntOrNull() ?: return@mapNotNull null
        RuleBand(
            minMs = min,
            maxMsExclusive = max,
            code = field.code.ifBlank { "?" },
            label = field.label.ifBlank { "?" },
            severity = severity,
        )
    }
    return RuleConfig(
        bands = bands,
        slowResponseThresholdMs = uiState.slowThreshold.toLongOrNull() ?: 580L,
        logic = uiState.logic,
        minValidTrials = uiState.minValidTrials.toIntOrNull() ?: 4,
        excludeFalseStarts = uiState.excludeFalseStarts,
        excludeReactionAboveMs = uiState.excludeAbove.toLongOrNull(),
    )
}

@Composable
private fun BandEditorRow(
    index: Int,
    band: BandField,
    enabled: Boolean,
    canRemove: Boolean,
    onChange: (BandField) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumericField(
                    label = "Min",
                    value = band.minMs,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { onChange(band.copy(minMs = it)) }
                SmallNumericField(
                    label = "Maks (<)",
                    value = band.maxMsExclusive,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { onChange(band.copy(maxMsExclusive = it)) }
                SmallNumericField(
                    label = "Severity",
                    value = band.severity,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { onChange(band.copy(severity = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = band.code,
                    onValueChange = { onChange(band.copy(code = it)) },
                    label = { Text("Kode") },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = band.label,
                    onValueChange = { onChange(band.copy(label = it)) },
                    label = { Text("Label") },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.weight(1.5f),
                )
                if (canRemove) {
                    TextButton(onClick = onRemove, enabled = enabled) { Text("Hapus") }
                }
            }
            Text(
                text = "Pita ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmallNumericField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun NumericEditorField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LogicDropdown(
    logic: ClassificationLogic,
    enabled: Boolean,
    onSelect: (ClassificationLogic) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Logika: ${logicLabel(logic)}", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ClassificationLogic.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(logicLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
