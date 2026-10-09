package com.ratig.app.domain.repository

import com.ratig.app.core.device.DeviceMetadata
import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.FollowUp
import com.ratig.app.domain.model.FollowUpStatus
import com.ratig.app.domain.model.FatigueRule
import com.ratig.app.domain.model.ProtocolStatus
import com.ratig.app.domain.model.TestMode
import com.ratig.app.domain.model.TestProtocol
import com.ratig.app.domain.model.TestResult
import com.ratig.app.domain.model.TestSession
import com.ratig.app.domain.model.TrialRecord
import kotlinx.serialization.Serializable

/** Payload sent to the server-side finalize RPC. */
@Serializable
data class FinalizeRequest(
    val sessionId: String,
    val trials: List<TrialRecord>,
    val appVersion: String,
    val deviceMetadata: DeviceMetadata,
)

interface ProtocolRepository {
    suspend fun listActiveProtocols(): AppResult<List<TestProtocol>>
    suspend fun listAllProtocols(): AppResult<List<TestProtocol>>
    suspend fun getProtocol(id: String): AppResult<TestProtocol>
    /** Admin-only create/update (draft). Activation requires approval. */
    suspend fun upsertProtocol(protocol: TestProtocol): AppResult<TestProtocol>
    suspend fun setProtocolStatus(id: String, status: ProtocolStatus): AppResult<Unit>

    /** Approved rule currently effective for a protocol (fallback: latest approved). */
    suspend fun effectiveRule(protocolId: String): AppResult<FatigueRule?>
    suspend fun listRules(protocolId: String? = null): AppResult<List<FatigueRule>>
    /** Admin-only upsert (draft); approval separate. */
    suspend fun upsertRule(rule: FatigueRule): AppResult<FatigueRule>
    suspend fun approveRule(ruleId: String): AppResult<Unit>
    suspend fun approveProtocol(protocolId: String): AppResult<Unit>
}

interface TestSessionRepository {
    /** Creates the session row (status CREATED) before the test starts. */
    suspend fun createSession(
        workerId: String,
        protocolId: String,
        fatigueRuleId: String?,
        shiftId: String?,
        testMode: TestMode = TestMode.CLASSIC,
        randomSeed: String? = null,
        modeConfig: String? = null,
        nikSnapshot: String? = null,
    ): AppResult<TestSession>

    suspend fun markInProgress(sessionId: String): AppResult<Unit>
    suspend fun markInterrupted(sessionId: String, reason: String): AppResult<Unit>
    suspend fun markFailed(sessionId: String, reason: String): AppResult<Unit>

    /**
     * Finalizes the session via the `finalize_test_session` RPC: the server
     * recomputes metrics + classification from the approved rule inside one
     * transaction and persists trials + result + status. Idempotent.
     */
    suspend fun finalizeSession(request: FinalizeRequest): AppResult<TestResult>

    suspend fun getSession(id: String): AppResult<TestSession>
    suspend fun getTrials(sessionId: String): AppResult<List<TrialRecord>>
    suspend fun getResult(sessionId: String): AppResult<TestResult?>

    /** Paginated history with filters (server-side). */
    suspend fun history(
        workerQuery: String? = null,
        departmentId: String? = null,
        shiftId: String? = null,
        examinerId: String? = null,
        status: com.ratig.app.domain.model.SessionStatus? = null,
        classificationCode: String? = null,
        from: java.time.Instant? = null,
        to: java.time.Instant? = null,
        limit: Int = 30,
        offset: Int = 0,
    ): AppResult<List<TestSession>>
}

interface FollowUpRepository {
    suspend fun list(
        onlyOpen: Boolean = false,
        workerQuery: String? = null,
        limit: Int = 30,
        offset: Int = 0,
    ): AppResult<List<FollowUp>>

    suspend fun listForSession(sessionId: String): AppResult<List<FollowUp>>
    suspend fun create(followUp: FollowUp): AppResult<FollowUp>
    suspend fun updateStatus(id: String, status: FollowUpStatus): AppResult<Unit>
}
