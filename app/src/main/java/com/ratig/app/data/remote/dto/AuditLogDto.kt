package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.AuditLogEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/**
 * Row of the `v_audit_overview` view: audit_logs enriched with actor_name.
 * Append-only; the client only ever reads this view.
 */
@Serializable
data class AuditLogDto(
    val id: String,
    @SerialName("actor_id") val actorId: String? = null,
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("occurred_at") val occurredAt: Instant? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
    // Denormalized display column from the view
    @SerialName("actor_name") val actorName: String? = null,
) {
    fun toDomain(): AuditLogEntry = AuditLogEntry(
        id = id,
        actorId = actorId,
        action = action,
        entityType = entityType,
        entityId = entityId,
        occurredAt = occurredAt,
        metadata = metadata.toStringMap(),
        actorName = actorName,
    )
}

/** Flattens a jsonb metadata object; non-string values fall back to their JSON text. */
private fun JsonObject.toStringMap(): Map<String, String> = buildMap {
    for ((key, value) in this@toStringMap) {
        put(key, when (value) {
            is JsonPrimitive -> value.contentOrNull ?: value.toString()
            is JsonElement -> value.toString()
        })
    }
}
