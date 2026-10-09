package com.ratig.app.domain.model

import com.ratig.app.core.json.InstantAsStringSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A single measurement attempt inside a session. */
@Serializable
data class TrialRecord(
    val id: String? = null,
    val sessionId: String,
    val trialNumber: Int,
    /** Nanoseconds since boot when the stimulus frame was actually rendered. */
    val stimulusAtMonotonicNs: Long? = null,
    /** Nanoseconds since boot when the tap event was dispatched. */
    val tapAtMonotonicNs: Long? = null,
    /** Computed as tapAt - stimulusAt in ms when valid. */
    val reactionTimeMs: Long? = null,
    val trialStatus: TrialStatus = TrialStatus.INVALID,
    val falseStart: Boolean = false,
    val missedResponse: Boolean = false,
    /** Mode tests: what was shown, e.g. rgb_red / rgb_green / button_A / circle_nogo. */
    val stimulusKind: String? = null,
    /** Mode tests: was this stimulus the target (true) or a distractor (false)? */
    val isTarget: Boolean? = null,
    /** Mode tests: correct | wrong | late | early | none. */
    val responseType: String? = null,
    /** Mode tests: was the response correct for this trial? */
    val responseCorrect: Boolean? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
)

/** One examination session (worker + examiner + protocol + rule). */
@Serializable
data class TestSession(
    val id: String,
    val workerId: String,
    val examinerId: String,
    val shiftId: String? = null,
    val protocolId: String,
    val fatigueRuleId: String? = null,
    val appVersion: String? = null,
    val deviceMetadata: Map<String, String> = emptyMap(),
    @Serializable(with = InstantAsStringSerializer::class)
    val startedAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val completedAt: Instant? = null,
    val sessionStatus: SessionStatus = SessionStatus.CREATED,
    val interruptionReason: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    val testMode: TestMode = TestMode.CLASSIC,
    /** Seeded RNG base - reproduces stimulus randomization for audit. */
    val randomSeed: String? = null,
    /** Frozen copy of the protocol's mode configuration at session start (JSON). */
    val modeConfig: String? = null,
    /** Optional NIK audit snapshot recorded when the session was created. */
    val nikSnapshot: String? = null,
    /** Local-only sync lifecycle (not a server column). */
    val syncStatus: com.ratig.app.core.sync.SyncStatus = com.ratig.app.core.sync.SyncStatus.SYNCED,
    // Denormalized (joined view)
    val workerName: String? = null,
    val workerNumber: String? = null,
    val examinerName: String? = null,
    val protocolName: String? = null,
    val protocolVersion: String? = null,
)

/** Computed metrics for a set of trials (pure computation, unit tested). */
@Serializable
data class TestMetrics(
    val totalTrials: Int,
    val validTrialCount: Int,
    val invalidTrialCount: Int,
    val missedResponseCount: Int,
    val falseStartCount: Int,
    val minReactionTimeMs: Long? = null,
    val maxReactionTimeMs: Long? = null,
    val meanReactionTimeMs: Double? = null,
    val medianReactionTimeMs: Double? = null,
    val standardDeviationMs: Double? = null,
    val slowResponseCount: Int = 0,
    /** VALID trials excluded from RT statistics as device artifacts (audit). */
    val excludedArtifactCount: Int = 0,
    /** Session wall duration (finished - started), informational only. */
    val sessionDurationMs: Long? = null,
)

/** Persisted result row for a session. Raw metrics kept separate from classification. */
@Serializable
data class TestResult(
    val id: String,
    val sessionId: String,
    val metrics: TestMetrics,
    /** Machine code from the applied rule band, e.g. BAND_2 or NOT_CONFIGURED. */
    val classificationCode: String?,
    /** Human label from SOP/validation as stored in the rule. */
    val classificationLabel: String?,
    /** Plain-language explanation incl. which rule version was applied. */
    val classificationExplanation: String? = null,
    val ruleVersion: String? = null,
    /** Observation metrics (mode tests only; null for classic). */
    val accuracyRate: Double? = null,
    val correctResponseRate: Double? = null,
    val falseAlarmRate: Double? = null,
    val omissionRate: Double? = null,
    val testMode: TestMode? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
)

/** Policy-based follow-up recommendation attached to a result band. */
@Serializable
data class FollowUpRecommendation(
    val code: String,
    val title: String,
    val description: String,
)

@Serializable
data class FollowUp(
    val id: String,
    val sessionId: String,
    val actionType: String,
    val notes: String? = null,
    val status: FollowUpStatus = FollowUpStatus.OPEN,
    val assignedTo: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val dueAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val completedAt: Instant? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    // Denormalized
    val workerName: String? = null,
    val assignedToName: String? = null,
)

@Serializable
data class ScheduleEntry(
    val id: String,
    val workerId: String,
    val examinerId: String,
    val shiftId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val scheduledAt: Instant,
    val status: ScheduleStatus = ScheduleStatus.SCHEDULED,
    val notes: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
    // Denormalized
    val workerName: String? = null,
    val examinerName: String? = null,
    val shiftName: String? = null,
)

/** Immutable audit trail row (append-only, admin read-only). */
@Serializable
data class AuditLogEntry(
    val id: String,
    val actorId: String? = null,
    val action: String,
    val entityType: String,
    val entityId: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val occurredAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap(),
    // Denormalized
    val actorName: String? = null,
)
