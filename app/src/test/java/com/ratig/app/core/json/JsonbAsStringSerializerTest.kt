package com.ratig.app.core.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the jsonb <-> String bridge. These pin the exact behaviour
 * that caused the "Unexpected JSON token ... at path: $[0].mode_config" crash
 * when the server returned a structured `jsonb` object for a field the DTO
 * declared as a String.
 */
class JsonbAsStringSerializerTest {

    @Serializable
    private data class Holder(
        @Serializable(with = NullableJsonbAsStringSerializer::class)
        val modeConfig: String? = null,
    )

    private val json = Json { explicitNulls = false }

    @Test
    fun `decodes a jsonb object into its compact json string`() {
        val decoded = json.decodeFromString(
            Holder.serializer(),
            """{"modeConfig":{"targetColor":"green","distractors":2}}""",
        )
        assertEquals("""{"targetColor":"green","distractors":2}""", decoded.modeConfig)
    }

    @Test
    fun `decodes an empty jsonb object`() {
        val decoded = json.decodeFromString(Holder.serializer(), """{"modeConfig":{}}""")
        assertEquals("{}", decoded.modeConfig)
    }

    @Test
    fun `decodes jsonb null to null`() {
        val decoded = json.decodeFromString(Holder.serializer(), """{"modeConfig":null}""")
        assertNull(decoded.modeConfig)
    }

    @Test
    fun `decodes a legacy json string value unchanged`() {
        val decoded = json.decodeFromString(Holder.serializer(), """{"modeConfig":"{\"a\":1}"}""")
        assertEquals("""{"a":1}""", decoded.modeConfig)
    }

    @Test
    fun `encodes a json string back into a real json object`() {
        val encoded = json.encodeToString(
            Holder.serializer(),
            Holder(modeConfig = """{"targetColor":"red"}"""),
        )
        assertEquals("""{"modeConfig":{"targetColor":"red"}}""", encoded)
    }

    @Test
    fun `encodes null as json null`() {
        val encoded = json.encodeToString(Holder.serializer(), Holder(modeConfig = null))
        assertEquals("""{}""", encoded)
    }

    @Test
    fun `unparseable string is sent as a json string rather than crashing`() {
        val encoded = json.encodeToString(Holder.serializer(), Holder(modeConfig = "not json"))
        assertEquals("""{"modeConfig":"not json"}""", encoded)
    }
}
