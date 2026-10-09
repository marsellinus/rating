package com.ratig.app.feature.identification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.identity.NikMask
import com.ratig.app.core.identity.NikValidation
import com.ratig.app.core.identity.NikValidator
import com.ratig.app.core.identity.QrParseResult
import com.ratig.app.core.identity.QrPayload
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.sync.SyncStatusBus
import com.ratig.app.data.offline.NetworkMonitor
import com.ratig.app.data.offline.OfflinePolicyStore
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.repository.DashboardRepository
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.domain.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One worker match, with everything the confirm card needs (privacy-masked). */
data class WorkerCandidate(
    val worker: Worker,
    /** The NIK that matched; in-memory only, never logged, shown masked. */
    val matchedNik: String,
    val maskedNik: String,
    /** true when the match came from the on-device cache (offline) vs live server. */
    val fromCache: Boolean,
    /** Cache row timestamp for the "Cache — diperbarui HH:mm" badge. */
    val cacheUpdatedAt: Instant?,
)

sealed interface IdentificationSearch {
    data object Idle : IdentificationSearch
    data object Searching : IdentificationSearch
    data class Found(val candidate: WorkerCandidate) : IdentificationSearch
    data object NotFound : IdentificationSearch
    data class Failed(val message: String) : IdentificationSearch
}

data class IdentificationUiState(
    val isOnline: Boolean = true,
    val nikMinLength: Int = NikValidator.DEFAULT_MIN_LENGTH,
    val nikMaxLength: Int = NikValidator.DEFAULT_MAX_LENGTH,
    val qrPrefix: String = QrPayload.DEFAULT_PREFIX,
    val nikInput: String = "",
    val nikError: String? = null,
    val search: IdentificationSearch = IdentificationSearch.Idle,
    /** Worker explicitly confirmed via "Konfirmasi Pekerja" — gates session setup. */
    val confirmedWorker: WorkerCandidate? = null,
    /** Last examination summary for the autofill card (null = none/unknown). */
    val lastExam: TestSession? = null,
    val protocols: List<TestProtocol> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val selectedProtocolId: String? = null,
    /** null = use the worker's own shift. */
    val selectedShiftId: String? = null,
    val loadingSetup: Boolean = false,
    val setupError: String? = null,
    val creatingSession: Boolean = false,
    val todayExamCount: Long? = null,
    val unsyncedSessions: Int = 0,
    /** Found a different worker while an unprocessed confirmation is active. */
    val switchCandidate: WorkerCandidate? = null,
    /** Returning to the screen with an unprocessed confirmation still active. */
    val showResumePrompt: Boolean = false,
    /** One-shot: session created, Route must call onSessionReady. */
    val sessionReady: String? = null,
    /** One-shot snackbar message (scan problems etc.). */
    val transientMessage: String? = null,
)

/**
 * Worker identification by NIK: manual input (debounced lookup) or camera scan.
 *
 * Hard rules enforced here (CONTRACT-2 §Identification):
 *  - workers are NEVER auto-created by input/scan — a failed lookup only offers
 *    manual re-search or explicit registration via WorkerEditRoute;
 *  - the NIK lives in memory only, never logged, never placed in URLs;
 *  - sessions store a MASKED NIK snapshot (`nikSnapshot`), the workers row
 *    (referenced by UUID) remains the authoritative identity.
 */
