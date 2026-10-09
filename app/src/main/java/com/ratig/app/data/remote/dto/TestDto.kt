package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.core.json.NullableJsonbAsStringSerializer
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.TestMetrics
import com.ratig.app.domain.model.TestResult
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.model.TrialStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * DTOs for `test_sessions`, `test_trials` and `test_results`. Column names are
 * snake_case exactly as in the database; denormalized view fields
 * (`v_session_overview`) are optional so the same DTO decodes base-table rows.
 */

@Serializable
data class TestSessionDto(
    val id: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("examiner_id") val examinerId: String,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("fatigue_rule_id") val fatigueRuleId: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("device_metadata") val deviceMetadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("started_at") @Serializable(with = InstantAsStringSerializer::class)
    val startedAt: Instant? = null,
    @SerialName("completed_at") @Serializable(with = InstantAsStringSerializer::class)
    val completedAt: Instant? = null,
    @SerialName("session_status") val sessionStatus: SessionStatus = SessionStatus.CREATED,
    @SerialName("interruption_reason") val interruptionReason: String? = null,
    @SerialName("created_at") @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    @SerialName("test_mode") val testMode: String = "classic",
    @SerialName("random_seed") val randomSeed: String? = null,
    @SerialName("mode_config")
    @Serializable(with = NullableJsonbAsStringSerializer::class)
    val modeConfig: String? = null,
    @SerialName("nik_snapshot") val nikSnapshot: String? = null,
    // Denormalized (v_session_overview only)
    @SerialName("worker_name") val workerName: String? = null,
    @SerialName("worker_number") val workerNumber: String? = null,
    @SerialName("examiner_name") val examinerName: String? = null,
    @SerialName("protocol_name") val protocolName: String? = null,
    @SerialName("protocol_version") val protocolVersion: String? = null,
) {
    fun toDomain(): TestSession = TestSession(
        id = id,
        workerId = workerId,
        examinerId = examinerId,
        shiftId = shiftId,
        protocolId = protocolId,
        fatigueRuleId = fatigueRuleId,
        appVersion = appVersion,
        deviceMetadata = deviceMetadata.toStringMap(),
        startedAt = startedAt,
        completedAt = completedAt,
        sessionStatus = sessionStatus,
        interruptionReason = interruptionReason,
        createdAt = createdAt,
        testMode = TestMode.fromRaw(testMode),
        randomSeed = randomSeed,
        modeConfig = modeConfig,
        nikSnapshot = nikSnapshot,
        workerName = workerName,
        workerNumber = workerNumber,
        examinerName = examinerName,
        protocolName = protocolName,
        protocolVersion = protocolVersion,
    )
}

/** Insert payload for a new session row (status 'created', no server-generated id). */
@Serializable
data class TestSessionInsertDto(
    @SerialName("worker_id") val workerId: String,
    @SerialName("examiner_id") val examinerId: String,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("fatigue_rule_id") val fatigueRuleId: String? = null,
    @SerialName("app_version") val appVersion: String,
    @SerialName("device_metadata") val deviceMetadata: JsonObject,
    @SerialName("session_status") val sessionStatus: String = "created",
    @SerialName("test_mode") val testMode: String = "classic",
    @SerialName("random_seed") val randomSeed: String? = null,
    @SerialName("mode_config")
    @Serializable(with = NullableJsonbAsStringSerializer::class)
    val modeConfig: String? = null,
    @SerialName("nik_snapshot") val nikSnapshot: String? = null,
)

@Serializable
data class TrialDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("trial_number") val trialNumber: Int,
    @SerialName("stimulus_at_monotonic_ns") val stimulusAtMonotonicNs: Long? = null,
    @SerialName("tap_at_monotonic_ns") val tapAtMonotonicNs: Long? = null,
    @SerialName("reaction_time_ms") val reactionTimeMs: Long? = null,
    @SerialName("trial_status") val trialStatus: TrialStatus = TrialStatus.INVALID,
    @SerialName("false_start") val falseStart: Boolean = false,
    @SerialName("missed_response") val missedResponse: Boolean = false,
    @SerialName("stimulus_kind") val stimulusKind: String? = null,
    @SerialName("is_target") val isTarget: Boolean? = null,
    @SerialName("response_type") val responseType: String? = null,
    @SerialName("response_correct") val responseCorrect: Boolean? = null,
    @SerialName("created_at") @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
) {
    fun toDomain(): TrialRecord = TrialRecord(
        id = id,
        sessionId = sessionId,
        trialNumber = trialNumber,
        stimulusAtMonotonicNs = stimulusAtMonotonicNs,
        tapAtMonotonicNs = tapAtMonotonicNs,
        reactionTimeMs = reactionTimeMs,
        trialStatus = trialStatus,
        falseStart = falseStart,
        missedResponse = missedResponse,
        stimulusKind = stimulusKind,
        isTarget = isTarget,
        responseType = responseType,
        responseCorrect = responseCorrect,
        createdAt = createdAt,
    )
}

