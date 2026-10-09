package com.ratig.app.data.local

import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.ratig.app.core.sync.SyncStatus
import java.util.UUID

/**
 * Common offline-first columns shared by every entity of [RatigDatabase]
 * (CONTRACT-2 §Offline-first). Room persists inherited constructor properties,
 * so each concrete entity only declares its domain columns.
 *
 * Invariants:
 *  - `local_id` is always device-generated (UUID v4); it is the sole primary
 *    key and stable across sync attempts.
 *  - `server_id` is set ONLY after the server confirmed persistence.
 *  - `sync_status = SYNCED` is written ONLY after server confirmation
 *    (REST insert/update success or finalize RPC 200). A plain Room save is
 *    always `PENDING` ("Tersimpan di perangkat").
 *  - `last_sync_error` contains fixed Indonesian reason strings only - never
 *    raw exception text, tokens or NIK values.
 *  - `protocol_version` records the RATIG protocol version that produced the
 *    row ("1" when not protocol-specific); `payload_version` is the local
 *    schema/shape version of the stored payload.
 */
abstract class OfflineEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    open val localId: String = newLocalId(),
    /** Server row id once confirmed; NULL for never-synced local records. */
    @ColumnInfo(name = "server_id")
    open val serverId: String? = null,
    /** auth.users id of the device owner at write time ("" when unknown). */
    @ColumnInfo(name = "owner_user_id")
    open val ownerUserId: String,
    @ColumnInfo(name = "created_at")
    open val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    open val updatedAt: Long,
    @ColumnInfo(name = "sync_status")
    open val syncStatus: SyncStatus,
    @ColumnInfo(name = "retry_count")
    open val retryCount: Int = 0,
    @ColumnInfo(name = "last_sync_attempt_at")
    open val lastSyncAttemptAt: Long? = null,
    @ColumnInfo(name = "last_sync_error")
    open val lastSyncError: String? = null,
    @ColumnInfo(name = "protocol_version")
    open val protocolVersion: String = DEFAULT_PROTOCOL_VERSION,
    @ColumnInfo(name = "payload_version")
    open val payloadVersion: Int = PAYLOAD_VERSION,
) {
    companion object {
        const val PAYLOAD_VERSION: Int = 1
        const val DEFAULT_PROTOCOL_VERSION: String = "1"

        fun newLocalId(): String = UUID.randomUUID().toString()
    }
}
