package com.ratig.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Application roles. Stored as text in profiles.role; enforced by RLS + RPCs.
 *
 *  - [SUPER_ADMIN]: everything (user/role administration, master data, monitoring)
 *  - [ADMIN]: manage users + monitoring
 *  - [USER]: run tests + view own history
 */
@Serializable
enum class UserRole {
    @SerialName("super_admin") SUPER_ADMIN,
    @SerialName("admin") ADMIN,
    @SerialName("user") USER;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER
    }
}

/** Account lifecycle. New Google accounts start PENDING and need admin approval. */
@Serializable
enum class AccountStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("suspended") SUSPENDED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Serializable
enum class SessionStatus {
    @SerialName("created") CREATED,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("interrupted") INTERRUPTED,
    @SerialName("pending_sync") PENDING_SYNC,
    @SerialName("finalized") FINALIZED,
    @SerialName("failed") FAILED;

    companion object {
        fun fromRaw(raw: String?): SessionStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FAILED
    }

    val isTerminal: Boolean get() = this == FINALIZED || this == FAILED
}

@Serializable
enum class TrialStatus {
    @SerialName("valid") VALID,
    @SerialName("false_start") FALSE_START,
    @SerialName("missed") MISSED,
    @SerialName("invalid") INVALID;

    companion object {
        fun fromRaw(raw: String?): TrialStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: INVALID
    }
}

@Serializable
enum class FollowUpStatus {
    @SerialName("open") OPEN,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED;

    companion object {
        fun fromRaw(raw: String?): FollowUpStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPEN
    }
}

@Serializable
enum class ScheduleStatus {
    @SerialName("scheduled") SCHEDULED,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("needs_repeat") NEEDS_REPEAT;

    companion object {
        fun fromRaw(raw: String?): ScheduleStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SCHEDULED
    }
}

/** Follow-up action types; kept as free values validated by CHECK on server. */
object FollowUpAction {
    const val RECHECK = "recheck"
    const val REST = "rest"
    const val EVALUATION = "evaluation"
    const val ESCALATION = "escalation"
    const val OTHER = "other"
}

/**
 * Test modes. CLASSIC is the reference reaction-time mode used for fatigue
 * classification; the other modes are attention/observation tests whose
 * results are NEVER merged into a fatigue score.
 */
@Serializable
enum class TestMode {
    @SerialName("classic") CLASSIC,
    @SerialName("rgb_random") RGB_RANDOM,
    @SerialName("random_button") RANDOM_BUTTON,
    @SerialName("focus_inhibition") FOCUS_INHIBITION;

    /** Server-side (DB/JSON) value for this mode. */
    val serverValue: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String?): TestMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CLASSIC
    }
}
