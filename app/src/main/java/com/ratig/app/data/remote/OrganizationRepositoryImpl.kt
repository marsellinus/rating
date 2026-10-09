package com.ratig.app.data.remote

import com.ratig.app.core.result.AppError
import com.ratig.app.core.result.AppResult
import com.ratig.app.data.remote.dto.DepartmentDto
import com.ratig.app.data.remote.dto.ShiftDto
import com.ratig.app.data.remote.dto.WorkAreaDto
import com.ratig.app.data.remote.dto.fromDomain
import com.ratig.app.data.remote.dto.toDomain
import com.ratig.app.domain.model.Department
import com.ratig.app.domain.model.Shift
import com.ratig.app.domain.model.WorkArea
import com.ratig.app.domain.repository.OrganizationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/**
 * PostgREST-backed master data management for departments, work areas and
 * shifts. `upsert*` semantics: a blank id inserts (server generates the
 * uuid), a present id patches the existing row.
 */
class OrganizationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : OrganizationRepository {

    override suspend fun listDepartments(activeOnly: Boolean): AppResult<List<Department>> =
        AppResult.of {
            supabase.postgrest["departments"].select {
                if (activeOnly) filter { eq("active_status", true) }
                order("name", Order.ASCENDING)
            }.decodeList<DepartmentDto>().map { it.toDomain() }
        }

    override suspend fun upsertDepartment(department: Department): AppResult<Department> =
        persist("Departemen") {
            val dto = if (department.id.isBlank()) {
                supabase.postgrest["departments"]
                    .insert(DepartmentDto.fromDomain(department)) { select() }
                    .decodeSingle<DepartmentDto>()
            } else {
                supabase.postgrest["departments"].update(
                    update = {
                        set("name", department.name.trim())
                        set("description", department.description?.trim()?.takeIf { it.isNotEmpty() })
                        set("active_status", department.activeStatus)
                    },
                    request = {
                        filter { eq("id", department.id) }
                        select()
                    },
                ).decodeSingleOrNull<DepartmentDto>()
            }
            dto?.toDomain()
        }

    override suspend fun listWorkAreas(
        departmentId: String?,
        activeOnly: Boolean,
    ): AppResult<List<WorkArea>> = AppResult.of {
        supabase.postgrest["work_areas"].select {
            filter {
                if (departmentId != null) eq("department_id", departmentId)
                if (activeOnly) eq("active_status", true)
            }
            order("name", Order.ASCENDING)
        }.decodeList<WorkAreaDto>().map { it.toDomain() }
    }

    override suspend fun upsertWorkArea(area: WorkArea): AppResult<WorkArea> =
        persist("Area kerja") {
            val dto = if (area.id.isBlank()) {
                supabase.postgrest["work_areas"]
                    .insert(WorkAreaDto.fromDomain(area)) { select() }
                    .decodeSingle<WorkAreaDto>()
            } else {
                supabase.postgrest["work_areas"].update(
                    update = {
                        set("department_id", area.departmentId)
                        set("name", area.name.trim())
                        set("description", area.description?.trim()?.takeIf { it.isNotEmpty() })
                        set("active_status", area.activeStatus)
                    },
                    request = {
                        filter { eq("id", area.id) }
                        select()
                    },
                ).decodeSingleOrNull<WorkAreaDto>()
            }
            dto?.toDomain()
        }

    override suspend fun listShifts(activeOnly: Boolean): AppResult<List<Shift>> = AppResult.of {
        supabase.postgrest["shifts"].select {
            if (activeOnly) filter { eq("active_status", true) }
            order("start_time", Order.ASCENDING)
        }.decodeList<ShiftDto>().map { it.toDomain() }
    }

    override suspend fun upsertShift(shift: Shift): AppResult<Shift> = persist("Shift") {
        val dto = if (shift.id.isBlank()) {
            supabase.postgrest["shifts"]
                .insert(ShiftDto.fromDomain(shift)) { select() }
                .decodeSingle<ShiftDto>()
        } else {
            supabase.postgrest["shifts"].update(
                update = {
                    set("name", shift.name.trim())
                    set("start_time", shift.startTime.toString())
                    set("end_time", shift.endTime.toString())
                    set("timezone", shift.timezone)
                    set("active_status", shift.activeStatus)
                },
                request = {
                    filter { eq("id", shift.id) }
                    select()
                },
            ).decodeSingleOrNull<ShiftDto>()
        }
        dto?.toDomain()
    }

    /**
     * Shared insert-or-patch flow. `null` from the block means the target row
     * vanished (or RLS hid it) - surfaced as a friendly validation error
     * instead of a bare success.
     */
    private suspend fun <T> persist(label: String, block: suspend () -> T?): AppResult<T> {
        val result = AppResult.of { block() }
        return when (result) {
            is AppResult.Success -> result.value?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Validation("$label tidak ditemukan atau sudah dihapus."))
            is AppResult.Failure -> result
        }
    }
}
