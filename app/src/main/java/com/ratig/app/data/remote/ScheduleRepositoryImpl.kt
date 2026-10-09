package com.ratig.app.data.remote

import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.remote.dto.ScheduleDto
import com.ratig.app.data.remote.dto.ScheduleInsertDto
import com.ratig.app.domain.model.ScheduleEntry
import com.ratig.app.domain.repository.ScheduleRepository
import com.ratig.app.domain.model.ScheduleStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedule repository. Reads go through `v_schedule_overview`
 * (schedules.* + worker_name + examiner_name + shift_name); writes target the
 * `schedules` table directly.
 */
@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : ScheduleRepository {

    override suspend fun list(
        from: Instant,
        to: Instant,
        examinerId: String?,
    ): AppResult<List<ScheduleEntry>> = AppResult.of {
        supabase.postgrest[V_SCHEDULE_OVERVIEW].select {
            filter {
                gte("scheduled_at", from.toString())
                lt("scheduled_at", to.toString())
                if (examinerId != null) eq("examiner_id", examinerId)
            }
            order("scheduled_at", Order.ASCENDING)
        }.decodeList<ScheduleDto>().map { it.toDomain() }
    }

    override suspend fun listUpcoming(limit: Int): AppResult<List<ScheduleEntry>> = AppResult.of {
        supabase.postgrest[V_SCHEDULE_OVERVIEW].select {
            filter {
                gte("scheduled_at", TimeProvider.nowUtc().toString())
                isIn("status", listOf(SCHEDULED_RAW, NEEDS_REPEAT_RAW))
            }
            order("scheduled_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<ScheduleDto>().map { it.toDomain() }
    }

    override suspend fun create(entry: ScheduleEntry): AppResult<ScheduleEntry> = AppResult.of {
        supabase.postgrest[TABLE_SCHEDULES].insert(
            ScheduleInsertDto(
                workerId = entry.workerId,
                examinerId = entry.examinerId,
                shiftId = entry.shiftId,
                scheduledAt = entry.scheduledAt,
                status = entry.status.name.lowercase(),
                notes = entry.notes,
            ),
        ) { select() }.decodeSingle<ScheduleDto>().toDomain()
    }

    override suspend fun updateStatus(id: String, status: ScheduleStatus): AppResult<Unit> = AppResult.of {
        supabase.postgrest[TABLE_SCHEDULES].update({
            set("status", status.name.lowercase())
        }) {
            filter { eq("id", id) }
        }
        Unit
    }

    override suspend fun update(entry: ScheduleEntry): AppResult<ScheduleEntry> = AppResult.of {
        supabase.postgrest[TABLE_SCHEDULES].update({
            set("worker_id", entry.workerId)
            set("examiner_id", entry.examinerId)
            if (entry.shiftId != null) set("shift_id", entry.shiftId)
            set("scheduled_at", entry.scheduledAt.toString())
            set("notes", entry.notes)
        }) {
            filter { eq("id", entry.id) }
            select()
        }.decodeSingle<ScheduleDto>().toDomain()
    }

    private companion object {
        const val V_SCHEDULE_OVERVIEW = "v_schedule_overview"
        const val TABLE_SCHEDULES = "schedules"
        const val SCHEDULED_RAW = "scheduled"
        const val NEEDS_REPEAT_RAW = "needs_repeat"
    }
}
