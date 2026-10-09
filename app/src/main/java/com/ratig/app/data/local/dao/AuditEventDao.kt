package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.entity.LocalAuditEventEntity
import kotlinx.coroutines.flow.Flow

/** Locally appended audit events (retention; never auto-deleted). */
@Dao
interface AuditEventDao {

    @Insert
    suspend fun insert(event: LocalAuditEventEntity)

    @Insert
    suspend fun insertAll(events: List<LocalAuditEventEntity>)

    @Update
    suspend fun update(event: LocalAuditEventEntity)

    @Query("SELECT * FROM local_audit_events ORDER BY occurred_at DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<LocalAuditEventEntity>

    @Query("SELECT * FROM local_audit_events ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<LocalAuditEventEntity>>

    @Query("SELECT * FROM local_audit_events WHERE entity_id = :entityLocalId ORDER BY occurred_at ASC")
    suspend fun byEntityId(entityLocalId: String): List<LocalAuditEventEntity>

    @Query(
        "SELECT COUNT(*) FROM local_audit_events " +
            "WHERE sync_status IN ('PENDING', 'SYNCING', 'FAILED_RETRYABLE')",
    )
    fun pendingCount(): Flow<Int>

    /** Status update after an upload attempt; `lastSyncError` must be sanitized. */
    @Query(
        "UPDATE local_audit_events SET " +
            "sync_status = :syncStatus, " +
            "retry_count = :retryCount, " +
            "last_sync_attempt_at = :lastSyncAttemptAt, " +
            "last_sync_error = :lastSyncError, " +
            "updated_at = :updatedAt " +
            "WHERE local_id = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        syncStatus: SyncStatus,
        retryCount: Int,
        lastSyncAttemptAt: Long?,
        lastSyncError: String?,
        updatedAt: Long,
    )
}
