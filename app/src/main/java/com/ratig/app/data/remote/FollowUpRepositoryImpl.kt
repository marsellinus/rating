package com.ratig.app.data.remote

import com.ratig.app.core.result.AppResult
import com.ratig.app.data.remote.dto.FollowUpDto
import com.ratig.app.domain.model.FollowUp
import com.ratig.app.domain.model.FollowUpStatus
import com.ratig.app.domain.repository.FollowUpRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follow-ups are listed through the `v_follow_up_overview` view
 * (security_invoker: RLS of the caller applies) and written to the
 * `follow_ups` table directly.
 */
@Singleton
class FollowUpRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : FollowUpRepository {

    override suspend fun list(
        onlyOpen: Boolean,
        workerQuery: String?,
        limit: Int,
        offset: Int,
    ): AppResult<List<FollowUp>> = AppResult.of {
        supabase.postgrest["v_follow_up_overview"].select {
            filter {
                if (onlyOpen) eq("status", FollowUpStatus.OPEN.name.lowercase())
                if (!workerQuery.isNullOrBlank()) ilike("worker_name", "%${workerQuery.trim()}%")
            }
            order("created_at", Order.DESCENDING)
            range(offset.toLong(), (offset + limit - 1).coerceAtLeast(offset).toLong())
        }.decodeList<FollowUpDto>().map { it.toDomain() }
    }

    override suspend fun listForSession(sessionId: String): AppResult<List<FollowUp>> = AppResult.of {
        supabase.postgrest["v_follow_up_overview"].select {
            filter { eq("session_id", sessionId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<FollowUpDto>().map { it.toDomain() }
    }

    override suspend fun create(followUp: FollowUp): AppResult<FollowUp> = AppResult.of {
        val dto = FollowUpDto.forInsert(
            sessionId = followUp.sessionId,
            actionType = followUp.actionType,
            notes = followUp.notes,
            // Default assignee is the current authenticated user.
            assignedTo = followUp.assignedTo ?: supabase.auth.currentUserOrNull()?.id,
            dueAt = followUp.dueAt,
        )
        supabase.postgrest["follow_ups"].insert(dto) { select() }.decodeSingle<FollowUpDto>().toDomain()
    }

    override suspend fun updateStatus(id: String, status: FollowUpStatus): AppResult<Unit> = AppResult.of {
        supabase.postgrest["follow_ups"].update({
            set("status", status.name.lowercase())
            set("updated_at", Instant.now().toString())
            if (status == FollowUpStatus.COMPLETED) {
                set("completed_at", Instant.now().toString())
            }
        }) {
            filter { eq("id", id) }
        }
        Unit
    }
}
