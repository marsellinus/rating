package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import java.time.Instant

/**
 * One recorded measurement attempt of a local session. Trials are written
 * transactionally with their session and inherit its sync lifecycle: they
 * are marked SYNCED only when the server confirmed the whole session
 * (finalize RPC 200). Individual server trial ids are never returned by the
 * RPC, so `server_id` stays NULL while `sync_status` reflects the batch.
 */
@Entity(
    tableName = LocalTestTrialEntity.TABLE_NAME,
    indices = [
        Index(value = ["session_local_id", "trial_number"], unique = true),
    ],
)
data class LocalTestTrialEntity(
    @ColumnInfo(name = "session_local_id") val sessionLocalId: String,
    @ColumnInfo(name = "trial_number") val trialNumber: Int,
    @ColumnInfo(name = "stimulus_at_monotonic_ns") val stimulusAtMonotonicNs: Long? = null,
    @ColumnInfo(name = "tap_at_monotonic_ns") val tapAtMonotonicNs: Long? = null,
    @ColumnInfo(name = "reaction_time_ms") val reactionTimeMs: Long? = null,
    /** Raw wire value ('valid' | 'false_start' | 'missed' | 'invalid'). */
    @ColumnInfo(name = "trial_status") val trialStatus: String,
    @ColumnInfo(name = "false_start") val falseStart: Boolean = false,
    @ColumnInfo(name = "missed_response") val missedResponse: Boolean = false,
    /** Mode tests: what was shown, e.g. rgb_green / button_5 / circle_nogo. */
    @ColumnInfo(name = "stimulus_kind") val stimulusKind: String? = null,
    /** Mode tests: target stimulus (true) or distractor (false)? */
    @ColumnInfo(name = "is_target") val isTarget: Boolean? = null,
    /** Mode tests: correct | wrong | late | early | none. */
    @ColumnInfo(name = "response_type") val responseType: String? = null,
    /** Mode tests: was the response correct for this trial? */
    @ColumnInfo(name = "response_correct") val responseCorrect: Boolean? = null,
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

    fun toDomain(): TrialRecord = TrialRecord(
        id = localId,
        sessionId = sessionLocalId,
        trialNumber = trialNumber,
        stimulusAtMonotonicNs = stimulusAtMonotonicNs,
        tapAtMonotonicNs = tapAtMonotonicNs,
        reactionTimeMs = reactionTimeMs,
        trialStatus = TrialStatus.fromRaw(trialStatus),
        falseStart = falseStart,
        missedResponse = missedResponse,
        stimulusKind = stimulusKind,
        isTarget = isTarget,
        responseType = responseType,
        responseCorrect = responseCorrect,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    companion object {
        const val TABLE_NAME = "local_test_trials"

        fun fromDomain(
            trial: TrialRecord,
            sessionLocalId: String,
            ownerUserId: String,
            syncStatus: SyncStatus,
            nowEpochMs: Long,
        ): LocalTestTrialEntity = LocalTestTrialEntity(
            sessionLocalId = sessionLocalId,
            trialNumber = trial.trialNumber,
            stimulusAtMonotonicNs = trial.stimulusAtMonotonicNs,
            tapAtMonotonicNs = trial.tapAtMonotonicNs,
            reactionTimeMs = trial.reactionTimeMs,
            trialStatus = trial.trialStatus.wireValue(),
            falseStart = trial.falseStart,
            missedResponse = trial.missedResponse,
            stimulusKind = trial.stimulusKind,
            isTarget = trial.isTarget,
            responseType = trial.responseType,
            responseCorrect = trial.responseCorrect,
            localId = trial.id ?: OfflineEntity.newLocalId(),
            serverId = if (syncStatus == SyncStatus.SYNCED) trial.id else null,
            ownerUserId = ownerUserId,
            createdAt = trial.createdAt?.toEpochMilli() ?: nowEpochMs,
            updatedAt = nowEpochMs,
            syncStatus = syncStatus,
        )
    }
}

/** Raw wire spellings for locally stored enum-ish columns. */
internal fun TrialStatus.wireValue(): String = when (this) {
    TrialStatus.VALID -> "valid"
    TrialStatus.FALSE_START -> "false_start"
    TrialStatus.MISSED -> "missed"
    TrialStatus.INVALID -> "invalid"
}
