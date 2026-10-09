package com.ratig.app.data.remote

import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.json.JsonCodec
import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.data.remote.dto.TestResultDto
import com.ratig.app.data.remote.dto.TestSessionDto
import com.ratig.app.data.remote.dto.TestSessionInsertDto
import com.ratig.app.data.remote.dto.TrialDto
import com.ratig.app.domain.model.SessionStatus
import com.ratig.app.domain.model.TestResult
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.TrialRecord
import com.ratig.app.domain.repository.FinalizeRequest
import com.ratig.app.domain.repository.TestSessionRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed [TestSessionRepository]. No Log calls: unit-test friendly
 * (`unitTests.isReturnDefaultValues`). All failures flow through
 * [AppResult.of] -> [AppError].
 */
@Singleton
class TestSessionRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : TestSessionRepository {

    override suspend fun createSession(
        workerId: String,
        protocolId: String,
        fatigueRuleId: String?,
        shiftId: String?,
        testMode: TestMode,
        randomSeed: String?,
        modeConfig: String?,
        nikSnapshot: String?,
    ): AppResult<TestSession> = AppResult.of {
        val examinerId = supabase.auth.currentUserOrNull()?.id
            ?: error("Pengguna belum masuk; sesi hanya dapat dibuat oleh petugas.")
        val device = DeviceMetadata.current()
        val row = supabase.postgrest["test_sessions"].insert(
            TestSessionInsertDto(
                workerId = workerId,
                examinerId = examinerId,
                shiftId = shiftId,
                protocolId = protocolId,
                fatigueRuleId = fatigueRuleId,
                appVersion = device.appVersionName,
                deviceMetadata = device.toJsonObject(),
                sessionStatus = SESSION_STATUS_CREATED,
                testMode = testMode.serverValue,
                randomSeed = randomSeed,
                modeConfig = modeConfig,
                nikSnapshot = nikSnapshot,
            ),
        ) { select() }.decodeSingle<TestSessionDto>()
        row.toDomain()
    }

    override suspend fun markInProgress(sessionId: String): AppResult<Unit> = AppResult.of {
        supabase.postgrest["test_sessions"].update({
            set("session_status", SESSION_STATUS_IN_PROGRESS)
            set("started_at", TimeProvider.nowUtc().toString())
        }) { filter { eq("id", sessionId) } }
    }

    override suspend fun markInterrupted(sessionId: String, reason: String): AppResult<Unit> =
        AppResult.of {
            supabase.postgrest["test_sessions"].update({
                set("session_status", SESSION_STATUS_INTERRUPTED)
                set("interruption_reason", reason)
            }) { filter { eq("id", sessionId) } }
        }

    override suspend fun markFailed(sessionId: String, reason: String): AppResult<Unit> =
        AppResult.of {
            supabase.postgrest["test_sessions"].update({
                set("session_status", SESSION_STATUS_FAILED)
                set("interruption_reason", reason)
            }) { filter { eq("id", sessionId) } }
        }

    override suspend fun finalizeSession(request: FinalizeRequest): AppResult<TestResult> =
        AppResult.of {
            val trialsJson = buildJsonArray { request.trials.forEach { add(it.toServerJson()) } }
            val payload = supabase.postgrest.rpc(
                function = "finalize_test_session",
                parameters = buildJsonObject {
                    put("p_session_id", request.sessionId)
                    put("p_trials", trialsJson)
                    put("p_app_version", request.appVersion)
                    put("p_device_metadata", request.deviceMetadata.toJsonObject())
                },
            ).decodeAs<JsonElement>()
            // The RPC returns the persisted test_results row (snake_case columns,
            // recomputed server-side from the approved rule; idempotent).
            JsonCodec.json.decodeFromJsonElement(TestResultDto.serializer(), payload).toDomain()
        }

