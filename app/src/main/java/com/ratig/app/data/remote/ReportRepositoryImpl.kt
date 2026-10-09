package com.ratig.app.data.remote

import com.ratig.app.core.result.AppResult
import com.ratig.app.data.remote.dto.ReportRowDto
import com.ratig.app.data.reports.ReportFilter
import com.ratig.app.data.reports.ReportRepository
import com.ratig.app.data.reports.ReportRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Report read model sourced from the `v_report_rows` view with date range +
 * department/shift equality filters, capped at [ReportFilter.limit] rows
 * (default 2000) since exports pull the full result set to the device.
 */
@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : ReportRepository {

    override suspend fun rows(request: ReportFilter): AppResult<List<ReportRow>> = AppResult.of {
        supabase.postgrest[V_REPORT_ROWS].select {
            filter {
                gte("session_completed_at", request.from.toString())
                lte("session_completed_at", request.to.toString())
                if (request.departmentId != null) eq("department_id", request.departmentId)
                if (request.shiftId != null) eq("shift_id", request.shiftId)
            }
            order("session_completed_at", Order.DESCENDING)
            range(0, (request.limit - 1).toLong())
        }.decodeList<ReportRowDto>().map { it.toDomain() }
    }

    private companion object {
        const val V_REPORT_ROWS = "v_report_rows"
    }
}
