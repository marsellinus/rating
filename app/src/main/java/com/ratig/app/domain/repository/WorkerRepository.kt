package com.ratig.app.domain.repository

import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.WorkArea
import com.ratig.app.domain.model.Worker
import com.ratig.app.domain.model.WorkerFilter

interface WorkerRepository {
    suspend fun list(filter: WorkerFilter): AppResult<List<Worker>>
    suspend fun get(id: String): AppResult<Worker>
    /** Exact-match NIK lookup (server when online, cache when offline). */
    suspend fun findByNik(nik: String): AppResult<Worker?>
    suspend fun create(worker: Worker): AppResult<Worker>
    suspend fun update(worker: Worker): AppResult<Worker>
    /** Soft delete: sets active_status = false; history stays intact. */
    suspend fun deactivate(id: String): AppResult<Unit>
    suspend fun reactivate(id: String): AppResult<Unit>
    /** Worker linked to the current auth user (self-service role). */
    suspend fun getMyRecord(): AppResult<Worker?>
    suspend fun examinationHistory(workerId: String, limit: Int, offset: Int): AppResult<List<com.ratig.app.domain.model.TestSession>>
}

interface OrganizationRepository {
    suspend fun listDepartments(activeOnly: Boolean = false): AppResult<List<Department>>
    suspend fun upsertDepartment(department: Department): AppResult<Department>
    suspend fun listWorkAreas(departmentId: String? = null, activeOnly: Boolean = false): AppResult<List<WorkArea>>
    suspend fun upsertWorkArea(area: WorkArea): AppResult<WorkArea>
    suspend fun listShifts(activeOnly: Boolean = false): AppResult<List<Shift>>
    suspend fun upsertShift(shift: Shift): AppResult<Shift>
}
