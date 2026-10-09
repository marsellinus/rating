package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.Worker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Flat `workers` row.
 *
 * Deliberately NO nested foreign-table joins: the repository enriches the
 * display names (department/work area/shift) locally from cached organization
 * lists. This keeps decoding simple and avoids nested serialization issues.
 *
 * Nullable fields have `= null` defaults so the PostgREST serializer's
 * `encodeDefaults = false` omits them from INSERT bodies (server defaults /
 * NULL apply).
 */
@Serializable
data class WorkerDto(
    /** Server-generated uuid; null (omitted) on INSERT. */
    val id: String? = null,
    @SerialName("employee_number") val employeeNumber: String,
    @SerialName("full_name") val fullName: String,
    /**
     * Business key (`workers.nik`, digits only, unique). Nullable so INSERT
     * bodies omit it (the column has a server-side policy); SELECTs populate
     * it and the offline worker cache indexes it.
     */
    val nik: String? = null,
    val email: String? = null,
    @SerialName("department_id") val departmentId: String? = null,
    @SerialName("work_area_id") val workAreaId: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("active_status") val activeStatus: Boolean = true,
    @SerialName("user_id") val userId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant? = null,
) {
    companion object
}

fun WorkerDto.toDomain(
    departmentName: String? = null,
    workAreaName: String? = null,
    shiftName: String? = null,
): Worker = Worker(
    id = requireNotNull(id) { "Worker row without id" },
    employeeNumber = employeeNumber,
    fullName = fullName,
    email = email,
    departmentId = departmentId,
    workAreaId = workAreaId,
    jobTitle = jobTitle,
    shiftId = shiftId,
    activeStatus = activeStatus,
    userId = userId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    departmentName = departmentName,
    workAreaName = workAreaName,
    shiftName = shiftName,
)

/**
 * Builds the INSERT payload. The client never generates worker ids - the row
 * id always comes from the server (`gen_random_uuid()`), so [Worker.id] is
 * intentionally ignored (blank or not).
 */
fun WorkerDto.Companion.fromDomain(worker: Worker): WorkerDto = WorkerDto(
    id = null,
    employeeNumber = worker.employeeNumber.trim(),
    fullName = worker.fullName.trim(),
    email = worker.email?.trim()?.takeIf { it.isNotEmpty() },
    departmentId = worker.departmentId,
    workAreaId = worker.workAreaId,
    jobTitle = worker.jobTitle?.trim()?.takeIf { it.isNotEmpty() },
    shiftId = worker.shiftId,
    activeStatus = worker.activeStatus,
    userId = worker.userId,
)
