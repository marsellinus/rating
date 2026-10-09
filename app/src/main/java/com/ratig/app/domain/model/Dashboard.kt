package com.ratig.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Aggregate statistics models. Dashboards fetch ONLY aggregates via RPC/views
 * - raw rows are never pulled to the device for statistics.
 */
@Serializable
data class AdminDashboardStats(
    val activeWorkers: Long,
    val totalUsers: Long,
    val pendingApprovals: Long,
    val totalSessions: Long,
    val sessionsToday: Long,
    val unfinishedSessions: Long,
    val openFollowUps: Long,
    val classificationCounts: List<ClassificationCount>,
    val byDepartment: List<NamedCount>,
    val byShift: List<NamedCount>,
    val trend: List<DailyCount>,
    val sampleSize: Long,
)

@Serializable
data class ManagementDashboardStats(
    val totalSessions: Long,
    val sessionsToday: Long,
    val openFollowUps: Long,
    val classificationCounts: List<ClassificationCount>,
    val byDepartment: List<NamedCount>,
    val byShift: List<NamedCount>,
    val trend: List<DailyCount>,
    val sampleSize: Long,
)

@Serializable
data class ExaminerDashboardStats(
    val scheduledToday: Long,
    val sessionsToday: Long,
    val sessionsLast7Days: Long,
    val openFollowUps: Long,
    val recentResults: List<RecentResult>,
)

@Serializable
data class ClassificationCount(
    val code: String,
    val label: String,
    val count: Long,
    val severity: Int,
)

@Serializable
data class NamedCount(val name: String, val count: Long)

@Serializable
data class DailyCount(val day: String, val count: Long)

@Serializable
data class RecentResult(
    val sessionId: String,
    val workerName: String,
    val completedAt: String?,
    val classificationCode: String?,
    val classificationLabel: String?,
    val severity: Int,
    val meanReactionTimeMs: Double?,
)

/** Display model for trend charts of reaction time over time. */
@Serializable
data class ReactionTrendPoint(
    val date: String,
    val meanMs: Double?,
    val sampleCount: Long,
)
