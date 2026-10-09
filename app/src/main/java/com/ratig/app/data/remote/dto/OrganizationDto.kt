package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.WorkArea
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime

@Serializable
data class DepartmentDto(
    /** Server-generated uuid; null (omitted) on INSERT. */
    val id: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("active_status") val activeStatus: Boolean = true,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
) {
    companion object
}

fun DepartmentDto.toDomain(): Department = Department(
    id = requireNotNull(id) { "Department row without id" },
    name = name,
    description = description,
    activeStatus = activeStatus,
)

/** id null (omitted) lets the server generate the uuid on INSERT. */
fun DepartmentDto.Companion.fromDomain(department: Department): DepartmentDto = DepartmentDto(
    id = department.id.takeIf { it.isNotBlank() },
    name = department.name.trim(),
    description = department.description?.trim()?.takeIf { it.isNotEmpty() },
    activeStatus = department.activeStatus,
)

@Serializable
data class WorkAreaDto(
    /** Server-generated uuid; null (omitted) on INSERT. */
    val id: String? = null,
    @SerialName("department_id") val departmentId: String,
    val name: String,
    val description: String? = null,
    @SerialName("active_status") val activeStatus: Boolean = true,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
) {
    companion object
}

fun WorkAreaDto.toDomain(): WorkArea = WorkArea(
    id = requireNotNull(id) { "Work area row without id" },
    departmentId = departmentId,
    name = name,
    description = description,
    activeStatus = activeStatus,
)

fun WorkAreaDto.Companion.fromDomain(area: WorkArea): WorkAreaDto = WorkAreaDto(
    id = area.id.takeIf { it.isNotBlank() },
    departmentId = area.departmentId,
    name = area.name.trim(),
    description = area.description?.trim()?.takeIf { it.isNotEmpty() },
    activeStatus = area.activeStatus,
)

/**
 * `shifts` row. Postgres `time` columns are exchanged as plain strings
 * ("07:00:00") and parsed to [java.time.LocalTime] in the domain mapping.
 */
@Serializable
data class ShiftDto(
    /** Server-generated uuid; null (omitted) on INSERT. */
    val id: String? = null,
    val name: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val timezone: String = "Asia/Jakarta",
    @SerialName("active_status") val activeStatus: Boolean = true,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
) {
    companion object
}

fun ShiftDto.toDomain(): Shift = Shift(
    id = requireNotNull(id) { "Shift row without id" },
    name = name,
    startTime = LocalTime.parse(startTime),
    endTime = LocalTime.parse(endTime),
    timezone = timezone,
    activeStatus = activeStatus,
)

fun ShiftDto.Companion.fromDomain(shift: Shift): ShiftDto = ShiftDto(
    id = shift.id.takeIf { it.isNotBlank() },
    name = shift.name.trim(),
    startTime = shift.startTime.toString(),
    endTime = shift.endTime.toString(),
    timezone = shift.timezone,
    activeStatus = shift.activeStatus,
)
