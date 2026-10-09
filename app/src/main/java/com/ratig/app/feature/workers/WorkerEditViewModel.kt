package com.ratig.app.feature.workers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.core.result.AppError
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.result.onFailure
import com.ratig.app.core.result.onSuccess
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.WorkArea
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Create/edit form for a worker. The optional `workerId` nav argument decides
 * between create and update mode; work areas follow the selected department.
 */
@HiltViewModel
class WorkerEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workerRepository: WorkerRepository,
    private val organizationRepository: OrganizationRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val saving: Boolean = false,
        val isNew: Boolean = true,
        val error: String? = null,
        // Form fields
        val employeeNumber: String = "",
        val fullName: String = "",
        val email: String = "",
        val jobTitle: String = "",
        val departmentId: String? = null,
        val workAreaId: String? = null,
        val shiftId: String? = null,
        val activeStatus: Boolean = true,
        // Options
        val departments: List<Department> = emptyList(),
        val workAreas: List<WorkArea> = emptyList(),
        val workAreasLoading: Boolean = false,
        val shifts: List<Shift> = emptyList(),
        // Inline validation (Indonesian)
        val employeeNumberError: String? = null,
        val fullNameError: String? = null,
        val emailError: String? = null,
    )

    private val workerId: String? = savedStateHandle.get<String>("workerId")

    private val _uiState = MutableStateFlow(UiState(isNew = workerId == null))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEmployeeNumberChange(value: String) =
        _uiState.update { it.copy(employeeNumber = value, employeeNumberError = null) }

    fun onFullNameChange(value: String) =
        _uiState.update { it.copy(fullName = value, fullNameError = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null) }

    fun onJobTitleChange(value: String) =
        _uiState.update { it.copy(jobTitle = value) }

    fun onDepartmentSelected(departmentId: String?) {
        if (_uiState.value.departmentId == departmentId) return
        _uiState.update { it.copy(departmentId = departmentId, workAreaId = null) }
        loadWorkAreas(departmentId)
    }

    fun onWorkAreaSelected(workAreaId: String?) =
        _uiState.update { it.copy(workAreaId = workAreaId) }

    fun onShiftSelected(shiftId: String?) =
        _uiState.update { it.copy(shiftId = shiftId) }

    /** Persists the form. Calls [onDone] exactly once on success. */
    fun save(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.saving || state.loading) return

        val employeeNumberError = if (state.employeeNumber.isBlank()) {
            "Nomor induk pekerja wajib diisi."
        } else {
            null
        }
        val fullNameError = if (state.fullName.isBlank()) {
            "Nama lengkap wajib diisi."
        } else {
            null
        }
        val trimmedEmail = state.email.trim()
        val emailError = if (trimmedEmail.isNotEmpty() && !EMAIL_REGEX.matches(trimmedEmail)) {
            "Format email tidak valid."
        } else {
            null
        }
        if (employeeNumberError != null || fullNameError != null || emailError != null) {
            _uiState.update {
                it.copy(
                    employeeNumberError = employeeNumberError,
                    fullNameError = fullNameError,
                    emailError = emailError,
                )
            }
            return
        }

        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val current = _uiState.value
            val worker = Worker(
                id = workerId.orEmpty(),
                employeeNumber = current.employeeNumber.trim(),
                fullName = current.fullName.trim(),
                email = trimmedEmail.takeIf { it.isNotEmpty() },
                departmentId = current.departmentId,
                workAreaId = current.workAreaId,
                jobTitle = current.jobTitle.trim().takeIf { it.isNotEmpty() },
                shiftId = current.shiftId,
                activeStatus = current.activeStatus,
            )
            val result = if (current.isNew) {
                workerRepository.create(worker)
            } else {
                workerRepository.update(worker)
            }
            result
                .onSuccess {
                    _uiState.update { it.copy(saving = false, error = null) }
                    onDone()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(saving = false, error = error.userMessage) }
                }
        }
    }

    /** Soft-deletes (active_status = false). Confirm in the UI first. */
    fun deactivate() = setActive(false)

    /** Restores a deactivated worker (active_status = true). */
    fun reactivate() = setActive(true)

    private fun setActive(active: Boolean) {
        val id = workerId ?: return
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (active) workerRepository.reactivate(id) else workerRepository.deactivate(id)
            result
                .onSuccess {
                    _uiState.update { it.copy(saving = false, activeStatus = active) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(saving = false, error = error.userMessage) }
                }
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val departmentsDeferred = async { organizationRepository.listDepartments(activeOnly = true) }
                    val shiftsDeferred = async { organizationRepository.listShifts(activeOnly = true) }
                    val existing = workerId?.let { workerRepository.get(it) }

                    val departments: List<Department> = when (val departmentsResult = departmentsDeferred.await()) {
                        is AppResult.Success -> departmentsResult.value
                        is AppResult.Failure -> emptyList()
                    }
                    val shifts: List<Shift> = when (val shiftsResult = shiftsDeferred.await()) {
                        is AppResult.Success -> shiftsResult.value
                        is AppResult.Failure -> emptyList()
                    }
                    when (existing) {
                        null -> _uiState.update {
                            it.copy(loading = false, departments = departments, shifts = shifts)
                        }

                        is AppResult.Success -> {
                            val worker = existing.value
                            _uiState.update {
                                it.copy(
                                    loading = false,
                                    isNew = false,
                                    error = null,
                                    employeeNumber = worker.employeeNumber,
                                    fullName = worker.fullName,
                                    email = worker.email.orEmpty(),
                                    jobTitle = worker.jobTitle.orEmpty(),
                                    departmentId = worker.departmentId,
                                    workAreaId = worker.workAreaId,
                                    shiftId = worker.shiftId,
                                    activeStatus = worker.activeStatus,
                                    departments = departments,
                                    shifts = shifts,
                                )
                            }
                            loadWorkAreas(worker.departmentId)
                        }

                        is AppResult.Failure -> _uiState.update {
                            it.copy(
                                loading = false,
                                error = existing.error.userMessage,
                                departments = departments,
                                shifts = shifts,
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = AppError.Unexpected().userMessage) }
            }
        }
    }

    private fun loadWorkAreas(departmentId: String?) {
        if (departmentId == null) {
            _uiState.update { it.copy(workAreas = emptyList(), workAreasLoading = false) }
            return
        }
        _uiState.update { it.copy(workAreasLoading = true) }
        viewModelScope.launch {
            val result = organizationRepository.listWorkAreas(departmentId = departmentId, activeOnly = true)
            _uiState.update { current ->
                when (result) {
                    is AppResult.Success -> current.copy(
                        workAreas = result.value,
                        workAreasLoading = false,
                    )

                    is AppResult.Failure -> current.copy(workAreasLoading = false)
                }
            }
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    }
}
