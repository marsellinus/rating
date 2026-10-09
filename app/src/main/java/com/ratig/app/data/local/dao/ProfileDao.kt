package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ratig.app.data.local.entity.CachedProfileEntity
import kotlinx.coroutines.flow.Flow

/** Cache of `profiles` rows (server-confirmed fetches only). */
@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsert(profile: CachedProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<CachedProfileEntity>)

    @Query("SELECT * FROM cached_profiles WHERE server_id = :serverId LIMIT 1")
    suspend fun byServerId(serverId: String): CachedProfileEntity?

    @Query("SELECT * FROM cached_profiles WHERE server_id = :serverId LIMIT 1")
    fun observeByServerId(serverId: String): Flow<CachedProfileEntity?>

    /**
     * Most recently cached profile on the device. Single-user-device
     * assumption: used only when no explicit user id is available (offline
     * restore path); see [com.ratig.app.data.offline.OfflineGate].
     */
    @Query("SELECT * FROM cached_profiles ORDER BY cached_at DESC LIMIT 1")
    suspend fun latestCached(): CachedProfileEntity?

    @Query("SELECT COUNT(*) FROM cached_profiles")
    suspend fun count(): Int

    @Query("DELETE FROM cached_profiles")
    suspend fun clearAll()
}
