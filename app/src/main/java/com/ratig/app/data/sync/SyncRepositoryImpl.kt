package com.ratig.app.data.sync

import android.content.Context
import com.ratig.app.core.sync.ServerResultDto
import com.ratig.app.core.sync.SyncFailure
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.core.sync.SyncStatusBus
import com.ratig.app.core.sync.SYNC_MAX_RETRIES
import com.ratig.app.core.sync.SyncWorker
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.local.dao.SessionDao
import com.ratig.app.data.local.dao.SyncQueueDao
import com.ratig.app.data.local.dao.WorkerDao
import com.ratig.app.data.local.entity.LocalTestResultEntity
import com.ratig.app.data.local.entity.LocalTestSessionEntity
import com.ratig.app.data.local.entity.SessionWithChildren
import com.ratig.app.data.offline.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Room-backed implementation of [SyncRepository]. All DAO access of the sync
 * engine is confined here; [SyncWorker] and [com.ratig.app.core.sync.SyncGateway]
 * contain no Room code.
 *
 * Producers feeding [SyncStatusBus] (started in `init`):
 *  - NetworkMonitor.isOnline -> isOnline + reconnect trigger,
 *  - SessionDao count Flows  -> pendingSessions/pendingTrials/failedCount,
 *  - SyncWorker outcomes     -> lastSyncAt/lastSyncError.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionDao: SessionDao,
    private val syncQueueDao: SyncQueueDao,
    private val workerDao: WorkerDao,
    private val networkMonitor: NetworkMonitor,
    private val bus: SyncStatusBus,
    @Named(SYNC_SCOPE) private val scope: CoroutineScope,
) : SyncRepository {

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** The bus IS the snapshot; the producers above keep it current. */
    override val snapshot: StateFlow<com.ratig.app.core.sync.SyncSnapshot> = bus.snapshot

    init {
        scope.launch {
            networkMonitor.isOnline.collect { bus.setOnline(it) }
        }
        scope.launch {
            combine(
                sessionDao.pendingSessionCount(),
                sessionDao.pendingTrialCount(),
                sessionDao.failedSessionCount(),
            ) { pendingSessions, pendingTrials, failedCount ->
                Triple(pendingSessions, pendingTrials, failedCount)
            }.collect { (pendingSessions, pendingTrials, failedCount) ->
                bus.setCounts(pendingSessions, pendingTrials, failedCount)
            }
        }
        // Wake the engine whenever connectivity returns with work left over.
        scope.launch {
            networkMonitor.isOnline.drop(1).filter { it }.collect {
                if (bus.snapshot.value.pendingSessions > 0) requestManualSync()
            }
        }
    }

    override fun observeSessionRows(): Flow<List<SessionSyncRow>> =
        sessionDao.observeRecentSessions().mapLatest { sessions ->
            sessions.map { session ->
                val worker = workerDao.byServerId(session.workerId)
                SessionSyncRow(
                    localId = session.localId,
                    workerName = worker?.fullName,
                    workerNumber = worker?.employeeNumber,
                    testMode = session.testMode,
                    startedAt = session.startedAt?.let(Instant::ofEpochMilli),
                    trialCount = sessionDao.trialsBySession(session.localId).size,
                    syncStatus = session.syncStatus,
                    retryCount = session.retryCount,
                    lastSyncError = session.lastSyncError,
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun requestManualSync() = SyncWorker.enqueueOneTime(appContext)

    override suspend fun loadPendingSessions(limit: Int): List<SessionWithChildren> =
        sessionDao.pendingSessions(limit).mapNotNull { sessionDao.sessionWithChildren(it.localId) }

    override suspend fun markSyncing(localId: String) {
        val session = sessionDao.sessionByLocalId(localId) ?: return
        val now = nowMs()
        sessionDao.updateSessionSync(
            localId = localId,
            syncStatus = SyncStatus.SYNCING,
            retryCount = session.retryCount,
            lastSyncAttemptAt = session.lastSyncAttemptAt,
            lastSyncError = session.lastSyncError,
            serverId = session.serverId,
            updatedAt = now,
        )
        mirrorQueue(localId, SyncStatus.SYNCING, session.retryCount, session.lastSyncAttemptAt, session.lastSyncError, now)
    }

    override suspend fun applySuccess(localId: String, result: ServerResultDto) {
        val now = nowMs()
        val existing = sessionDao.resultBySession(localId)
        val syncedResult = (existing
            ?: LocalTestResultEntity(
                sessionLocalId = localId,
                validTrialCount = result.validTrialCount,
                createdAt = now,
            )
        ).copy(
            validTrialCount = result.validTrialCount,
            invalidTrialCount = result.invalidTrialCount,
            missedResponseCount = result.missedResponseCount,
            falseStartCount = result.falseStartCount,
            meanReactionTimeMs = result.meanReactionTimeMs,
            medianReactionTimeMs = result.medianReactionTimeMs,
            minReactionTimeMs = result.minReactionTimeMs,
            maxReactionTimeMs = result.maxReactionTimeMs,
            standardDeviationMs = result.standardDeviationMs,
            slowResponseCount = result.slowResponseCount,
            excludedArtifactCount = result.excludedArtifactCount,
            classificationCode = result.classificationCode,
            classificationLabel = result.classificationLabel,
            classificationExplanation = result.classificationExplanation,
            ruleId = result.ruleId,
            ruleVersion = result.ruleVersion,
            accuracyRate = result.accuracyRate,
            correctResponseRate = result.correctResponseRate,
            falseAlarmRate = result.falseAlarmRate,
            omissionRate = result.omissionRate,
            testMode = result.testMode?.lowercase(),
            serverId = result.id,
            syncStatus = SyncStatus.SYNCED,
            retryCount = 0,
            lastSyncAttemptAt = now,
            lastSyncError = null,
            updatedAt = now,
        )
        // Sequential writes are safe: finalize is idempotent, so a crash
        // between them simply re-confirms the same data on the next attempt.
        sessionDao.upsertResult(syncedResult)
        sessionDao.markTrialsSynced(localId, now, now)
        val session = sessionDao.sessionByLocalId(localId)
        val serverId = result.sessionId.ifBlank { localId }
        sessionDao.updateSessionSync(
            localId = localId,
            syncStatus = SyncStatus.SYNCED,
            retryCount = session?.retryCount ?: 0,
            lastSyncAttemptAt = now,
            lastSyncError = null,
            serverId = serverId,
            updatedAt = now,
        )
        mirrorQueue(localId, SyncStatus.SYNCED, session?.retryCount ?: 0, now, null, now)
        bus.onSessionSynced(Instant.ofEpochMilli(now))
    }

    override suspend fun applyFailure(session: LocalTestSessionEntity, failure: SyncFailure) {
        val now = nowMs()
        val newRetryCount = session.retryCount + 1
        val status = when {
            !failure.retryable -> SyncStatus.NEEDS_REVIEW
            newRetryCount >= SYNC_MAX_RETRIES -> SyncStatus.FAILED_PERMANENT
            else -> SyncStatus.FAILED_RETRYABLE
        }
        sessionDao.updateSessionSync(
            localId = session.localId,
            syncStatus = status,
            retryCount = newRetryCount,
            lastSyncAttemptAt = now,
            lastSyncError = failure.reason,
            serverId = session.serverId,
            updatedAt = now,
        )
        mirrorQueue(session.localId, status, newRetryCount, now, failure.reason, now)
        bus.onSyncFailed(failure.reason)
    }

    override suspend fun setSyncing(value: Boolean) {
        _isSyncing.value = value
    }

    /** Keeps an EXISTING sync_queue row consistent; never creates entries. */
    private suspend fun mirrorQueue(
        entityLocalId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long?,
        error: String?,
        updatedAt: Long,
    ) {
        val queueRow = syncQueueDao.queueByEntityLocalId(entityLocalId) ?: return
        syncQueueDao.updateStatus(queueRow.entityLocalId, status, retryCount, lastAttemptAt, error, updatedAt)
    }

    private fun nowMs(): Long = TimeProvider.nowUtc().toEpochMilli()

    companion object {
        const val SYNC_SCOPE = "syncScope"
    }
}
