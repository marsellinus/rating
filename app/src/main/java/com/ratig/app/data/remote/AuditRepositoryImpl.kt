package com.ratig.app.data.remote

import com.ratig.app.core.result.AppResult
import com.ratig.app.data.remote.dto.AuditLogDto
import com.ratig.app.domain.model.AuditLogEntry
import com.ratig.app.domain.repository.AuditRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the audit trail through the `v_audit_overview` view
 * (audit_logs + actor_name). Writes happen exclusively via DB triggers/RPCs,
 * so this repository only ever selects.
 */
@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : AuditRepository {

    override suspend fun list(
        action: String?,
        entityType: String?,
        limit: Int,
        offset: Int,
    ): AppResult<List<AuditLogEntry>> = AppResult.of {
        supabase.postgrest[V_AUDIT_OVERVIEW].select {
            filter {
                if (action != null) eq("action", action)
                if (entityType != null) eq("entity_type", entityType)
            }
            order("occurred_at", Order.DESCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<AuditLogDto>().map { it.toDomain() }
    }

    private companion object {
        const val V_AUDIT_OVERVIEW = "v_audit_overview"
    }
}
