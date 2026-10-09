package com.ratig.app.domain.model

import com.ratig.app.core.json.InstantAsStringSerializer
import com.ratig.app.core.json.JsonCodec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Test protocol: HOW the reaction-time test is executed on the device.
 * Everything that affects measurement is versioned and stored per session.
 */
@Serializable
data class TestProtocol(
    val id: String,
    val name: String,
    val protocolVersion: String,
    val trialCount: Int,
    val stimulusDelayMinMs: Long,
    val stimulusDelayMaxMs: Long,
    /** Maximum wait for a response after stimulus; beyond this the trial is MISSED. */
    val responseTimeoutMs: Long,
    /** Extra validated configuration (e.g. min interval between trials). */
    val configuration: ProtocolConfiguration = ProtocolConfiguration(),
    val status: ProtocolStatus = ProtocolStatus.DRAFT,
    val createdBy: String? = null,
    val approvedBy: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
)

@Serializable
enum class ProtocolStatus {
    @SerialName("draft") DRAFT,
    @SerialName("active") ACTIVE,
    @SerialName("retired") RETIRED;

    companion object {
        fun fromRaw(raw: String?): ProtocolStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DRAFT
    }
}

@Serializable
data class ProtocolConfiguration(
    /** Minimum gap between two trials, keeps the test humane and repeatable. */
    val interTrialDelayMs: Long = 800L,
    /** Show an optional practice trial set before the counted trials. */
    val practiceTrialCount: Int = 0,
    /** Reject reaction times faster than humanly plausible (false-start guard). */
    val minPlausibleReactionMs: Long = 80L,
    /**
     * Mode-specific configuration for non-classic test modes (TestModes slice);
     * null for classic-only protocols. Serialized as the nested `mode` object
     * of the `configuration` jsonb; omitted when null (explicitNulls=false).
     */
    val mode: com.ratig.app.core.timing.modes.ModeConfiguration? = null,
) {
    companion object {
        fun fromJson(json: String?): ProtocolConfiguration =
            if (json.isNullOrBlank()) ProtocolConfiguration()
            else runCatching { JsonCodec.decode<ProtocolConfiguration>(json) }
                .getOrDefault(ProtocolConfiguration())

        fun toJson(value: ProtocolConfiguration): String = JsonCodec.encode(value)
    }
}

/**
 * Fatigue classification rules: WHICH band a result falls into and what the
 * band means. Bands and labels are configuration - the defaults from the
 * project reference (<240, 240-<410, 410-<580, >=580 ms) are seeded with
 * placeholder labels until the organization validates them.
 */
@Serializable
data class FatigueRule(
    val id: String,
    val protocolId: String,
    val ruleVersion: String,
    val config: RuleConfig,
    @Serializable(with = InstantAsStringSerializer::class)
    val effectiveFrom: Instant,
    @Serializable(with = InstantAsStringSerializer::class)
    val effectiveUntil: Instant? = null,
    val approvalStatus: ApprovalStatus = ApprovalStatus.DRAFT,
    val createdBy: String? = null,
    val approvedBy: String? = null,
    @Serializable(with = InstantAsStringSerializer::class)
    val createdAt: Instant? = null,
)

@Serializable
enum class ApprovalStatus {
    @SerialName("draft") DRAFT,
    @SerialName("approved") APPROVED,
    @SerialName("retired") RETIRED;

    companion object {
        fun fromRaw(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DRAFT
    }
}

/** Which computed metric drives the classification band lookup. */
@Serializable
enum class ClassificationLogic {
    @SerialName("mean_ms_band") MEAN_MS_BAND,
    @SerialName("median_ms_band") MEDIAN_MS_BAND,
    @SerialName("slow_count_band") SLOW_COUNT_BAND;

    companion object {
        fun fromRaw(raw: String?): ClassificationLogic =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEAN_MS_BAND
    }
}

@Serializable
data class RuleConfig(
    /** Ordered ascending by minMs; last band must be open-ended. */
    val bands: List<RuleBand>,
    /** Reaction times >= this are counted as "slow responses". */
    val slowResponseThresholdMs: Long = 580L,
    val logic: ClassificationLogic = ClassificationLogic.MEAN_MS_BAND,
    /** For SLOW_COUNT_BAND logic: number of slow responses per band index. */
    val slowCountBands: List<SlowCountBand> = emptyList(),
    /** Minimum valid trials required for classification to be attempted. */
    val minValidTrials: Int = 4,
    /** Explicit exclusion policy - nothing is silently discarded. */
    val excludeFalseStarts: Boolean = true,
    /** Reaction times above this are excluded as device artifacts (documented). */
    val excludeReactionAboveMs: Long? = 2000L,
) {
    fun toJson(): String = JsonCodec.encode(this)

    companion object {
        fun fromJson(json: String?): RuleConfig =
            if (json.isNullOrBlank()) defaultUnvalidated() 
            else runCatching { JsonCodec.decode<RuleConfig>(json) }.getOrDefault(defaultUnvalidated())

        /** Four reference bands with labels intentionally unvalidated. */
        fun defaultUnvalidated(): RuleConfig = RuleConfig(
            bands = listOf(
                RuleBand(minMs = 0, maxMsExclusive = 240, code = "BAND_1", label = "Perlu validasi", severity = 1),
                RuleBand(minMs = 240, maxMsExclusive = 410, code = "BAND_2", label = "Perlu validasi", severity = 2),
                RuleBand(minMs = 410, maxMsExclusive = 580, code = "BAND_3", label = "Perlu validasi", severity = 3),
                RuleBand(minMs = 580, maxMsExclusive = null, code = "BAND_4", label = "Perlu validasi", severity = 4),
            ),
        )
    }
}

@Serializable
data class RuleBand(
    val minMs: Long,
    val maxMsExclusive: Long?,
    /** Stable machine code stored in test_results.classification_code. */
    val code: String,
    /** Human label from SOP/validation - displayed to users. */
    val label: String,
    /** 0 = best ... N = worst; drives color coding (color is never the only cue). */
    val severity: Int,
) {
    fun contains(value: Long): Boolean =
        value >= minMs && (maxMsExclusive == null || value < maxMsExclusive)
}

@Serializable
data class SlowCountBand(
    val minSlowCount: Int,
    val code: String,
    val label: String,
    val severity: Int,
)
