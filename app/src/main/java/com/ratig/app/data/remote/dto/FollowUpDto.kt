package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.FollowUp
import com.ratig.app.domain.model.FollowUpStatus
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row of table `follow_ups` or view `v_follow_up_overview`
 * (view adds worker_name via session→worker and assigned_to_name via profiles).
 *
 * Used for both decode (rows always carry `id`) and insert, where `id` and
 * audit timestamps stay null and are omitted (`explicitNulls = false`) so the
 * database defaults (`gen_random_uuid()`, `now()`) apply.
 */
@Serializable
data class FollowUpDto(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("action_type") val actionType: String,
    val notes: String? = null,
    val status: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("due_at") @Serializable(with = InstantAsStringSerializer::class) val dueAt: Instant? = null,
    @SerialName("completed_at") @Serializable(with = InstantAsStringSerializer::class) val completedAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantAsStringSerializer::class) val updatedAt: Instant? = null,
    // Joined columns, only present on v_follow_up_overview.
    @SerialName("worker_name") val workerName: String? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
) {
    fun toDomain(): FollowUp = FollowUp(
        id = requireNotNull(id) { "follow_ups.id missing in decoded row" },
        sessionId = sessionId,
        actionType = actionType,
        notes = notes,
        status = FollowUpStatus.fromRaw(status),
        assignedTo = assignedTo,
        dueAt = dueAt,
        completedAt = completedAt,
        createdAt = createdAt,
        workerName = workerName,
        assignedToName = assignedToName,
    )

    companion object {
        /** Insert payload; server fills id/status default/created_at/updated_at. */
        fun forInsert(
            sessionId: String,
            actionType: String,
            notes: String?,
            assignedTo: String?,
            dueAt: Instant?,
        ): FollowUpDto = FollowUpDto(
            sessionId = sessionId,
            actionType = actionType,
            notes = notes,
            status = FollowUpStatus.OPEN.name.lowercase(),
            assignedTo = assignedTo,
            dueAt = dueAt,
        )
    }
}
