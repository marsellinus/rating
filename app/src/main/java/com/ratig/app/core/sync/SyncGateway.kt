package com.ratig.app.core.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server side of the sync engine (CONTRACT-2 §Sync engine). Pure network
 * calls - no Room access, no state. The worker drives the algorithm:
 *
 *  1. REST-insert the session row carrying the CLIENT UUID as `id`
 *     (PostgREST allows explicit PKs). A 409 means the row already exists
 *     from an earlier attempt -> idempotent "AlreadyExists", continue.
 *  2. `finalize_test_session` (idempotent, server-authoritative): recomputes
 *     metrics/classification, inserts trials + result, finalizes the session.
 *     200 returns the stored `test_results` row.
 *
 * Failure handling NEVER deletes local data and NEVER reports success without
 * a confirmed 2xx. Error reasons are fixed Indonesian strings (see
 * [SyncFailure]) - tokens, NIK and raw server bodies never leave this class.
 */
@Singleton
class SyncGateway @Inject constructor(
    private val supabase: SupabaseClient,
    private val json: Json,
) {

    sealed interface SessionInsertResult {
        /** Row created with the client UUID as primary key. */
        data object Inserted : SessionInsertResult

        /** 409 - row already exists from an earlier attempt; safe to continue. */
        data object AlreadyExists : SessionInsertResult

        data class Failed(val failure: SyncFailure) : SessionInsertResult
    }

    sealed interface FinalizeResult {
        /** Server-confirmed result row (values are authoritative). */
        data class Success(val result: ServerResultDto) : FinalizeResult

        data class Failed(val failure: SyncFailure) : FinalizeResult
    }

    /** Creates (or finds existing) `test_sessions` row for a pending local session. */
    suspend fun insertSessionRow(payload: SyncSessionInsertDto): SessionInsertResult =
        try {
            supabase.postgrest["test_sessions"].insert(payload)
            SessionInsertResult.Inserted
        } catch (e: PostgrestRestException) {
            if (e.statusCode == HTTP_CONFLICT) {
                SessionInsertResult.AlreadyExists
            } else {
                SessionInsertResult.Failed(e.toSyncFailure())
            }
        } catch (e: RestException) {
            SessionInsertResult.Failed(e.toSyncFailure())
        } catch (t: Throwable) {
            SessionInsertResult.Failed(SyncFailure.from(t))
        }

    /**
     * Calls `finalize_test_session(p_session_id, p_trials, p_app_version,
     * p_device_metadata)`. `p_trials` carries every recorded trial including
     * the mode fields (stimulus_kind / is_target / response_type /
     * response_correct); classic sessions simply omit the mode keys.
     */
    suspend fun finalizeSession(
        sessionId: String,
        trials: List<SyncTrialPayloadDto>,
        appVersion: String,
        deviceMetadata: JsonObject,
    ): FinalizeResult =
        try {
            val args = buildJsonObject {
                put("p_session_id", sessionId)
                put("p_trials", json.encodeToJsonElement(trials))
                put("p_app_version", appVersion)
                put("p_device_metadata", deviceMetadata)
            }
            val result = supabase.postgrest.rpc("finalize_test_session", args)
                .decodeAs<ServerResultDto>()
            FinalizeResult.Success(result)
        } catch (e: PostgrestRestException) {
            FinalizeResult.Failed(e.toSyncFailure())
        } catch (e: RestException) {
            FinalizeResult.Failed(e.toSyncFailure())
        } catch (t: Throwable) {
            FinalizeResult.Failed(SyncFailure.from(t))
        }

    private fun RestException.toSyncFailure(): SyncFailure {
        val statusCode = (this as? PostgrestRestException)?.statusCode ?: 0
        val pgCode = (this as? PostgrestRestException)?.code
        return when {
            pgCode == "42501" -> SyncFailure.Forbidden
            statusCode == 401 -> SyncFailure.Auth
            statusCode == 403 -> SyncFailure.Forbidden
            statusCode == 400 -> SyncFailure.Validation(pgCode)
            else -> SyncFailure.Unexpected()
        }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
