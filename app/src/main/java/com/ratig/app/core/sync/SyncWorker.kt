package com.ratig.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.json.JsonCodec
import com.ratig.app.data.sync.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * CONTRACT-2 §Sync engine worker: Hilt-injected CoroutineWorker.
 *
 * One pass processes every PENDING / FAILED_RETRYABLE session oldest-first
 * (CONTRACT-2 algorithm):
 *  1. load session + trials + result from Room (transactional read),
 *  2. REST-insert the session row with the CLIENT UUID as id - a 409 means
 *     it already exists, which is fine (idempotent path),
 *  3. call `finalize_test_session`; on 200 the server result row is written
 *     into Room and session/trials/result are marked SYNCED,
 *  4. failures are mapped by [SyncFailure]: network/401 retry with backoff
 *     (max [SYNC_MAX_RETRIES] per item, then FAILED_PERMANENT);
 *     403/42501 and 400 validation go to NEEDS_REVIEW.
 *
 * Invariants: never deletes local data, never marks SYNCED without server
 * confirmation, never logs tokens or NIK.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val gateway: SyncGateway,
    private val supabase: SupabaseClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Without a server session every attempt would end 401; leave data
        // PENDING (no retry-count penalty) and wait for the next trigger -
        // auth storage autoload + auto refresh run at app start.
        if (supabase.auth.currentSessionOrNull() == null) return Result.retry()

        syncRepository.setSyncing(true)
        try {
            val batches = syncRepository.loadPendingSessions(limit = SYNC_BATCH_LIMIT)
            for (bundle in batches) {
                if (isStopped) break
                syncSession(bundle)
            }
            return Result.success()
        } catch (t: Throwable) {
            // Worker-level crash (e.g. DB closed): backoff via WorkManager.
            return if (runAttemptCount < SYNC_MAX_RETRIES) Result.retry() else Result.failure()
        } finally {
            syncRepository.setSyncing(false)
        }
    }

    private suspend fun syncSession(bundle: com.ratig.app.data.local.entity.SessionWithChildren) {
        val session = bundle.session
        syncRepository.markSyncing(session.localId)

        // (1)+(2) Session row with the client UUID as primary key. 409 ->
        // already exists from an earlier attempt; both outcomes continue.
        val inserted = gateway.insertSessionRow(toInsertDto(session))
        if (inserted is SyncGateway.SessionInsertResult.Failed) {
            syncRepository.applyFailure(session, inserted.failure)
            return
        }

        // (3) Idempotent finalize RPC returns the server-authoritative result.
        val finalized = gateway.finalizeSession(
            sessionId = session.localId,
            trials = bundle.trials.sortedBy { it.trialNumber }.map { toTrialPayload(it) },
            appVersion = session.appVersion ?: DeviceMetadata.current().appVersionName,
            deviceMetadata = parseDeviceMetadata(session.deviceMetadataJson),
        )
        when (finalized) {
            is SyncGateway.FinalizeResult.Success ->
                syncRepository.applySuccess(session.localId, finalized.result)
            is SyncGateway.FinalizeResult.Failed ->
                syncRepository.applyFailure(session, finalized.failure)
        }
    }

    private fun toInsertDto(session: com.ratig.app.data.local.entity.LocalTestSessionEntity) =
        SyncSessionInsertDto(
            id = session.localId,
            workerId = session.workerId,
            examinerId = session.examinerId,
            shiftId = session.shiftId,
            protocolId = session.protocolId,
            fatigueRuleId = session.fatigueRuleId,
            appVersion = session.appVersion ?: DeviceMetadata.current().appVersionName,
            deviceMetadata = parseDeviceMetadata(session.deviceMetadataJson),
            startedAt = session.startedAt?.let { Instant.ofEpochMilli(it).toString() },
            completedAt = session.completedAt?.let { Instant.ofEpochMilli(it).toString() },
            sessionStatus = SESSION_STATUS_PENDING_SYNC,
            testMode = session.testMode.lowercase(),
            randomSeed = session.randomSeed,
            modeConfig = session.modeConfig,
            nikSnapshot = session.nikSnapshot,
        )

    private fun toTrialPayload(trial: com.ratig.app.data.local.entity.LocalTestTrialEntity) =
        SyncTrialPayloadDto(
            trialNumber = trial.trialNumber,
            stimulusAtMonotonicNs = trial.stimulusAtMonotonicNs,
            tapAtMonotonicNs = trial.tapAtMonotonicNs,
            reactionTimeMs = trial.reactionTimeMs,
            trialStatus = trial.trialStatus.lowercase(),
            falseStart = trial.falseStart,
            missedResponse = trial.missedResponse,
            stimulusKind = trial.stimulusKind,
            isTarget = trial.isTarget,
            responseType = trial.responseType,
            responseCorrect = trial.responseCorrect,
        )

    private fun parseDeviceMetadata(raw: String?): JsonObject =
        raw?.let { value ->
            runCatching { JsonCodec.json.parseToJsonElement(value) }.getOrNull() as? JsonObject
        } ?: JsonObject(emptyMap())

    companion object {
        const val PERIODIC_WORK_NAME = "ratig_sync_periodic"
        const val MANUAL_WORK_NAME = "ratig_sync_manual"

        /** Sessions processed per worker pass (oldest first). */
        const val SYNC_BATCH_LIMIT = 20

        /** Wire value between insert and finalize (DB CHECK constraint). */
        const val SESSION_STATUS_PENDING_SYNC = "pending_sync"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Called once from RatigApp.onCreate; KEEP policy makes repeats safe. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** "Sinkronkan sekarang" button / reconnect trigger (REPLACE latest). */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
