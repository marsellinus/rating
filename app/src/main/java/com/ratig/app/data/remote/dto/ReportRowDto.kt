package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.data.reports.ReportRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Row of the `v_report_rows` view: one denormalized row per finalized session
 * covering worker identity, dates, organization, examiner, metrics,
 * classification, rule version and follow-up status.
 */
@Serializable
data class ReportRowDto(
    @SerialName("session_id") val sessionId: String,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("session_created_at") val sessionCreatedAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    @SerialName("session_completed_at") val sessionCompletedAt: Instant? = null,
    @SerialName("worker_id") val workerId: String,
    @SerialName("worker_number") val workerNumber: String,
    @SerialName("worker_name") val workerName: String,
    @SerialName("department_name") val departmentName: String? = null,
    @SerialName("work_area_name") val workAreaName: String? = null,
    @SerialName("shift_name") val shiftName: String? = null,
    @SerialName("examiner_name") val examinerName: String? = null,
    @SerialName("valid_trial_count") val validTrialCount: Int = 0,
    @SerialName("missed_response_count") val missedResponseCount: Int = 0,
    @SerialName("false_start_count") val falseStartCount: Int = 0,
    @SerialName("mean_reaction_time_ms") val meanReactionTimeMs: Double? = null,
    @SerialName("median_reaction_time_ms") val medianReactionTimeMs: Double? = null,
    @SerialName("min_reaction_time_ms") val minReactionTimeMs: Int? = null,
    @SerialName("max_reaction_time_ms") val maxReactionTimeMs: Int? = null,
    @SerialName("standard_deviation_ms") val standardDeviationMs: Double? = null,
    @SerialName("slow_response_count") val slowResponseCount: Int = 0,
    @SerialName("classification_code") val classificationCode: String? = null,
    @SerialName("classification_label") val classificationLabel: String? = null,
    val severity: Int? = null,
    @SerialName("rule_version") val ruleVersion: String? = null,
    @SerialName("follow_up_status") val followUpStatus: String? = null,
) {
    fun toDomain(): ReportRow = ReportRow(
        sessionId = sessionId,
        sessionCreatedAt = sessionCreatedAt,
        sessionCompletedAt = sessionCompletedAt,
        workerId = workerId,
        workerNumber = workerNumber,
        workerName = workerName,
        departmentName = departmentName,
        workAreaName = workAreaName,
        shiftName = shiftName,
        examinerName = examinerName,
        validTrialCount = validTrialCount,
        missedResponseCount = missedResponseCount,
        falseStartCount = falseStartCount,
        meanReactionTimeMs = meanReactionTimeMs,
        medianReactionTimeMs = medianReactionTimeMs,
        minReactionTimeMs = minReactionTimeMs,
        maxReactionTimeMs = maxReactionTimeMs,
        standardDeviationMs = standardDeviationMs,
        slowResponseCount = slowResponseCount,
        classificationCode = classificationCode,
        classificationLabel = classificationLabel,
        severity = severity,
        ruleVersion = ruleVersion,
        followUpStatus = followUpStatus,
    )
}
