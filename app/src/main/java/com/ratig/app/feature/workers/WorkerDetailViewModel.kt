package com.ratig.app.feature.workers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import com.ratig.app.domain.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Worker profile + examination history + "Mulai Tes" (examiner) flow.
 */
@HiltViewModel
class WorkerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val organizationRepository: OrganizationRepository,
    private val protocolRepository: ProtocolRepository,
    private val testSessionRepository: TestSessionRepository,
) : ViewModel() {

    /** One examination history row: date + classification (or status fallback). */
    data class HistoryRowUi(
        val sessionId: String,
        val dateLabel: String,
        /** Classification label from the finalized result; null before finalization. */
        val classificationLabel: String?,
        /** Status fallback shown while the session has no result yet. */
        val statusLabel: String,
    )

    data class StartTestUiState(
        val creating: Boolean = false,
        /** Set once the session row is created; the screen navigates on it. */
        val sessionId: String? = null,
        val error: String? = null,
    )

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val worker: Worker? = null,
        val history: List<HistoryRowUi> = emptyList(),
        val historyLoading: Boolean = false,
        val optionsLoading: Boolean = false,
        val protocols: List<TestProtocol> = emptyList(),
        val shifts: List<Shift> = emptyList(),
        val startTest: StartTestUiState = StartTestUiState(),
    )

    private val workerId: String = savedStateHandle.get<String>("workerId").orEmpty()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    fun loadTestOptions() {
        if (_uiState.value.optionsLoading) return
        _uiState.update { it.copy(optionsLoading = true) }
        viewModelScope.launch {
            coroutineScope {
                val protocolsDeferred = async { protocolRepository.listActiveProtocols() }
                val shiftsDeferred = async { organizationRepository.listShifts(activeOnly = true) }
                val protocolsResult = protocolsDeferred.await()
                val shiftsResult = shiftsDeferred.await()
                _uiState.update { current ->
                    current.copy(
                        optionsLoading = false,
                        protocols = when (protocolsResult) {
                            is AppResult.Success -> protocolsResult.value
                            is AppResult.Failure -> current.protocols
                        },
                        shifts = when (shiftsResult) {
                            is AppResult.Success -> shiftsResult.value
                            is AppResult.Failure -> current.shifts
                        },
                    )
                }
            }
        }
    }

    /**
     * Creates the session row (status CREATED, no fatigue rule - the approved
     * rule is applied server-side at finalization) then hands the session id
     * to the screen via [UiState.startTest.sessionId].
     */
    fun startTest(protocolId: String, shiftId: String?) {
        if (workerId.isBlank()) return
        if (_uiState.value.startTest.creating) return
        _uiState.update { it.copy(startTest = StartTestUiState(creating = true)) }
        viewModelScope.launch {
            val result = testSessionRepository.createSession(
                workerId = workerId,
                protocolId = protocolId,
                fatigueRuleId = null,
                shiftId = shiftId,
            )
            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(startTest = StartTestUiState(sessionId = result.value.id))
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        startTest = StartTestUiState(error = result.error.userMessage),
                    )
                }
            }
        }
    }

    private fun load() {
        if (workerId.isBlank()) {
            _uiState.update { it.copy(error = "Pekerja tidak ditemukan.") }
            return
        }
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val workerResult = workerRepository.get(workerId)
            when (workerResult) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(loading = false, worker = workerResult.value, error = null) }
                    loadHistory()
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, error = workerResult.error.userMessage)
                }
            }
        }
    }

    private fun loadHistory() {
        _uiState.update { it.copy(historyLoading = true) }
        viewModelScope.launch {
            val result = workerRepository.examinationHistory(
                workerId = workerId,
                limit = HISTORY_LIMIT,
                offset = 0,
            )
            when (result) {
                is AppResult.Success -> {
                    val rows = fetchClassificationLabels(result.value)
                    _uiState.update { it.copy(historyLoading = false, history = rows) }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(historyLoading = false)
                }
            }
        }
    }

    /**
     * `v_session_overview` carries the classification, but the domain
     * [TestSession] does not - fetch the persisted result per session
     * (bounded by HISTORY_LIMIT) to show the classification label.
     */
    private suspend fun fetchClassificationLabels(sessions: List<TestSession>): List<HistoryRowUi> =
        coroutineScope {
            sessions.map { session ->
                async {
                    val label = when (val result = testSessionRepository.getResult(session.id)) {
                        is AppResult.Success -> result.value?.classificationLabel
                        is AppResult.Failure -> null
                    }
                    HistoryRowUi(
                        sessionId = session.id,
                        dateLabel = formatDateLabel(session),
                        classificationLabel = label,
                        statusLabel = statusLabel(session.sessionStatus),
                    )
                }
            }.awaitAll()
        }

    private fun formatDateLabel(session: TestSession): String {
        val instant: Instant = session.completedAt ?: session.createdAt ?: return "—"
        return instant.atZone(SERVER_ZONE).format(HISTORY_DATE_FORMAT)
    }

    private fun statusLabel(status: SessionStatus): String = when (status) {
        SessionStatus.CREATED -> "Menunggu pemeriksaan"
        SessionStatus.IN_PROGRESS -> "Sedang diperiksa"
        SessionStatus.INTERRUPTED -> "Terputus"
        SessionStatus.PENDING_SYNC -> "Menunggu sinkronisasi"
        SessionStatus.FINALIZED -> "Selesai"
        SessionStatus.FAILED -> "Gagal"
    }

    private companion object {
        const val HISTORY_LIMIT = 10

        val SERVER_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

        val HISTORY_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy • HH:mm", Locale.forLanguageTag("id-ID"))
    }
}
