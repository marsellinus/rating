package com.ratig.app.data.remote.dto

import com.ratig.app.core.json.JsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Regression: the 7-day report preview failed with a serialization error. */
class ReportRowDtoTest {

    @Test
    fun `a real v_report_rows row decodes`() {
        val json = """
        {"session_id":"3a188c54-cc43-4dcf-822c-b0135c8682fd","session_created_at":"2026-10-09T04:17:01.700081+00:00","session_completed_at":"2026-10-09T04:23:39.218289+00:00","worker_number":"EMP-TEST-001","worker_name":"Budi Test Pekerja","valid_trial_count":10,"mean_reaction_time_ms":345.0,"median_reaction_time_ms":345,"min_reaction_time_ms":300,"max_reaction_time_ms":390,"standard_deviation_ms":30.276503540974918,"severity":2}
        """.trimIndent()
        val row = JsonCodec.decode<ReportRowDto>(json)
        assertEquals("EMP-TEST-001", row.workerNumber)
        assertNotNull(row.sessionCreatedAt)
        assertEquals(2, row.severity)
    }

    @Test
    fun `a row with null metrics decodes`() {
        val json = """
        {"session_id":"776a101f-f1c2-4b3c-9d42-b04a50c8fbf8","session_created_at":null,"session_completed_at":"2026-11-20T11:23:00+00:00","worker_number":"","worker_name":"X","valid_trial_count":0,"min_reaction_time_ms":null,"max_reaction_time_ms":null,"mean_reaction_time_ms":null,"median_reaction_time_ms":null,"standard_deviation_ms":null,"severity":null}
        """.trimIndent()
        val row = JsonCodec.decode<ReportRowDto>(json)
        assertEquals(null, row.meanReactionTimeMs)
        assertEquals(null, row.severity)
    }
}
