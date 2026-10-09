package com.ratig.app.data.local

import androidx.room.TypeConverter
import com.ratig.app.core.sync.SyncStatus

/**
 * Room type converters. Only enums are converted - timestamps are already
 * stored as epoch-millis [Long] columns and JSON payloads as [String].
 *
 * Unknown status strings (future app versions, corrupted rows) fall back to
 * [SyncStatus.PENDING] so a row is never silently treated as server-synced.
 */
class Converters {

    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(raw: String?): SyncStatus =
        raw?.let { name -> SyncStatus.entries.firstOrNull { it.name == name } } ?: SyncStatus.PENDING
}
