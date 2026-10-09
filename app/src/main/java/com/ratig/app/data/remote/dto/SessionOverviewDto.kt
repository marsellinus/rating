package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.TestSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Row of the `v_session_overview` view: test_sessions columns plus the
 * denormalized names/classification used by history lists.
 *
 * Shared by the workers and history features. `device_metadata` and other
 * non-mapped session columns are intentionally not declared
 * (`ignoreUnknownKeys = true` on the shared Json).
 */
@Serializable
data class SessionOverviewDto(
    val id: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("examiner_id") val examinerId: String? = null,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("fatigue_rule_id") val fatigueRuleId: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("started_at") val startedAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("completed_at") val completedAt: Instant? = null,
    @SerialName("session_status") val sessionStatus: String? = null,
    @SerialName("interruption_reason") val interruptionReason: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
    // Joined view columns
    @SerialName("worker_name") val workerName: String? = null,
    @SerialName("worker_number") val workerNumber: String? = null,
    @SerialName("examiner_name") val examinerName: String? = null,
    @SerialName("protocol_name") val protocolName: String? = null,
    @SerialName("protocol_version") val protocolVersion: String? = null,
    @SerialName("classification_code") val classificationCode: String? = null,
    @SerialName("classification_label") val classificationLabel: String? = null,
    val severity: Int? = null,
    @SerialName("mean_reaction_time_ms") val meanReactionTimeMs: Double? = null,
)

fun SessionOverviewDto.toDomain(): TestSession = TestSession(
    id = id,
    workerId = workerId,
    examinerId = examinerId.orEmpty(),
    shiftId = shiftId,
    protocolId = protocolId,
    fatigueRuleId = fatigueRuleId,
    appVersion = appVersion,
    deviceMetadata = emptyMap(),
    startedAt = startedAt,
    completedAt = completedAt,
    sessionStatus = SessionStatus.fromRaw(sessionStatus),
    interruptionReason = interruptionReason,
    createdAt = createdAt,
    workerName = workerName,
    workerNumber = workerNumber,
    examinerName = examinerName,
    protocolName = protocolName,
    protocolVersion = protocolVersion,
)
