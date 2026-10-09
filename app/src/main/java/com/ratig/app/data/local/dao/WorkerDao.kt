package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ratig.app.data.local.entity.CachedWorkerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Cache of `workers` rows. NIK is a TEXT business key - never convert to a
 * number (leading zeros). Searches use indexed exact/prefix matches only.
 */
@Dao
interface WorkerDao {

    @Upsert
    suspend fun upsert(worker: CachedWorkerEntity)

    @Upsert
    suspend fun upsertAll(workers: List<CachedWorkerEntity>)

    @Query("SELECT * FROM cached_workers WHERE server_id = :serverId LIMIT 1")
    suspend fun byServerId(serverId: String): CachedWorkerEntity?

    /** Exact NIK match (the identification flow's primary lookup). */
    @Query("SELECT * FROM cached_workers WHERE nik = :nik LIMIT 1")
    suspend fun byNik(nik: String): CachedWorkerEntity?

    /** Offline NIK prefix search, freshest cache rows first. */
    @Query(
        "SELECT * FROM cached_workers WHERE nik LIKE :prefix || '%' " +
            "ORDER BY cached_at DESC, full_name ASC LIMIT :limit",
    )
    suspend fun searchByNikPrefix(prefix: String, limit: Int = 20): List<CachedWorkerEntity>

    /** Offline name / employee-number search (SQLite LIKE, case-insensitive). */
    @Query(
        "SELECT * FROM cached_workers WHERE full_name LIKE '%' || :query || '%' " +
            "OR employee_number LIKE '%' || :query || '%' " +
            "ORDER BY full_name ASC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int = 20): List<CachedWorkerEntity>

    @Query("SELECT * FROM cached_workers ORDER BY full_name ASC LIMIT :limit OFFSET :offset")
    suspend fun list(limit: Int = 50, offset: Int = 0): List<CachedWorkerEntity>

    @Query("SELECT COUNT(*) FROM cached_workers")
    fun count(): Flow<Int>

    @Query("DELETE FROM cached_workers WHERE nik = :nik")
    suspend fun deleteByNik(nik: String)

    @Query("DELETE FROM cached_workers")
    suspend fun clearAll()
}
