package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.ratig.app.data.local.OfflineEntity
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.domain.model.AccountStatus
import com.ratig.app.domain.model.UserProfile
import com.ratig.app.domain.model.UserRole

/**
 * Server-confirmed cache of one `profiles` row (the device owner's own
 * profile and, transiently, profiles fetched by admin flows). Written only
 * after a successful server fetch, so rows are always [SyncStatus.SYNCED].
 *
 * `cached_at` doubles as the lastSync anchor used by the offline gate
 * (validity window) - see [com.ratig.app.data.offline.OfflineGate].
 */
@Entity(
    tableName = CachedProfileEntity.TABLE_NAME,
    indices = [
        Index(value = ["server_id"], unique = true),
    ],
)
data class CachedProfileEntity(
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "email") val email: String,
    /** Raw wire value ('admin' | 'examiner' | 'worker' | 'management'). */
    @ColumnInfo(name = "role") val role: String,
    /** Raw wire value ('pending' | 'active' | 'suspended'). */
    @ColumnInfo(name = "account_status") val accountStatus: String,
    /** Epoch millis of the successful server fetch that produced this row. */
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
    // Common offline columns (profile rows are server-confirmed by definition).
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
    override val protocolVersion: String = OfflineEntity.DEFAULT_PROTOCOL_VERSION,
    @ColumnInfo(name = "payload_version")
    override val payloadVersion: Int = OfflineEntity.PAYLOAD_VERSION,
) : OfflineEntity(localId, serverId, ownerUserId, createdAt, updatedAt, syncStatus, retryCount, lastSyncAttemptAt, lastSyncError, protocolVersion, payloadVersion) {

    fun toDomain(): UserProfile = UserProfile(
        id = requireNotNull(serverId) { "Cached profile row without server id" },
        fullName = fullName,
        email = email,
        role = UserRole.fromRaw(role),
        accountStatus = AccountStatus.fromRaw(accountStatus),
    )

    companion object {
        const val TABLE_NAME = "cached_profiles"

        fun fromProfile(
            profile: UserProfile,
            ownerUserId: String,
            cachedAt: Long,
        ): CachedProfileEntity = CachedProfileEntity(
            fullName = profile.fullName,
            email = profile.email,
            role = profile.role.name.lowercase(),
            accountStatus = profile.accountStatus.name.lowercase(),
            cachedAt = cachedAt,
            serverId = profile.id,
            ownerUserId = ownerUserId,
        )
    }
}