/** Insert payload for `test_trials` (also the shape of finalize `p_trials`). */
@Serializable
data class TrialInsertDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("trial_number") val trialNumber: Int,
    @SerialName("stimulus_at_monotonic_ns") val stimulusAtMonotonicNs: Long? = null,
    @SerialName("tap_at_monotonic_ns") val tapAtMonotonicNs: Long? = null,
    @SerialName("reaction_time_ms") val reactionTimeMs: Long? = null,
    @SerialName("trial_status") val trialStatus: String,
    @SerialName("false_start") val falseStart: Boolean = false,
    @SerialName("missed_response") val missedResponse: Boolean = false,
    @SerialName("stimulus_kind") val stimulusKind: String? = null,
    @SerialName("is_target") val isTarget: Boolean? = null,
    @SerialName("response_type") val responseType: String? = null,
    @SerialName("response_correct") val responseCorrect: Boolean? = null,
) {
    companion object {
        fun from(record: TrialRecord, statusWire: String): TrialInsertDto = TrialInsertDto(
            sessionId = record.sessionId,
            trialNumber = record.trialNumber,
            stimulusAtMonotonicNs = record.stimulusAtMonotonicNs,
            tapAtMonotonicNs = record.tapAtMonotonicNs,
            reactionTimeMs = record.reactionTimeMs,
            trialStatus = statusWire,
            falseStart = record.falseStart,
            missedResponse = record.missedResponse,
            stimulusKind = record.stimulusKind,
            isTarget = record.isTarget,
            responseType = record.responseType,
            responseCorrect = record.responseCorrect,
        )
    }
}

@Serializable
data class TestResultDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("valid_trial_count") val validTrialCount: Int,
    @SerialName("invalid_trial_count") val invalidTrialCount: Int = 0,
    @SerialName("missed_response_count") val missedResponseCount: Int = 0,
    @SerialName("false_start_count") val falseStartCount: Int = 0,
    @SerialName("mean_reaction_time_ms") val meanReactionTimeMs: Double? = null,
    @SerialName("median_reaction_time_ms") val medianReactionTimeMs: Double? = null,
    @SerialName("min_reaction_time_ms") val minReactionTimeMs: Long? = null,
    @SerialName("max_reaction_time_ms") val maxReactionTimeMs: Long? = null,
    @SerialName("standard_deviation_ms") val standardDeviationMs: Double? = null,
    @SerialName("slow_response_count") val slowResponseCount: Int = 0,
    @SerialName("excluded_artifact_count") val excludedArtifactCount: Int = 0,
    @SerialName("classification_code") val classificationCode: String? = null,
    @SerialName("classification_label") val classificationLabel: String? = null,
    @SerialName("classification_explanation") val classificationExplanation: String? = null,
    @SerialName("rule_version") val ruleVersion: String? = null,
    // Mode-test observation rates (null for classic sessions).
    @SerialName("accuracy_rate") val accuracyRate: Double? = null,
    @SerialName("correct_response_rate") val correctResponseRate: Double? = null,
    @SerialName("false_alarm_rate") val falseAlarmRate: Double? = null,
    @SerialName("omission_rate") val omissionRate: Double? = null,
    @SerialName("test_mode") val testMode: String? = null,
    @SerialName("created_at") @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
) {
    fun toDomain(): TestResult = TestResult(
        id = id,
        sessionId = sessionId,
        metrics = TestMetrics(
            totalTrials = validTrialCount + invalidTrialCount,
            validTrialCount = validTrialCount,
            invalidTrialCount = invalidTrialCount,
            missedResponseCount = missedResponseCount,
            falseStartCount = falseStartCount,
            minReactionTimeMs = minReactionTimeMs,
            maxReactionTimeMs = maxReactionTimeMs,
            meanReactionTimeMs = meanReactionTimeMs,
            medianReactionTimeMs = medianReactionTimeMs,
            standardDeviationMs = standardDeviationMs,
            slowResponseCount = slowResponseCount,
            excludedArtifactCount = excludedArtifactCount,
            sessionDurationMs = null,
        ),
        classificationCode = classificationCode,
        classificationLabel = classificationLabel,
        classificationExplanation = classificationExplanation,
        ruleVersion = ruleVersion,
        accuracyRate = accuracyRate,
        correctResponseRate = correctResponseRate,
        falseAlarmRate = falseAlarmRate,
        omissionRate = omissionRate,
        testMode = TestMode.fromRaw(testMode),
        createdAt = createdAt,
    )
}

private fun JsonObject.toStringMap(): Map<String, String> = buildMap {
    for ((key, element) in this@toStringMap) {
        this[key] = (element as? JsonPrimitive)?.content ?: element.toString()
    }
}
