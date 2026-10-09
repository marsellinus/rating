package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.entity.LocalTestResultEntity
import com.ratig.app.data.local.entity.LocalTestSessionEntity
import com.ratig.app.data.local.entity.LocalTestTrialEntity
import com.ratig.app.data.local.entity.SessionWithChildren
import kotlinx.coroutines.flow.Flow

/**
 * Local examination sessions + trials + results. Everything a sync attempt
 * needs lives here so the sync engine can run transactionally in Room.
 */
@Dao
abstract class SessionDao {

    // region writes

    @Insert
    abstract suspend fun insertSession(session: LocalTestSessionEntity)

    @Insert
    abstract suspend fun insertTrials(trials: List<LocalTestTrialEntity>)

    @Insert
    abstract suspend fun insertResult(result: LocalTestResultEntity)

    /** Atomic save of a finished (or in-progress) session with all children. */
    @Transaction
    open suspend fun insertSessionWithTrials(
        session: LocalTestSessionEntity,
        trials: List<LocalTestTrialEntity>,
        result: LocalTestResultEntity?,
    ) {
        insertSession(session)
        if (trials.isNotEmpty()) insertTrials(trials)
        if (result != null) insertResult(result)
    }

    @Update
    abstract suspend fun updateSession(session: LocalTestSessionEntity)

    @Upsert
    abstract suspend fun upsertResult(result: LocalTestResultEntity)

    /**
     * Targeted sync-state update (exactly the fields the sync engine owns).
     * `lastSyncError` must already be sanitized (fixed Indonesian reason
     * strings - no exception text, tokens or NIK).
     */
    @Query(
        "UPDATE local_test_sessions SET " +
            "sync_status = :syncStatus, " +
            "retry_count = :retryCount, " +
            "last_sync_attempt_at = :lastSyncAttemptAt, " +
            "last_sync_error = :lastSyncError, " +
            "server_id = COALESCE(:serverId, server_id), " +
            "updated_at = :updatedAt " +
            "WHERE local_id = :localId",
    )
    abstract suspend fun updateSessionSync(
        localId: String,
        syncStatus: SyncStatus,
        retryCount: Int,
        lastSyncAttemptAt: Long?,
        lastSyncError: String?,
        serverId: String?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE local_test_trials SET " +
            "sync_status = 'SYNCED', " +
            "last_sync_attempt_at = :lastSyncAttemptAt, " +
            "last_sync_error = NULL, " +
            "updated_at = :updatedAt " +
            "WHERE session_local_id = :sessionLocalId",
    )
    abstract suspend fun markTrialsSynced(sessionLocalId: String, lastSyncAttemptAt: Long, updatedAt: Long)

    @Query(
        "UPDATE local_test_results SET " +
            "sync_status = 'SYNCED', " +
            "last_sync_attempt_at = :lastSyncAttemptAt, " +
            "last_sync_error = NULL, " +
            "updated_at = :updatedAt " +
            "WHERE session_local_id = :sessionLocalId",
    )
    abstract suspend fun markResultSynced(sessionLocalId: String, lastSyncAttemptAt: Long, updatedAt: Long)

    // endregion

    // region reads

    @Query("SELECT * FROM local_test_sessions WHERE local_id = :localId LIMIT 1")
    abstract suspend fun sessionByLocalId(localId: String): LocalTestSessionEntity?

    @Transaction
    @Query("SELECT * FROM local_test_sessions WHERE local_id = :localId LIMIT 1")
    abstract suspend fun sessionWithChildren(localId: String): SessionWithChildren?

    /** Retryable sessions, oldest first. */
    @Query(
        "SELECT * FROM local_test_sessions " +
            "WHERE sync_status IN ('PENDING', 'FAILED_RETRYABLE') " +
            "ORDER BY created_at ASC LIMIT :limit",
    )
    abstract suspend fun pendingSessions(limit: Int = 20): List<LocalTestSessionEntity>

    @Query("SELECT * FROM local_test_trials WHERE session_local_id = :sessionLocalId ORDER BY trial_number ASC")
    abstract suspend fun trialsBySession(sessionLocalId: String): List<LocalTestTrialEntity>

    /** Recent sessions across ALL statuses (sync status screen listing). */
    @Query("SELECT * FROM local_test_sessions ORDER BY created_at DESC LIMIT :limit")
    abstract fun observeRecentSessions(limit: Int = 100): Flow<List<LocalTestSessionEntity>>

    @Query("SELECT * FROM local_test_results WHERE session_local_id = :sessionLocalId LIMIT 1")
    abstract suspend fun resultBySession(sessionLocalId: String): LocalTestResultEntity?

    // endregion

    // region aggregate counts (SyncStatusBus)

    /** Sessions eligible for automatic sync (PENDING / SYNCING / FAILED_RETRYABLE). */
    @Query(
        "SELECT COUNT(*) FROM local_test_sessions " +
            "WHERE sync_status IN ('PENDING', 'SYNCING', 'FAILED_RETRYABLE')",
    )
    abstract fun pendingSessionCount(): Flow<Int>

    /** Sessions that will NOT be retried automatically - need admin review. */
    @Query(
        "SELECT COUNT(*) FROM local_test_sessions " +
            "WHERE sync_status IN ('FAILED_PERMANENT', 'NEEDS_REVIEW')",
    )
    abstract fun failedSessionCount(): Flow<Int>

    /** Trial rows belonging to sessions the server has not confirmed yet. */
    @Query(
        "SELECT COUNT(*) FROM local_test_trials " +
            "WHERE session_local_id IN (" +
            "SELECT local_id FROM local_test_sessions WHERE sync_status != 'SYNCED')",
    )
    abstract fun pendingTrialCount(): Flow<Int>

    // endregion
}
