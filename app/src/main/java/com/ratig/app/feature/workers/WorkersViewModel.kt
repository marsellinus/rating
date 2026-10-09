package com.ratig.app.feature.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.model.WorkerFilter
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Worker list: free-text search (300 ms debounce), department / shift /
 * status filter chips and cursor-less range pagination.
 */
@HiltViewModel
class WorkersViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val organizationRepository: OrganizationRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val workers: List<Worker> = emptyList(),
        val query: String = "",
        val departments: List<Department> = emptyList(),
        val shifts: List<Shift> = emptyList(),
        val selectedDepartmentId: String? = null,
        val selectedShiftId: String? = null,
        val activeOnly: Boolean = true,
        /** True when the last page returned fewer rows than the page size. */
        val endReached: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    /** Stale-response guard: only the latest list request may write state. */
    private var listGeneration = 0

    init {
        loadFilterOptions()
        refresh()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            refresh()
        }
    }

    fun selectDepartment(departmentId: String?) {
        if (_uiState.value.selectedDepartmentId == departmentId) return
        _uiState.update { it.copy(selectedDepartmentId = departmentId) }
        refresh()
    }

    fun selectShift(shiftId: String?) {
        if (_uiState.value.selectedShiftId == shiftId) return
        _uiState.update { it.copy(selectedShiftId = shiftId) }
        refresh()
    }

    fun toggleActiveOnly() {
        _uiState.update { it.copy(activeOnly = !it.activeOnly) }
        refresh()
    }

    fun refresh() {
        val generation = ++listGeneration
        _uiState.update { it.copy(loading = it.workers.isEmpty(), error = null) }
        viewModelScope.launch {
            val state = _uiState.value
            val result = workerRepository.list(
                WorkerFilter(
                    query = state.query.trim().takeIf { it.isNotEmpty() },
                    departmentId = state.selectedDepartmentId,
                    shiftId = state.selectedShiftId,
                    activeOnly = state.activeOnly,
                    limit = PAGE_SIZE,
                    offset = 0,
                ),
            )
            if (generation != listGeneration) return@launch
            result
                .onSuccess { workers ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            workers = workers,
                            endReached = workers.size < PAGE_SIZE,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.userMessage) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || state.endReached || state.workers.isEmpty()) return
        _uiState.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            val result = workerRepository.list(
                WorkerFilter(
                    query = state.query.trim().takeIf { it.isNotEmpty() },
                    departmentId = state.selectedDepartmentId,
                    shiftId = state.selectedShiftId,
                    activeOnly = state.activeOnly,
                    limit = PAGE_SIZE,
                    offset = state.workers.size,
                ),
            )
            result
                .onSuccess { more ->
                    _uiState.update { current ->
                        val fresh = more.filter { row -> current.workers.none { it.id == row.id } }
                        current.copy(
                            loadingMore = false,
                            workers = current.workers + fresh,
                            endReached = more.size < PAGE_SIZE,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loadingMore = false, error = error.userMessage) }
                }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val departments = organizationRepository.listDepartments(activeOnly = true)
            val shifts = organizationRepository.listShifts(activeOnly = true)
            _uiState.update { current ->
                current.copy(
                    departments = when (departments) {
                        is AppResult.Success -> departments.value
                        is AppResult.Failure -> current.departments
                    },
                    shifts = when (shifts) {
                        is AppResult.Success -> shifts.value
                        is AppResult.Failure -> current.shifts
                    },
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
