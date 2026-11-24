package com.ratig.app.data.remote

import com.ratig.app.core.json.JsonCodec
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression: dashboard_admin returns camelCase keys while the parser used to
 * read only snake_case, so classification and department charts were blank.
 */
class DashboardParserTest {

    @Test
    fun `objectList accepts camelCase dashboard keys`() {
        val json = """
        {"classificationCounts":[{"code":"BAND_2","label":"Fatigue Ringan","count":3,"severity":2}],
         "byDepartment":[{"name":"K3L","count":10}],
         "byShift":[{"name":"Malam","count":3}],
         "trend":[{"day":"2026-11-24","count":4}]}
        """.trimIndent()
        val o = JsonCodec.decode<JsonObject>(json)

        assertEquals(1, o.objectList("classification_counts", "classificationCounts").size)
        assertEquals(1, o.objectList("by_department", "byDepartment").size)
        assertEquals(1, o.objectList("by_shift", "byShift").size)
        assertEquals(1, o.objectList("trend").size)
    }

    @Test
    fun `objectList accepts snakeCase dashboard keys`() {
        val o = JsonCodec.decode<JsonObject>("""{"by_department":[{"name":"K3L","count":2}]}""")
        assertEquals("K3L", o.objectList("by_department", "byDepartment").first()["name"].toString().trim('"'))
    }
}
