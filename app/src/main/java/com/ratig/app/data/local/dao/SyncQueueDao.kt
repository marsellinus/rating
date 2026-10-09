package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/** Durable queue of records awaiting server confirmation. */
@Dao
interface SyncQueueDao {

    @Insert
    suspend fun enqueue(item: SyncQueueEntity)

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE entity_local_id = :entityLocalId LIMIT 1")
    suspend fun queueByEntityLocalId(entityLocalId: String): SyncQueueEntity?

    /** Retryable queue items, oldest first. */
    @Query(
        "SELECT * FROM sync_queue " +
            "WHERE sync_status IN ('PENDING', 'FAILED_RETRYABLE') " +
            "ORDER BY created_at ASC LIMIT :limit",
    )
    suspend fun nextPending(limit: Int = 20): List<SyncQueueEntity>

    /**
     * Targeted status update for one record's queue item. `lastSyncError`
     * must already be sanitized (fixed reason strings only).
     */
    @Query(
        "UPDATE sync_queue SET " +
            "sync_status = :syncStatus, " +
            "retry_count = :retryCount, " +
            "last_sync_attempt_at = :lastSyncAttemptAt, " +
            "last_sync_error = :lastSyncError, " +
            "updated_at = :updatedAt " +
            "WHERE entity_local_id = :entityLocalId",
    )
    suspend fun updateStatus(
        entityLocalId: String,
        syncStatus: SyncStatus,
        retryCount: Int,
        lastSyncAttemptAt: Long?,
        lastSyncError: String?,
        updatedAt: Long,
    )

    /** Queue items eligible for automatic sync (PENDING / SYNCING / FAILED_RETRYABLE). */
    @Query(
        "SELECT COUNT(*) FROM sync_queue " +
            "WHERE sync_status IN ('PENDING', 'SYNCING', 'FAILED_RETRYABLE')",
    )
    fun pendingCount(): Flow<Int>

    /** Queue items that need administrator review (FAILED_PERMANENT / NEEDS_REVIEW). */
    @Query(
        "SELECT COUNT(*) FROM sync_queue " +
            "WHERE sync_status IN ('FAILED_PERMANENT', 'NEEDS_REVIEW')",
    )
    fun failedCount(): Flow<Int>
}
