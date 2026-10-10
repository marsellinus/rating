package com.ratig.app.domain.repository

import com.ratig.app.core.result.AppResult
import com.ratig.app.domain.model.AdminDashboardStats
import com.ratig.app.domain.model.AuditLogEntry
import com.ratig.app.domain.model.ExaminerDashboardStats
import com.ratig.app.domain.model.ManagementDashboardStats
import com.ratig.app.domain.model.ReactionTrendPoint
import com.ratig.app.domain.model.ScheduleEntry
import com.ratig.app.domain.model.ScheduleStatus
import java.time.Instant

interface DashboardRepository {
    suspend fun adminStats(): AppResult<AdminDashboardStats>
    suspend fun managementStats(from: Instant, to: Instant): AppResult<ManagementDashboardStats>
    suspend fun examinerStats(): AppResult<ExaminerDashboardStats>
    /** Management trend chart data (mean reaction time per day in range). */
    suspend fun reactionTrend(from: Instant, to: Instant, departmentId: String?): AppResult<List<ReactionTrendPoint>>
    /** Export finalized tests as an Excel or CSV file byte array. */
    suspend fun exportReport(from: String, to: String, format: String = "csv"): AppResult<ByteArray>
}

interface ScheduleRepository {
    suspend fun list(from: Instant, to: Instant, examinerId: String? = null): AppResult<List<ScheduleEntry>>
    suspend fun listUpcoming(limit: Int = 30): AppResult<List<ScheduleEntry>>
    suspend fun create(entry: ScheduleEntry): AppResult<ScheduleEntry>
    suspend fun updateStatus(id: String, status: ScheduleStatus): AppResult<Unit>
    suspend fun update(entry: ScheduleEntry): AppResult<ScheduleEntry>
}

interface AuditRepository {
    /** Admin-only read access; writes happen exclusively via DB triggers/RPCs. */
    suspend fun list(
        action: String? = null,
        entityType: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): AppResult<List<AuditLogEntry>>
}