    override suspend fun getSession(id: String): AppResult<TestSession> = AppResult.of {
        val rows = supabase.postgrest["test_sessions"].select {
            filter { eq("id", id) }
        }.decodeList<TestSessionDto>()
        rows.firstOrNull()?.toDomain() ?: error("Sesi tidak ditemukan.")
    }

    override suspend fun getTrials(sessionId: String): AppResult<List<TrialRecord>> = AppResult.of {
        supabase.postgrest["test_trials"].select {
            filter { eq("session_id", sessionId) }
            order("trial_number", Order.ASCENDING)
        }.decodeList<TrialDto>().map { it.toDomain() }
    }

    override suspend fun getResult(sessionId: String): AppResult<TestResult?> = AppResult.of {
        supabase.postgrest["test_results"].select {
            filter { eq("session_id", sessionId) }
        }.decodeList<TestResultDto>().firstOrNull()?.toDomain()
    }

    override suspend fun history(
        workerQuery: String?,
        departmentId: String?,
        shiftId: String?,
        examinerId: String?,
        status: SessionStatus?,
        classificationCode: String?,
        from: Instant?,
        to: Instant?,
        limit: Int,
        offset: Int,
    ): AppResult<List<TestSession>> = AppResult.of {
        supabase.postgrest["v_session_overview"].select {
            filter {
                if (!workerQuery.isNullOrBlank()) ilike("worker_name", "%$workerQuery%")
                if (departmentId != null) eq("department_id", departmentId)
                if (shiftId != null) eq("shift_id", shiftId)
                if (examinerId != null) eq("examiner_id", examinerId)
                if (status != null) eq("session_status", status.name.lowercase())
                if (classificationCode != null) eq("classification_code", classificationCode)
                if (from != null) gte("started_at", from.toString())
                if (to != null) lte("completed_at", to.toString())
            }
            range(offset.toLong(), (offset + limit - 1).toLong())
            order("created_at", Order.DESCENDING)
        }.decodeList<TestSessionDto>().map { it.toDomain() }
    }

    /** Serializes one trial into the shape expected by `finalize_test_session(p_trials)`. */
    private fun TrialRecord.toServerJson() = buildJsonObject {
        put("trial_number", trialNumber)
        if (stimulusAtMonotonicNs != null) put("stimulus_at_monotonic_ns", stimulusAtMonotonicNs)
        if (tapAtMonotonicNs != null) put("tap_at_monotonic_ns", tapAtMonotonicNs)
        if (reactionTimeMs != null) put("reaction_time_ms", reactionTimeMs)
        put("trial_status", trialStatus.wireValue())
        put("false_start", falseStart)
        put("missed_response", missedResponse)
        // Mode columns (null for classic trials -> omitted).
        if (stimulusKind != null) put("stimulus_kind", stimulusKind)
        if (isTarget != null) put("is_target", isTarget)
        if (responseType != null) put("response_type", responseType)
        if (responseCorrect != null) put("response_correct", responseCorrect)
    }

    private fun com.ratig.app.domain.model.TrialStatus.wireValue(): String = when (this) {
        com.ratig.app.domain.model.TrialStatus.VALID -> "valid"
        com.ratig.app.domain.model.TrialStatus.FALSE_START -> "false_start"
        com.ratig.app.domain.model.TrialStatus.MISSED -> "missed"
        com.ratig.app.domain.model.TrialStatus.INVALID -> "invalid"
    }

    private fun DeviceMetadata.toJsonObject() = buildJsonObject {
        put("manufacturer", manufacturer)
        put("model", model)
        put("android_sdk_int", androidSdkInt)
        put("android_release", androidRelease)
        put("app_version_name", appVersionName)
        put("app_version_code", appVersionCode)
    }

    private companion object {
        const val SESSION_STATUS_CREATED = "created"
        const val SESSION_STATUS_IN_PROGRESS = "in_progress"
        const val SESSION_STATUS_INTERRUPTED = "interrupted"
        const val SESSION_STATUS_FAILED = "failed"
    }
}