@HiltViewModel
class IdentificationViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val protocolRepository: ProtocolRepository,
    private val organizationRepository: OrganizationRepository,
    private val sessionRepository: TestSessionRepository,
    private val dashboardRepository: DashboardRepository,
    private val networkMonitor: NetworkMonitor,
    private val policyStore: OfflinePolicyStore,
    private val syncStatusBus: SyncStatusBus,
    private val nikValidator: NikValidator,
    private val qrPayload: QrPayload,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentificationUiState())
    val uiState: StateFlow<IdentificationUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** Set once createSession succeeded; drives the fresh-identification reset. */
    private var sessionCreated = false

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            syncStatusBus.snapshot.collect { snapshot ->
                _uiState.update { it.copy(unsyncedSessions = snapshot.pendingSessions) }
            }
        }
        viewModelScope.launch {
            policyStore.nikMinLength.collect { v ->
                _uiState.update { it.copy(nikMinLength = v) }
            }
        }
        viewModelScope.launch {
            policyStore.nikMaxLength.collect { v ->
                _uiState.update { it.copy(nikMaxLength = v) }
            }
        }
        viewModelScope.launch {
            policyStore.qrPrefix.collect { v ->
                _uiState.update { it.copy(qrPrefix = v) }
            }
        }
        refreshCounters()
    }

    /**
     * Called every time the screen re-enters composition. A session that was
     * created (examiner came back after results) ALWAYS resets to a fresh
     * identification; an unprocessed confirmation asks what to do.
     */
    fun onScreenResumed() {
        if (sessionCreated) {
            resetIdentification()
        } else if (_uiState.value.confirmedWorker != null) {
            _uiState.update { it.copy(showResumePrompt = true) }
        }
    }

    fun refreshCounters() {
        viewModelScope.launch {
            val today = when (val r = dashboardRepository.examinerStats()) {
                is AppResult.Success -> r.value.sessionsToday
                is AppResult.Failure -> null
            }
            _uiState.update { it.copy(todayExamCount = today) }
        }
    }

    // ---------------------------------------------------------------------
    // NIK input + lookup
    // ---------------------------------------------------------------------

    fun onNikChange(raw: String) {
        val s = _uiState.value
        val sanitized = nikValidator.sanitize(raw, s.nikMaxLength)
        _uiState.update { it.copy(nikInput = sanitized) }
        scheduleSearch(immediate = false)
    }

    fun clearNik() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                nikInput = "",
                nikError = null,
                search = IdentificationSearch.Idle,
                lastExam = null,
            )
        }
    }

    private fun scheduleSearch(immediate: Boolean) {
        searchJob?.cancel()
        val s = _uiState.value
        val validation = nikValidator.validate(s.nikInput, s.nikMinLength, s.nikMaxLength)
        _uiState.update {
            it.copy(
                nikError = (validation as? NikValidation.Invalid)?.message,
                search = if (validation is NikValidation.Valid) it.search else IdentificationSearch.Idle,
            )
        }
        if (validation !is NikValidation.Valid) return
        val nik = s.nikInput
        searchJob = viewModelScope.launch {
            // 350ms debounce for typing; immediate for scans.
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            runSearch(nik)
        }
    }

    private suspend fun runSearch(nik: String) {
        _uiState.update { it.copy(search = IdentificationSearch.Searching) }
        val result = workerRepository.findByNik(nik)
        // Ignore stale results: the input moved on while we were looking.
        if (_uiState.value.nikInput != nik) return
        when (result) {
            is AppResult.Success -> {
                val worker = result.value
                if (worker == null) {
                    _uiState.update { it.copy(search = IdentificationSearch.NotFound) }
                } else {
                    val candidate = toCandidate(worker, nik)
                    val current = _uiState.value
                    val unprocessed = current.confirmedWorker
                    if (unprocessed != null && !sessionCreated && unprocessed.worker.id != worker.id) {
                        // Switching worker with an unsaved confirmation -> gate.
                        _uiState.update {
                            it.copy(
                                search = IdentificationSearch.Idle,
                                switchCandidate = candidate,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(search = IdentificationSearch.Found(candidate)) }
                        loadLastExam(worker.id)
                    }
                }
            }

            is AppResult.Failure -> _uiState.update {
                it.copy(search = IdentificationSearch.Failed(result.error.userMessage))
            }
        }
    }

    private fun toCandidate(worker: Worker, nik: String): WorkerCandidate {
        val offline = !_uiState.value.isOnline
        return WorkerCandidate(
            worker = worker,
            matchedNik = nik,
            maskedNik = NikMask.mask(nik),
            fromCache = offline,
            cacheUpdatedAt = worker.updatedAt,
        )
    }

    private fun loadLastExam(workerId: String) {
        viewModelScope.launch {
            val last = when (val r = workerRepository.examinationHistory(workerId, limit = 1, offset = 0)) {
                is AppResult.Success -> r.value.firstOrNull()
                is AppResult.Failure -> null
            }
            _uiState.update { it.copy(lastExam = last) }
        }
    }

    // ---------------------------------------------------------------------
    // Scan intake (QR with prefix, CODE_128 raw NIK)
    // ---------------------------------------------------------------------

    fun onScanResult(raw: String, isQr: Boolean) {
        val s = _uiState.value
        val nik: String = if (isQr) {
            when (val parsed = qrPayload.parse(raw, s.qrPrefix, s.nikMinLength, s.nikMaxLength)) {
                is QrParseResult.Valid -> parsed.nik
                is QrParseResult.InvalidNik -> {
                    _uiState.update { it.copy(transientMessage = parsed.message) }
                    return
                }

                QrParseResult.Unrecognized -> {
                    _uiState.update { it.copy(transientMessage = "QR tidak dikenali.") }
                    return
                }
            }
        } else {
            val value = nikValidator.sanitize(raw, s.nikMaxLength)
            when (nikValidator.validate(value, s.nikMinLength, s.nikMaxLength)) {
                is NikValidation.Valid -> value
                is NikValidation.Invalid -> {
                    _uiState.update { it.copy(transientMessage = "Isi barcode bukan NIK yang valid.") }
                    return
                }
            }
        }
        _uiState.update { it.copy(nikInput = nik) }
        scheduleSearch(immediate = true)
    }

    /** "Cari lagi" after NotFound / lookup failure: re-runs the lookup now. */
    fun retrySearch() {
        if (_uiState.value.nikInput.isNotEmpty()) scheduleSearch(immediate = true)
    }

    fun retrySetup() {
        _uiState.value.confirmedWorker?.let { loadSetup(it.worker) }
    }

    fun dismissTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    // ---------------------------------------------------------------------
    // Confirm gate + switch/resume dialogs
    // ---------------------------------------------------------------------

    fun confirmWorker() {
        val candidate = (_uiState.value.search as? IdentificationSearch.Found)?.candidate ?: return
        if (!candidate.worker.activeStatus) return // inactive workers are blocked
        _uiState.update { it.copy(confirmedWorker = candidate, setupError = null) }
        loadSetup(candidate.worker)
    }

    fun confirmSwitchWorker() {
        val candidate = _uiState.value.switchCandidate ?: return
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                switchCandidate = null,
                confirmedWorker = null,
                search = IdentificationSearch.Found(candidate),
                lastExam = null,
                selectedProtocolId = null,
                selectedShiftId = null,
                setupError = null,
            )
        }
        loadLastExam(candidate.worker.id)
    }

    fun dismissSwitchWorker() {
        val keep = _uiState.value.confirmedWorker
        _uiState.update {
            it.copy(
                switchCandidate = null,
                search = keep?.let { c -> IdentificationSearch.Found(c) } ?: IdentificationSearch.Idle,
            )
        }
    }

    fun keepCurrentWorker() {
        _uiState.update { it.copy(showResumePrompt = false) }
    }

    fun startFreshIdentification() {
        _uiState.update { it.copy(showResumePrompt = false) }
        resetIdentification()
    }

    private fun resetIdentification() {
        searchJob?.cancel()
        sessionCreated = false
        _uiState.update { current ->
            IdentificationUiState(
                isOnline = current.isOnline,
                nikMinLength = current.nikMinLength,
                nikMaxLength = current.nikMaxLength,
                qrPrefix = current.qrPrefix,
                todayExamCount = current.todayExamCount,
                unsyncedSessions = current.unsyncedSessions,
            )
        }
    }

    // ---------------------------------------------------------------------
    // Session setup: active protocol (+ optional shift) then createSession
    // ---------------------------------------------------------------------

    private fun loadSetup(worker: Worker) {
        _uiState.update { it.copy(loadingSetup = true, setupError = null) }
        viewModelScope.launch {
            val protocolsResult = protocolRepository.listActiveProtocols()
            val shiftsResult = organizationRepository.listShifts(activeOnly = true)
            val protocols = (protocolsResult as? AppResult.Success)?.value.orEmpty()
            val shifts = (shiftsResult as? AppResult.Success)?.value.orEmpty()
            _uiState.update {
                it.copy(
                    protocols = protocols,
                    shifts = shifts,
                    loadingSetup = false,
                    setupError = (protocolsResult as? AppResult.Failure)?.error?.userMessage,
                    selectedProtocolId = it.selectedProtocolId
                        ?.takeIf { id -> protocols.any { p -> p.id == id } }
                        ?: protocols.firstOrNull()?.id,
                    selectedShiftId = worker.shiftId
                        ?.takeIf { id -> shifts.any { sh -> sh.id == id } },
                )
            }
            resolveMissingNames(worker)
        }
    }

    /** Fills display names when the lookup returned a worker without joins. */
    private suspend fun resolveMissingNames(worker: Worker) {
        var departmentName = worker.departmentName
        var workAreaName = worker.workAreaName
        if (departmentName == null && worker.departmentId != null) {
            val deps = (organizationRepository.listDepartments() as? AppResult.Success)?.value.orEmpty()
            departmentName = deps.firstOrNull { it.id == worker.departmentId }?.name
        }
        if (workAreaName == null && worker.workAreaId != null) {
            val areas = (organizationRepository.listWorkAreas() as? AppResult.Success)?.value.orEmpty()
            workAreaName = areas.firstOrNull { it.id == worker.workAreaId }?.name
        }
        if (departmentName == worker.departmentName && workAreaName == worker.workAreaName) return
        val patched = worker.copy(departmentName = departmentName, workAreaName = workAreaName)
        _uiState.update { current ->
            val candidateOf: (WorkerCandidate) -> WorkerCandidate = { c ->
                if (c.worker.id == patched.id) c.copy(worker = patched) else c
            }
            current.copy(
                search = (current.search as? IdentificationSearch.Found)
                    ?.let { it.copy(candidate = candidateOf(it.candidate)) }
                    ?: current.search,
                confirmedWorker = current.confirmedWorker?.let(candidateOf),
            )
        }
    }

    fun selectProtocol(protocolId: String) {
        _uiState.update { it.copy(selectedProtocolId = protocolId) }
    }

    /** @param shiftId null = follow the worker's own shift. */
    fun selectShift(shiftId: String?) {
        _uiState.update { it.copy(selectedShiftId = shiftId) }
    }

    fun startSession() {
        val s = _uiState.value
        val candidate = s.confirmedWorker ?: return
        val protocolId = s.selectedProtocolId ?: return
        if (s.creatingSession) return
        _uiState.update { it.copy(creatingSession = true, setupError = null) }
        viewModelScope.launch {
            val fatigueRuleId = when (val r = protocolRepository.effectiveRule(protocolId)) {
                is AppResult.Success -> r.value?.id
                is AppResult.Failure -> null
            }
            val created = sessionRepository.createSession(
                workerId = candidate.worker.id,
                protocolId = protocolId,
                fatigueRuleId = fatigueRuleId,
                shiftId = s.selectedShiftId ?: candidate.worker.shiftId,
                testMode = TestMode.CLASSIC,
                randomSeed = null,
                modeConfig = null,
                // Privacy policy: audit snapshot keeps a MASKED NIK only; the
                // workers row (worker_id) is the authoritative identity.
                nikSnapshot = candidate.maskedNik,
            )
            when (created) {
                is AppResult.Success -> {
                    sessionCreated = true
                    _uiState.update {
                        it.copy(creatingSession = false, sessionReady = created.value.id)
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(creatingSession = false, setupError = created.error.userMessage)
                }
            }
        }
    }

    fun consumeSessionReady() {
        _uiState.update { it.copy(sessionReady = null) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
