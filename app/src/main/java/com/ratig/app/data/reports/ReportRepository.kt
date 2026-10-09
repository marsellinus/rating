package com.ratig.app.data.reports

import com.ratig.app.core.result.AppResult
import java.time.Instant

/**
 * Filters for the report query. `from`/`to` bound the completed-session date
 * range; department/shift are optional equality filters. Rows are capped
 * (default 2000) because exports are pulled to the device in one request.
 */
data class ReportFilter(
    val from: Instant,
    val to: Instant,
    val departmentId: String? = null,
    val shiftId: String? = null,
    val limit: Int = 2000,
)

/**
 * One denormalized report row per finalized session, sourced from the
 * `v_report_rows` view (worker identity, dates, organization, examiner,
 * metrics, classification, rule version, follow-up status).
 *
 * Lives in the data layer (not domain/) because reports are a feature-scoped
 * read model with no cross-slice consumers.
 */
data class ReportRow(
    val sessionId: String,
    val sessionCreatedAt: Instant? = null,
    val sessionCompletedAt: Instant? = null,
    val workerId: String,
    val workerNumber: String,
    val workerName: String,
    val departmentName: String? = null,
    val workAreaName: String? = null,
    val shiftName: String? = null,
    val examinerName: String? = null,
    val validTrialCount: Int,
    val missedResponseCount: Int = 0,
    val falseStartCount: Int = 0,
    val meanReactionTimeMs: Double? = null,
    val medianReactionTimeMs: Double? = null,
    val minReactionTimeMs: Int? = null,
    val maxReactionTimeMs: Int? = null,
    val standardDeviationMs: Double? = null,
    val slowResponseCount: Int = 0,
    val classificationCode: String? = null,
    val classificationLabel: String? = null,
    val severity: Int? = null,
    val ruleVersion: String? = null,
    val followUpStatus: String? = null,
)

interface ReportRepository {
    suspend fun rows(filter: ReportFilter): AppResult<List<ReportRow>>
}
