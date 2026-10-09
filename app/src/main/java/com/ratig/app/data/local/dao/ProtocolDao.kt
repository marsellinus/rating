package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ratig.app.data.local.entity.CachedProtocolEntity
import kotlinx.coroutines.flow.Flow

/**
 * Cache of ACTIVE test protocols for offline test configuration. The whole
 * active set is replaced atomically on refresh so retired/deactivated
 * protocols cannot linger and be used for new sessions.
 */
@Dao
interface ProtocolDao {

    /** Deletes the cached active set, then inserts the fresh one, atomically. */
    @Transaction
    suspend fun replaceAll(protocols: List<CachedProtocolEntity>) {
        clearAll()
        if (protocols.isNotEmpty()) upsertAll(protocols)
    }

    @Upsert
    suspend fun upsert(protocol: CachedProtocolEntity)

    @Upsert
    suspend fun upsertAll(protocols: List<CachedProtocolEntity>)

    @Query("SELECT * FROM cached_protocols WHERE status = 'active' ORDER BY name ASC")
    suspend fun activeProtocols(): List<CachedProtocolEntity>

    @Query("SELECT * FROM cached_protocols WHERE status = 'active' ORDER BY name ASC")
    fun observeActiveProtocols(): Flow<List<CachedProtocolEntity>>

    @Query("SELECT * FROM cached_protocols WHERE server_id = :serverId LIMIT 1")
    suspend fun byServerId(serverId: String): CachedProtocolEntity?

    /** Newest cache timestamp across rows (freshness anchor for the cache). */
    @Query("SELECT MAX(cached_at) FROM cached_protocols")
    suspend fun latestCachedAt(): Long?

    @Query("DELETE FROM cached_protocols WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM cached_protocols")
    suspend fun clearAll()
}
