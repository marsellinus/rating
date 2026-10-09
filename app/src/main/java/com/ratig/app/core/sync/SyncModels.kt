package com.ratig.app.core.sync

import com.ratig.app.core.json.NullableJsonbAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * Public sync state surfaced to the UI (dashboard card + sync status screen).
 * Produced by [SyncStatusBus]; counts come from Room flows, connectivity from
 * [com.ratig.app.data.offline.NetworkMonitor], timestamps/errors from the worker.
 *
 * Count semantics (documented once, used everywhere):
 *  - [pendingSessions]: sessions not yet confirmed by the server and still
 *    eligible for automatic retry (PENDING / SYNCING / FAILED_RETRYABLE).
 *  - [pendingTrials]: trial rows whose session is not SYNCED yet.
 *  - [failedCount]: sessions that will NOT be retried automatically
 *    (FAILED_PERMANENT / NEEDS_REVIEW) - these need administrator review.
 */
data class SyncSnapshot(
    val isOnline: Boolean = false,
    val pendingSessions: Int = 0,
    val pendingTrials: Int = 0,
    val failedCount: Int = 0,
    val lastSyncAt: Instant? = null,
    val lastSyncError: String? = null,
) {
    val hasWork: Boolean get() = pendingSessions > 0 || failedCount > 0
}

/** Per-item retry budget inside [SyncWorker] before FAILED_PERMANENT. */
const val SYNC_MAX_RETRIES: Int = 5

/**
 * Exact Indonesian sync vocabulary (CONTRACT-2 §E). [isOnline] disambiguates
 * PENDING: saved-but-waiting on device vs. explicitly waiting for a network.
 */
fun SyncStatus.displayLabel(isOnline: Boolean): String = when (this) {
    SyncStatus.PENDING -> if (isOnline) "Tersimpan di perangkat" else "Menunggu koneksi"
    SyncStatus.SYNCING -> "Sedang disinkronkan"
    SyncStatus.SYNCED -> "Tersinkronisasi ke server"
    SyncStatus.FAILED_RETRYABLE -> "Gagal, akan dicoba kembali"
    SyncStatus.FAILED_PERMANENT, SyncStatus.NEEDS_REVIEW -> "Perlu pemeriksaan administrator"
}

// ---------------------------------------------------------------------
// Wire payloads (sync-only; self-contained so the engine never depends on
// UI-flow DTOs). Column/field names are snake_case exactly as PostgREST and
// the `finalize_test_session` RPC expect them.
// ---------------------------------------------------------------------

/** Insert payload for `test_sessions` carrying the CLIENT UUID as primary key. */
@Serializable
data class SyncSessionInsertDto(
    @SerialName("id") val id: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("examiner_id") val examinerId: String,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("fatigue_rule_id") val fatigueRuleId: String? = null,
    @SerialName("app_version") val appVersion: String,
    @SerialName("device_metadata") val deviceMetadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    // Finalize flips this to 'finalized'; keeps the constraint valid meanwhile.
    @SerialName("session_status") val sessionStatus: String = "pending_sync",
    @SerialName("test_mode") val testMode: String = "classic",
    @SerialName("random_seed") val randomSeed: String? = null,
    @SerialName("mode_config")
    @Serializable(with = NullableJsonbAsStringSerializer::class)
    val modeConfig: String? = null,
    @SerialName("nik_snapshot") val nikSnapshot: String? = null,
)

/** One element of the `p_trials` jsonb array sent to `finalize_test_session`. */
@Serializable
data class SyncTrialPayloadDto(
    @SerialName("trial_number") val trialNumber: Int,
    @SerialName("stimulus_at_monotonic_ns") val stimulusAtMonotonicNs: Long? = null,
    @SerialName("tap_at_monotonic_ns") val tapAtMonotonicNs: Long? = null,
    @SerialName("reaction_time_ms") val reactionTimeMs: Long? = null,
    @SerialName("trial_status") val trialStatus: String = "invalid",
    @SerialName("false_start") val falseStart: Boolean = false,
    @SerialName("missed_response") val missedResponse: Boolean = false,
    // Mode tests (classic leaves these out - explicitNulls=false drops them).
    @SerialName("stimulus_kind") val stimulusKind: String? = null,
    @SerialName("is_target") val isTarget: Boolean? = null,
    @SerialName("response_type") val responseType: String? = null,
    @SerialName("response_correct") val responseCorrect: Boolean? = null,
)

