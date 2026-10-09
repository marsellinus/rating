package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity
import com.ratig.app.domain.model.ProtocolConfiguration
import com.ratig.app.domain.model.ProtocolStatus
import com.ratig.app.domain.model.TestProtocol
import java.time.Instant

/**
 * Cache of ACTIVE `test_protocols` rows so offline test configuration
 * (trial count, delays, timeout, mode configuration) survives without
 * network. Written by the protocol repository after successful fetches; the
 * active set is replaced atomically (see `ProtocolDao.replaceAll`).
 *
 * `configuration` is the raw jsonb text; parse with
 * [ProtocolConfiguration.fromJson] (never throws - falls back to defaults).
 */
@Entity(
    tableName = CachedProtocolEntity.TABLE_NAME,
    indices = [
        Index(value = ["server_id"], unique = true),
    ],
)
data class CachedProtocolEntity(
    @ColumnInfo(name = "name") val name: String,
    /** The protocol's own version string (also mirrored in the common column). */
    @ColumnInfo(name = "protocol_version_label") val protocolVersionLabel: String,
    @ColumnInfo(name = "trial_count") val trialCount: Int,
    @ColumnInfo(name = "stimulus_delay_min_ms") val stimulusDelayMinMs: Long,
    @ColumnInfo(name = "stimulus_delay_max_ms") val stimulusDelayMaxMs: Long,
    @ColumnInfo(name = "response_timeout_ms") val responseTimeoutMs: Long,
    /** Raw jsonb configuration text (nullable - server default '{}'). */
    @ColumnInfo(name = "configuration") val configuration: String? = null,
    /** Raw wire value ('draft' | 'active' | 'retired'). */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @ColumnInfo(name = "approved_by") val approvedBy: String? = null,
    @ColumnInfo(name = "server_created_at") val serverCreatedAt: Long? = null,
    /** Epoch millis of the successful server fetch that produced this row. */
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
    // Common offline columns (cache rows are server-confirmed by definition).
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    override val localId: String = OfflineEntity.newLocalId(),
    @ColumnInfo(name = "server_id")
    override val serverId: String? = null,
    @ColumnInfo(name = "owner_user_id")
    override val ownerUserId: String = "",
    @ColumnInfo(name = "created_at")
    override val createdAt: Long = cachedAt,
    @ColumnInfo(name = "updated_at")
    override val updatedAt: Long = cachedAt,
    @ColumnInfo(name = "sync_status")
    override val syncStatus: SyncStatus = SyncStatus.SYNCED,
    @ColumnInfo(name = "retry_count")
    override val retryCount: Int = 0,
    @ColumnInfo(name = "last_sync_attempt_at")
    override val lastSyncAttemptAt: Long? = cachedAt,
    @ColumnInfo(name = "last_sync_error")
    override val lastSyncError: String? = null,
    @ColumnInfo(name = "protocol_version")
    override val protocolVersion: String = protocolVersionLabel,
    @ColumnInfo(name = "payload_version")
    override val payloadVersion: Int = OfflineEntity.PAYLOAD_VERSION,
) : OfflineEntity(localId, serverId, ownerUserId, createdAt, updatedAt, syncStatus, retryCount, lastSyncAttemptAt, lastSyncError, protocolVersion, payloadVersion) {

    fun toDomain(): TestProtocol = TestProtocol(
        id = requireNotNull(serverId) { "Cached protocol row without server id" },
        name = name,
        protocolVersion = protocolVersionLabel,
        trialCount = trialCount,
        stimulusDelayMinMs = stimulusDelayMinMs,
        stimulusDelayMaxMs = stimulusDelayMaxMs,
        responseTimeoutMs = responseTimeoutMs,
        configuration = ProtocolConfiguration.fromJson(configuration),
        status = ProtocolStatus.fromRaw(status),
        createdBy = createdBy,
        approvedBy = approvedBy,
        createdAt = serverCreatedAt?.let(Instant::ofEpochMilli),
    )

    companion object {
        const val TABLE_NAME = "cached_protocols"

        fun fromProtocol(
            protocol: TestProtocol,
            ownerUserId: String,
            cachedAt: Long,
        ): CachedProtocolEntity = CachedProtocolEntity(
            name = protocol.name,
            protocolVersionLabel = protocol.protocolVersion,
            trialCount = protocol.trialCount,
            stimulusDelayMinMs = protocol.stimulusDelayMinMs,
            stimulusDelayMaxMs = protocol.stimulusDelayMaxMs,
            responseTimeoutMs = protocol.responseTimeoutMs,
            configuration = ProtocolConfiguration.toJson(protocol.configuration),
            status = protocol.status.name.lowercase(),
            createdBy = protocol.createdBy,
            approvedBy = protocol.approvedBy,
            serverCreatedAt = protocol.createdAt?.toEpochMilli(),
            cachedAt = cachedAt,
            serverId = protocol.id,
            ownerUserId = ownerUserId,
            protocolVersion = protocol.protocolVersion,
        )
    }
}
