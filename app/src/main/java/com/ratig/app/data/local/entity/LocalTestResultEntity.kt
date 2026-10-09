package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity
import com.ratig.app.domain.model.TestMetrics
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestResult
import java.time.Instant

/**
 * Locally computed result for a session (one per session, enforced by the
 * unique index). Locally computed values are provisional: when the sync
 * engine receives the authoritative server row from `finalize_test_session`
 * it overwrites these columns and marks the row SYNCED with the server id.
 */
@Entity(
    tableName = LocalTestResultEntity.TABLE_NAME,
    indices = [
        Index(value = ["session_local_id"], unique = true),
    ],
)
data class LocalTestResultEntity(
    @ColumnInfo(name = "session_local_id") val sessionLocalId: String,
    @ColumnInfo(name = "valid_trial_count") val validTrialCount: Int,
    @ColumnInfo(name = "invalid_trial_count") val invalidTrialCount: Int = 0,
    @ColumnInfo(name = "missed_response_count") val missedResponseCount: Int = 0,
    @ColumnInfo(name = "false_start_count") val falseStartCount: Int = 0,
    @ColumnInfo(name = "mean_reaction_time_ms") val meanReactionTimeMs: Double? = null,
    @ColumnInfo(name = "median_reaction_time_ms") val medianReactionTimeMs: Double? = null,
    @ColumnInfo(name = "min_reaction_time_ms") val minReactionTimeMs: Long? = null,
    @ColumnInfo(name = "max_reaction_time_ms") val maxReactionTimeMs: Long? = null,
    @ColumnInfo(name = "standard_deviation_ms") val standardDeviationMs: Double? = null,
    @ColumnInfo(name = "slow_response_count") val slowResponseCount: Int = 0,
    @ColumnInfo(name = "excluded_artifact_count") val excludedArtifactCount: Int = 0,
    /** Machine band code from the applied rule (NULL for non-classic modes). */
    @ColumnInfo(name = "classification_code") val classificationCode: String? = null,
    @ColumnInfo(name = "classification_label") val classificationLabel: String? = null,
    @ColumnInfo(name = "classification_explanation") val classificationExplanation: String? = null,
    @ColumnInfo(name = "rule_id") val ruleId: String? = null,
    @ColumnInfo(name = "rule_version") val ruleVersion: String? = null,
    // Mode observation metrics (NULL for classic).
    @ColumnInfo(name = "accuracy_rate") val accuracyRate: Double? = null,
    @ColumnInfo(name = "correct_response_rate") val correctResponseRate: Double? = null,
    @ColumnInfo(name = "false_alarm_rate") val falseAlarmRate: Double? = null,
    @ColumnInfo(name = "omission_rate") val omissionRate: Double? = null,
    @ColumnInfo(name = "test_mode") val testMode: String? = null,
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

    fun toDomain(): TestResult = TestResult(
        id = localId,
        sessionId = sessionLocalId,
        metrics = TestMetrics(
            totalTrials = validTrialCount + invalidTrialCount,
            validTrialCount = validTrialCount,
            invalidTrialCount = invalidTrialCount,
            missedResponseCount = missedResponseCount,
            falseStartCount = falseStartCount,
            minReactionTimeMs = minReactionTimeMs,
            maxReactionTimeMs = maxReactionTimeMs,
            meanReactionTimeMs = meanReactionTimeMs,
            medianReactionTimeMs = medianReactionTimeMs,
            standardDeviationMs = standardDeviationMs,
            slowResponseCount = slowResponseCount,
            excludedArtifactCount = excludedArtifactCount,
        ),
        classificationCode = classificationCode,
        classificationLabel = classificationLabel,
        classificationExplanation = classificationExplanation,
        ruleVersion = ruleVersion,
        accuracyRate = accuracyRate,
        correctResponseRate = correctResponseRate,
        falseAlarmRate = falseAlarmRate,
        omissionRate = omissionRate,
        testMode = testMode?.let { TestMode.fromRaw(it) },
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    companion object {
        const val TABLE_NAME = "local_test_results"

        fun fromDomain(
            result: TestResult,
            sessionLocalId: String,
            ownerUserId: String,
            syncStatus: SyncStatus,
            nowEpochMs: Long,
        ): LocalTestResultEntity = LocalTestResultEntity(
            sessionLocalId = sessionLocalId,
            validTrialCount = result.metrics.validTrialCount,
            invalidTrialCount = result.metrics.invalidTrialCount,
            missedResponseCount = result.metrics.missedResponseCount,
            falseStartCount = result.metrics.falseStartCount,
            meanReactionTimeMs = result.metrics.meanReactionTimeMs,
            medianReactionTimeMs = result.metrics.medianReactionTimeMs,
            minReactionTimeMs = result.metrics.minReactionTimeMs,
            maxReactionTimeMs = result.metrics.maxReactionTimeMs,
            standardDeviationMs = result.metrics.standardDeviationMs,
            slowResponseCount = result.metrics.slowResponseCount,
            excludedArtifactCount = result.metrics.excludedArtifactCount,
            classificationCode = result.classificationCode,
            classificationLabel = result.classificationLabel,
            classificationExplanation = result.classificationExplanation,
            ruleId = null,
            ruleVersion = result.ruleVersion,
            accuracyRate = result.accuracyRate,
            correctResponseRate = result.correctResponseRate,
            falseAlarmRate = result.falseAlarmRate,
            omissionRate = result.omissionRate,
            testMode = result.testMode?.serverValue,
            localId = result.id,
            serverId = if (syncStatus == SyncStatus.SYNCED) result.id else null,
            ownerUserId = ownerUserId,
            createdAt = result.createdAt?.toEpochMilli() ?: nowEpochMs,
            updatedAt = nowEpochMs,
            syncStatus = syncStatus,
        )
    }
}