/**
 * The jsonb row returned by `finalize_test_session` (the server-computed
 * `test_results` row). Server values are authoritative and overwrite the
 * locally computed ones.
 */
@Serializable
data class ServerResultDto(
    @SerialName("id") val id: String,
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
    @SerialName("rule_id") val ruleId: String? = null,
    @SerialName("rule_version") val ruleVersion: String? = null,
    @SerialName("accuracy_rate") val accuracyRate: Double? = null,
    @SerialName("correct_response_rate") val correctResponseRate: Double? = null,
    @SerialName("false_alarm_rate") val falseAlarmRate: Double? = null,
    @SerialName("omission_rate") val omissionRate: Double? = null,
    @SerialName("test_mode") val testMode: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ---------------------------------------------------------------------
// Failure taxonomy. Every case carries a FIXED Indonesian reason string -
// raw exception text, SQL fragments, tokens and NIK values must never reach
// the UI, Room's last_sync_error column, or logs.
// ---------------------------------------------------------------------

sealed class SyncFailure(val retryable: Boolean, val reason: String) {

    /** IOException / timeout / DNS. Retried with backoff. */
    data object Network : SyncFailure(retryable = true, reason = "Koneksi jaringan bermasalah")

    /** 401 - token refresh will fix it; retried with backoff, no retry-count penalty. */
    data object Auth : SyncFailure(retryable = true, reason = "Sesi berakhir, menunggu penyegaran sesi")

    /** 403 / Postgres 42501 (RLS). Never auto-retried; administrator must review. */
    data object Forbidden : SyncFailure(retryable = false, reason = "Akses ditolak server")

    /** 400 / Postgres constraint codes. Never auto-retried; data needs inspection. */
    data class Validation(val code: String?) : SyncFailure(
        retryable = false,
        reason = when (code) {
            "23503" -> "Data terkait (pekerja/protokol/aturan) tidak ditemukan di server"
            "23514" -> "Data tidak memenuhi aturan yang berlaku di server"
            "22001" -> "Panjang nilai melebihi batas kolom di server"
            else -> "Data ditolak server saat validasi"
        },
    )

    /** Non-2xx/409 responses and unexpected shapes. Treated as review-needed. */
    data class Unexpected(val detail: String = "Kesalahan tak terduga saat sinkronisasi") :
        SyncFailure(retryable = false, reason = "Kesalahan tak terduga saat sinkronisasi")

    companion object {
        /** Maps a thrown error to a [SyncFailure] without leaking its message. */
        fun from(t: Throwable): SyncFailure {
            val marker = (t.message ?: "").lowercase()
            return when {
                t is java.io.IOException ||
                    t is io.ktor.client.plugins.HttpRequestTimeoutException ||
                    marker.contains("unable to resolve host") ||
                    marker.contains("failed to connect") ||
                    marker.contains("timeout") -> Network
                marker.contains("401") || marker.contains("jwt") || marker.contains("unauthorized") -> Auth
                marker.contains("42501") ||
                    marker.contains("row-level security") ||
                    marker.contains("permission denied") ||
                    marker.contains("403") -> Forbidden
                marker.contains("23503") || marker.contains("foreign key") -> Validation("23503")
                marker.contains("23514") || marker.contains("check constraint") -> Validation("23514")
                marker.contains("22001") || marker.contains("value too long") -> Validation("22001")
                marker.contains("400") || marker.contains("invalid input") || marker.contains("violates") ->
                    Validation(null)
                else -> Unexpected()
            }
        }
    }
}
