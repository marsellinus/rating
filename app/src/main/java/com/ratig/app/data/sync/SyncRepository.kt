package com.ratig.app.data.sync

import android.content.Context
import androidx.work.WorkManager
import com.ratig.app.core.sync.SyncSnapshot
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.entity.LocalTestSessionEntity
import com.ratig.app.data.local.entity.LocalTestTrialEntity
import com.ratig.app.data.local.entity.LocalTestResultEntity
import com.ratig.app.data.local.entity.SessionWithChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Singleton

/**
 * Offline-first sync facade between the UI, [com.ratig.app.core.sync.SyncWorker]
 * and the Room cache. Owned by the SyncEngine slice (CONTRACT-2).
 *
 * Guarantees:
 *  - [snapshot] never lies about confirmation: `lastSyncAt` is only written
 *    after `finalize_test_session` returned 200.
 *  - No method ever deletes local data.
 *  - No method logs or exposes tokens/NIK.
 */
interface SyncRepository {

    /** Combined network + Room counts + last outcome. Single UI source of truth. */
    val snapshot: StateFlow<SyncSnapshot>

    /** True while a [com.ratig.app.core.sync.SyncWorker] pass is running. */
    val isSyncing: StateFlow<Boolean>

    /** Recent local sessions (any status), newest first - for the status list. */
    fun observeSessionRows(): Flow<List<SessionSyncRow>>

    /** "Sinkronkan sekarang": one-shot work, CONNECTED constraint, 30s backoff. */
    fun requestManualSync()

    // ---- worker-side API -------------------------------------------------

    /** Pending + retryable sessions, oldest first, with trials and result. */
    suspend fun loadPendingSessions(limit: Int = 20): List<SessionWithChildren>

    /** Marks one session SYNCING before its attempt starts. */
    suspend fun markSyncing(localId: String)

    /** Writes the server-confirmed result and marks everything SYNCED. */
    suspend fun applySuccess(localId: String, result: com.ratig.app.core.sync.ServerResultDto)

    /**
     * Records a failed attempt: increments the retry budget, maps to
     * FAILED_RETRYABLE / FAILED_PERMANENT / NEEDS_REVIEW and stores the
     * sanitized reason.
     */
    suspend fun applyFailure(session: LocalTestSessionEntity, failure: com.ratig.app.core.sync.SyncFailure)

    /** Controlled by the worker around each pass. */
    suspend fun setSyncing(value: Boolean)
}

/** UI projection of a local session for the sync status screen. */
data class SessionSyncRow(
    val localId: String,
    val workerName: String?,
    val workerNumber: String?,
    val testMode: String?,
    val startedAt: Instant?,
    val trialCount: Int,
    val syncStatus: SyncStatus,
    val retryCount: Int,
    val lastSyncError: String?,
)
