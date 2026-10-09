package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity
import java.time.Instant

/**
 * Locally appended audit event. The server `audit_logs` table is append-only
 * via triggers with no direct client writes, so audit events recorded offline
 * are kept here (retention; never auto-deleted) until an authorized channel
 * uploads them. Metadata is stored as a JSON object string and must never
 * contain tokens or NIK values.
 */
@Entity(
    tableName = LocalAuditEventEntity.TABLE_NAME,
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["sync_status"]),
    ],
)
data class LocalAuditEventEntity(
    /** auth.users id of the actor ("" when unknown). */
    @ColumnInfo(name = "actor_id") val actorId: String,
    /** Stable action code, e.g. "test.finalized_offline". */
    @ColumnInfo(name = "action") val action: String,
    /** Entity kind, e.g. "test_session". */
    @ColumnInfo(name = "entity_type") val entityType: String,
    /** Local id of the referenced entity. */
    @ColumnInfo(name = "entity_id") val entityId: String? = null,
    /** Epoch millis when the event happened on the device. */
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    /** JSON object string; values are non-sensitive by policy. */
    @ColumnInfo(name = "metadata") val metadataJson: String? = null,
    // Common offline columns.
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    override val localId: String = OfflineEntity.newLocalId(),
    @ColumnInfo(name = "server_id")
    override val serverId: String? = null,
    @ColumnInfo(name = "owner_user_id")
    override val ownerUserId: String = "",
    @ColumnInfo(name = "created_at")
    override val createdAt: Long = occurredAt,
    @ColumnInfo(name = "updated_at")
    override val updatedAt: Long = occurredAt,
    @ColumnInfo(name = "sync_status")
    override val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "retry_count")
    override val retryCount: Int = 0,
    @ColumnInfo(name = "last_sync_attempt_at")
    override val lastSyncAttemptAt: Long? = null,
    @ColumnInfo(name = "last_sync_error")
    override val lastSyncError: String? = null,
    @ColumnInfo(name = "protocol_version")
    override val protocolVersion: String = OfflineEntity.DEFAULT_PROTOCOL_VERSION,
    @ColumnInfo(name = "payload_version")
    override val payloadVersion: Int = OfflineEntity.PAYLOAD_VERSION,
) : OfflineEntity(localId, serverId, ownerUserId, createdAt, updatedAt, syncStatus, retryCount, lastSyncAttemptAt, lastSyncError, protocolVersion, payloadVersion) {

    /** Epoch instant of [occurredAt], for display contexts. */
    fun occurredAtInstant(): Instant = Instant.ofEpochMilli(occurredAt)

    companion object {
        const val TABLE_NAME = "local_audit_events"
    }
}
