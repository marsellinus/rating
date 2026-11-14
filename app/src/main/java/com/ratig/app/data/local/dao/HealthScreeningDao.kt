package com.ratig.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ratig.app.data.local.entity.LocalHealthScreeningEntity
import kotlinx.coroutines.flow.Flow

/** Device-local paramedic screening, one row per examination session. */
@Dao
interface HealthScreeningDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(screening: LocalHealthScreeningEntity)

    @Query("SELECT * FROM local_health_screenings WHERE session_local_id = :sessionLocalId")
    suspend fun findBySession(sessionLocalId: String): LocalHealthScreeningEntity?

    @Query("SELECT * FROM local_health_screenings WHERE session_local_id = :sessionLocalId")
    fun observeBySession(sessionLocalId: String): Flow<LocalHealthScreeningEntity?>
}
