package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Relation
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestSession
import java.time.Instant

/**
 * Locally persisted examination session. The canonical offline-first record:
 * a Room save here is PENDING ("Tersimpan di perangkat") and the row is only
 * marked SYNCED (with `server_id`) after the server confirmed the session
 * (REST insert accepted and `finalize_test_session` returned 200).
 *
 * `local_id` doubles as the client-generated session id sent to the server
 * on insert (PostgREST accepts client PKs), so offline-created sessions keep
 * a stable identity across retries.
 */
@Entity(
    tableName = LocalTestSessionEntity.TABLE_NAME,
    indices = [
        Index(value = ["server_id"]),
        Index(value = ["worker_id"]),
        Index(value = ["examiner_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["created_at"]),
    ],
)
data class LocalTestSessionEntity(
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "examiner_id") val examinerId: String,
    @ColumnInfo(name = "shift_id") val shiftId: String? = null,
    @ColumnInfo(name = "protocol_id") val protocolId: String,
    @ColumnInfo(name = "fatigue_rule_id") val fatigueRuleId: String? = null,
    @ColumnInfo(name = "app_version") val appVersion: String? = null,
    /** JSON object string of [TestSession.deviceMetadata]. */
    @ColumnInfo(name = "device_metadata") val deviceMetadataJson: String? = null,
    /** Epoch millis; NULL until the first trial is presented. */
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    /** Epoch millis; NULL until completed. */
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    /** Raw wire value ('created' | 'in_progress' | ... ). */
    @ColumnInfo(name = "session_status") val sessionStatus: String,
    @ColumnInfo(name = "interruption_reason") val interruptionReason: String? = null,
    /** Raw wire value ('classic' | 'rgb_random' | ...). */
    @ColumnInfo(name = "test_mode") val testMode: String = TestMode.CLASSIC.name,
    /** Seeded RNG base saved for audit reproducibility. */
    @ColumnInfo(name = "random_seed") val randomSeed: String? = null,
    /** Frozen mode configuration JSON captured at session start. */
    @ColumnInfo(name = "mode_config") val modeConfig: String? = null,
    /** NIK audit snapshot exactly as the session creator supplied it. */
    @ColumnInfo(name = "nik_snapshot") val nikSnapshot: String? = null,
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

    fun toDomain(): TestSession = TestSession(
        id = localId,
        workerId = workerId,
        examinerId = examinerId,
        shiftId = shiftId,
        protocolId = protocolId,
        fatigueRuleId = fatigueRuleId,
        appVersion = appVersion,
        deviceMetadata = decodeMetadata(deviceMetadataJson),
        startedAt = startedAt?.let(Instant::ofEpochMilli),
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        sessionStatus = SessionStatus.fromRaw(sessionStatus),
        interruptionReason = interruptionReason,
        createdAt = Instant.ofEpochMilli(createdAt),
        testMode = TestMode.fromRaw(testMode),
        randomSeed = randomSeed,
        modeConfig = modeConfig,
        nikSnapshot = nikSnapshot,
        syncStatus = syncStatus,
    )

    companion object {
        const val TABLE_NAME = "local_test_sessions"

        /**
         * Builds the local record from a domain session. `session.id` is used
         * verbatim as the local id: for offline-created sessions it is the
         * client UUID that will become the server PK; for sessions fetched
         * from the server it is the server id (then `serverId` is set and the
         * row starts as SYNCED when the domain status says so).
         */
        fun fromDomain(
            session: TestSession,
            ownerUserId: String,
            nowEpochMs: Long,
        ): LocalTestSessionEntity {
            val serverBacked = session.syncStatus == SyncStatus.SYNCED
            return LocalTestSessionEntity(
                workerId = session.workerId,
                examinerId = session.examinerId,
                shiftId = session.shiftId,
                protocolId = session.protocolId,
                fatigueRuleId = session.fatigueRuleId,
                appVersion = session.appVersion,
                deviceMetadataJson = encodeMetadata(session.deviceMetadata),
                startedAt = session.startedAt?.toEpochMilli(),
                completedAt = session.completedAt?.toEpochMilli(),
                sessionStatus = session.sessionStatus.wireValue(),
                interruptionReason = session.interruptionReason,
                testMode = session.testMode.serverValue,
                randomSeed = session.randomSeed,
                modeConfig = session.modeConfig,
                nikSnapshot = session.nikSnapshot,
                localId = session.id,
                serverId = if (serverBacked) session.id else null,
                ownerUserId = ownerUserId,
                createdAt = session.createdAt?.toEpochMilli() ?: nowEpochMs,
                updatedAt = nowEpochMs,
                syncStatus = session.syncStatus,
            )
        }

        private fun encodeMetadata(metadata: Map<String, String>): String? =
            if (metadata.isEmpty()) {
                null
            } else {
                runCatching {
                    com.ratig.app.core.json.JsonCodec.encode(metadata)
                }.getOrNull()
            }

        private fun decodeMetadata(raw: String?): Map<String, String> =
            raw?.let { value ->
                runCatching {
                    com.ratig.app.core.json.JsonCodec.decode<Map<String, String>>(value)
                }.getOrNull()
            } ?: emptyMap()
    }
}

/** Raw wire spelling (snake_case, matching the server CHECK constraint). */
internal fun SessionStatus.wireValue(): String = when (this) {
    SessionStatus.CREATED -> "created"
    SessionStatus.IN_PROGRESS -> "in_progress"
    SessionStatus.INTERRUPTED -> "interrupted"
    SessionStatus.PENDING_SYNC -> "pending_sync"
    SessionStatus.FINALIZED -> "finalized"
    SessionStatus.FAILED -> "failed"
}

/** Session aggregate with its trials and (optional) result in one read. */
data class SessionWithChildren(
    @Embedded val session: LocalTestSessionEntity,
    @Relation(parentColumn = "local_id", entityColumn = "session_local_id")
    val trials: List<LocalTestTrialEntity>,
    @Relation(parentColumn = "local_id", entityColumn = "session_local_id")
    val result: LocalTestResultEntity?,
)
