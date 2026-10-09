package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.ScheduleEntry
import com.ratig.app.domain.model.ScheduleStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Row of the `v_schedule_overview` view: schedules.* enriched with
 * worker_name, examiner_name and shift_name for list display.
 */
@Serializable
data class ScheduleDto(
    val id: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("examiner_id") val examinerId: String,
    @SerialName("shift_id") val shiftId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("scheduled_at") val scheduledAt: Instant,
    val status: String,
    val notes: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("updated_at") val updatedAt: Instant? = null,
    // Denormalized display columns from the view
    @SerialName("worker_name") val workerName: String? = null,
    @SerialName("examiner_name") val examinerName: String? = null,
    @SerialName("shift_name") val shiftName: String? = null,
) {
    fun toDomain(): ScheduleEntry = ScheduleEntry(
        id = id,
        workerId = workerId,
        examinerId = examinerId,
        shiftId = shiftId,
        scheduledAt = scheduledAt,
        status = ScheduleStatus.fromRaw(status),
        notes = notes,
        createdAt = createdAt,
        workerName = workerName,
        examinerName = examinerName,
        shiftName = shiftName,
    )
}

/** Payload for INSERT into the `schedules` table (reads go through the view). */
@Serializable
data class ScheduleInsertDto(
    @SerialName("worker_id") val workerId: String,
    @SerialName("examiner_id") val examinerId: String,
    @SerialName("shift_id") val shiftId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("scheduled_at") val scheduledAt: Instant,
    val status: String = ScheduleStatus.SCHEDULED.name.lowercase(),
    val notes: String? = null,
)
