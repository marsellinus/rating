package com.ratig.app.data.remote

import com.ratig.app.core.result.AppResult
import com.ratig.app.core.time.TimeProvider
import com.ratig.app.domain.model.AdminDashboardStats
import com.ratig.app.domain.model.ClassificationCount
import com.ratig.app.domain.model.DailyCount
import com.ratig.app.domain.model.ExaminerDashboardStats
import com.ratig.app.domain.model.ManagementDashboardStats
import com.ratig.app.domain.model.NamedCount
import com.ratig.app.domain.model.ReactionTrendPoint
import com.ratig.app.domain.model.RecentResult
import com.ratig.app.domain.repository.DashboardRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Dashboard aggregates come exclusively from server-side RPCs returning jsonb
 * (single round trip, aggregates only - no raw rows). The JSON keys produced by
 * the SQL functions are snake_case while the domain models are camelCase, so
 * responses are mapped through small tolerant [JsonObject] parsers instead of
 * direct serialization: missing keys degrade to zero/empty, never crash.
 */
@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : DashboardRepository {

    override suspend fun adminStats(): AppResult<AdminDashboardStats> = AppResult.of {
        parseAdminStats(rpcJson("dashboard_admin", buildJsonObject { }).jsonObject)
    }

    override suspend fun managementStats(from: Instant, to: Instant): AppResult<ManagementDashboardStats> =
        AppResult.of {
            val args = buildJsonObject {
                put("p_from", TimeProvider.operationalDay(from).toString())
                put("p_to", TimeProvider.operationalDay(to).toString())
            }
            parseManagementStats(rpcJson("dashboard_management", args).jsonObject)
        }

    override suspend fun examinerStats(): AppResult<ExaminerDashboardStats> = AppResult.of {
        parseExaminerStats(rpcJson("dashboard_examiner", buildJsonObject { }).jsonObject)
    }

    override suspend fun reactionTrend(
        from: Instant,
        to: Instant,
        departmentId: String?,
    ): AppResult<List<ReactionTrendPoint>> = AppResult.of {
        val args = buildJsonObject {
            put("p_from", TimeProvider.operationalDay(from).toString())
            put("p_to", TimeProvider.operationalDay(to).toString())
            if (departmentId != null) put("p_department_id", departmentId)
        }
        rpcJson("reaction_trend", args).jsonArray.mapNotNull { element ->
            (element as? JsonObject)?.let { parseTrendPoint(it) }
        }
    }

    override suspend fun exportReport(from: String, to: String, format: String): AppResult<ByteArray> = AppResult.of {
        val args = buildJsonObject {
            put("from", from)
            put("to", to)
            put("format", format)
        }
        val response: HttpResponse = supabase.functions.invoke("export-report", args)
        if (response.status.value !in 200..299) {
            throw Exception("Gagal mengunduh laporan (Status: ${response.status.value})")
        }
        response.readBytes()
    }

    private suspend fun rpcJson(function: String, args: JsonObject): JsonElement =
        supabase.postgrest.rpc(function, args).decodeAs<JsonElement>()

    // --- Tolerant jsonb -> domain parsers (snake_case first, camelCase fallback) ---

    private fun JsonObject.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        }

    private fun JsonObject.num(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toLongOrNull()
        }

    private fun JsonObject.dec(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toDoubleOrNull()
        }

    private fun JsonElement?.asObjectList(): List<JsonObject> =
        (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun parseAdminStats(o: JsonObject): AdminDashboardStats = AdminDashboardStats(
        activeWorkers = o.num("active_workers", "activeWorkers") ?: 0L,
        totalUsers = o.num("total_users", "totalUsers") ?: 0L,
        pendingApprovals = o.num("pending_approvals", "pendingApprovals") ?: 0L,
        totalSessions = o.num("total_sessions", "totalSessions") ?: 0L,
        sessionsToday = o.num("sessions_today", "sessionsToday") ?: 0L,
        unfinishedSessions = o.num("unfinished_sessions", "unfinishedSessions") ?: 0L,
        openFollowUps = o.num("open_follow_ups", "openFollowUps") ?: 0L,
        classificationCounts = o["classification_counts"].asObjectList().map(::parseClassification),
        byDepartment = o["by_department"].asObjectList().map(::parseNamedCount),
        byShift = o["by_shift"].asObjectList().map(::parseNamedCount),
        trend = o["trend"].asObjectList().map(::parseDailyCount),
        sampleSize = o.num("sample_size", "sampleSize") ?: 0L,
    )

    private fun parseManagementStats(o: JsonObject): ManagementDashboardStats = ManagementDashboardStats(
        totalSessions = o.num("total_sessions", "totalSessions") ?: 0L,
        sessionsToday = o.num("sessions_today", "sessionsToday") ?: 0L,
        openFollowUps = o.num("open_follow_ups", "openFollowUps") ?: 0L,
        classificationCounts = o["classification_counts"].asObjectList().map(::parseClassification),
        byDepartment = o["by_department"].asObjectList().map(::parseNamedCount),
        byShift = o["by_shift"].asObjectList().map(::parseNamedCount),
        trend = o["trend"].asObjectList().map(::parseDailyCount),
        sampleSize = o.num("sample_size", "sampleSize") ?: 0L,
    )

    private fun parseExaminerStats(o: JsonObject): ExaminerDashboardStats = ExaminerDashboardStats(
        scheduledToday = o.num("scheduled_today", "scheduledToday") ?: 0L,
        sessionsToday = o.num("sessions_today", "sessionsToday") ?: 0L,
        sessionsLast7Days = o.num("sessions_last_7_days", "sessionsLast7Days") ?: 0L,
        openFollowUps = o.num("open_follow_ups", "openFollowUps") ?: 0L,
        recentResults = o["recent_results"].asObjectList().mapNotNull(::parseRecentResult),
    )

    private fun parseClassification(o: JsonObject): ClassificationCount = ClassificationCount(
        code = o.text("code", "classification_code") ?: "UNKNOWN",
        label = o.text("label", "classification_label") ?: o.text("code", "classification_code") ?: "-",
        count = o.num("count") ?: 0L,
        severity = (o.num("severity") ?: -1L).toInt(),
    )

    private fun parseNamedCount(o: JsonObject): NamedCount = NamedCount(
        name = o.text("name", "department", "department_name", "shift", "shift_name", "label") ?: "-",
        count = o.num("count", "sessions", "value", "total") ?: 0L,
    )

    private fun parseDailyCount(o: JsonObject): DailyCount = DailyCount(
        day = o.text("day", "date", "day_date") ?: "",
        count = o.num("count", "sessions", "value", "total") ?: 0L,
    )

    private fun parseRecentResult(o: JsonObject): RecentResult? {
        val sessionId = o.text("session_id", "sessionId") ?: return null
        return RecentResult(
            sessionId = sessionId,
            workerName = o.text("worker_name", "workerName") ?: "-",
            completedAt = o.text("completed_at", "completedAt"),
            classificationCode = o.text("classification_code", "classificationCode"),
            classificationLabel = o.text("classification_label", "classificationLabel"),
            severity = (o.num("severity") ?: -1L).toInt(),
            meanReactionTimeMs = o.dec("mean_reaction_time_ms", "meanReactionTimeMs"),
        )
    }

    private fun parseTrendPoint(o: JsonObject): ReactionTrendPoint? {
        val date = o.text("date", "day") ?: return null
        return ReactionTrendPoint(
            date = date,
            meanMs = o.dec("mean_ms", "meanMs", "mean_reaction_time_ms"),
            sampleCount = o.num("sample_count", "sampleCount", "count") ?: 0L,
        )
    }
}
