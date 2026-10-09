package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity

/**
 * One row per local record awaiting server confirmation. The queue never
 * deletes confirmed rows on success (retention rule): rows transition to
 * SYNCED and remain as an audit trail of what was uploaded.
 *
 * `entity_type` values: "test_session" (session+trials+result batch), plus
 * future record kinds. `operation`: "insert" | "update" | "rpc_finalize".
 */
@Entity(
    tableName = SyncQueueEntity.TABLE_NAME,
    indices = [
        Index(value = ["entity_local_id"]),
        Index(value = ["sync_status"]),
    ],
)
data class SyncQueueEntity(
    /** Local id of the record this queue item refers to. */
    @ColumnInfo(name = "entity_local_id") val entityLocalId: String,
    /** Record kind, e.g. "test_session". */
    @ColumnInfo(name = "entity_type") val entityType: String,
    /** Operation to perform, e.g. "rpc_finalize". */
    @ColumnInfo(name = "operation") val operation: String,
    // Common offline columns.
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    override val localId: String = OfflineEntity.newLocalId(),
    @ColumnInfo(name = "server_id")
    override val serverId: String? = null,
    @ColumnInfo(name = "owner_user_id")
    override val ownerUserId: String = "",
    @ColumnInfo(name = "created_at")
    override val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    override val updatedAt: Long = createdAt,
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

    companion object {
        const val TABLE_NAME = "sync_queue"

        const val ENTITY_TEST_SESSION = "test_session"
        const val OP_RPC_FINALIZE = "rpc_finalize"
    }
}
