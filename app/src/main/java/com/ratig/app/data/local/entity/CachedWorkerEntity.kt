package com.ratig.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ratig.app.core.sync.SyncStatus
import com.ratig.app.data.local.OfflineEntity
import com.ratig.app.domain.model.Worker

/**
 * Server-confirmed cache of `workers` rows written after successful list /
 * get / findByNik queries so worker identification keeps working offline.
 *
 * Privacy: NIK values live ONLY in this Room table (internal app storage) -
 * never in SharedPreferences, never in logs. UI layers must mask through
 * `core.identity.NikMask` outside the explicit confirm screen.
 *
 * `nik` is indexed for exact and prefix lookups; rows cached via list/get
 * may carry `nik = null` when the column was not part of the fetched data
 * (those rows are still listed offline, they just cannot match NIK search).
 */
@Entity(
    tableName = CachedWorkerEntity.TABLE_NAME,
    indices = [
        Index(value = ["nik"]),
        Index(value = ["server_id"], unique = true),
        Index(value = ["employee_number"]),
    ],
)
data class CachedWorkerEntity(
    @ColumnInfo(name = "employee_number") val employeeNumber: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "email") val email: String? = null,
    @ColumnInfo(name = "department_id") val departmentId: String? = null,
    @ColumnInfo(name = "work_area_id") val workAreaId: String? = null,
    @ColumnInfo(name = "job_title") val jobTitle: String? = null,
    @ColumnInfo(name = "shift_id") val shiftId: String? = null,
    @ColumnInfo(name = "active_status") val activeStatus: Boolean = true,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    /** Business key (`workers.nik`, digits only); indexed for offline search. */
    @ColumnInfo(name = "nik") val nik: String? = null,
    // Denormalized display names resolved by the repository at fetch time so
    // offline lists render without the organization tables.
    @ColumnInfo(name = "department_name") val departmentName: String? = null,
    @ColumnInfo(name = "work_area_name") val workAreaName: String? = null,
    @ColumnInfo(name = "shift_name") val shiftName: String? = null,
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
    override val protocolVersion: String = OfflineEntity.DEFAULT_PROTOCOL_VERSION,
    @ColumnInfo(name = "payload_version")
    override val payloadVersion: Int = OfflineEntity.PAYLOAD_VERSION,
) : OfflineEntity(localId, serverId, ownerUserId, createdAt, updatedAt, syncStatus, retryCount, lastSyncAttemptAt, lastSyncError, protocolVersion, payloadVersion) {

    fun toDomain(): Worker = Worker(
        id = requireNotNull(serverId) { "Cached worker row without server id" },
        employeeNumber = employeeNumber,
        fullName = fullName,
        email = email,
        departmentId = departmentId,
        workAreaId = workAreaId,
        jobTitle = jobTitle,
        shiftId = shiftId,
        activeStatus = activeStatus,
        userId = userId,
        departmentName = departmentName,
        workAreaName = workAreaName,
        shiftName = shiftName,
    )

    companion object {
        const val TABLE_NAME = "cached_workers"

        fun fromWorker(
            worker: Worker,
            nik: String?,
            ownerUserId: String,
            cachedAt: Long,
        ): CachedWorkerEntity = CachedWorkerEntity(
            employeeNumber = worker.employeeNumber,
            fullName = worker.fullName,
            email = worker.email,
            departmentId = worker.departmentId,
            workAreaId = worker.workAreaId,
            jobTitle = worker.jobTitle,
            shiftId = worker.shiftId,
            activeStatus = worker.activeStatus,
            userId = worker.userId,
            nik = nik,
            departmentName = worker.departmentName,
            workAreaName = worker.workAreaName,
            shiftName = worker.shiftName,
            cachedAt = cachedAt,
            serverId = worker.id,
            ownerUserId = ownerUserId,
        )
    }
}
