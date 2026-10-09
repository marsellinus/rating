package com.ratig.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ratig.app.data.local.dao.AuditEventDao
import com.ratig.app.data.local.dao.ProfileDao
import com.ratig.app.data.local.dao.ProtocolDao
import com.ratig.app.data.local.dao.SessionDao
import com.ratig.app.data.local.dao.SyncQueueDao
import com.ratig.app.data.local.dao.WorkerDao
import com.ratig.app.data.local.entity.CachedProfileEntity
import com.ratig.app.data.local.entity.CachedWorkerEntity
import com.ratig.app.data.local.entity.CachedProtocolEntity
import com.ratig.app.data.local.entity.LocalAuditEventEntity
import com.ratig.app.data.local.entity.LocalTestResultEntity
import com.ratig.app.data.local.entity.LocalTestSessionEntity
import com.ratig.app.data.local.entity.LocalTestTrialEntity
import com.ratig.app.data.local.entity.SyncQueueEntity

/**
 * The offline-first Room database (`ratig.db`, version 1, internal storage).
 *
 * Nothing in this database is authoritative: Room writes are PENDING until
 * the server confirms them (see [OfflineEntity] invariants). The database
 * contains worker/NIK data and therefore lives only in the app's internal
 * storage (Room default) - never on removable storage, never in logs.
 */
@Database(
    entities = [
        CachedProfileEntity::class,
        CachedWorkerEntity::class,
        CachedProtocolEntity::class,
        LocalTestSessionEntity::class,
        LocalTestTrialEntity::class,
        LocalTestResultEntity::class,
        SyncQueueEntity::class,
        LocalAuditEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RatigDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    abstract fun workerDao(): WorkerDao

    abstract fun protocolDao(): ProtocolDao

    abstract fun sessionDao(): SessionDao

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun auditEventDao(): AuditEventDao

    companion object {
        const val NAME: String = "ratig.db"
    }
}
