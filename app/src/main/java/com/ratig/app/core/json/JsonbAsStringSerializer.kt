package com.ratig.app.core.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Bridges a Postgres `jsonb` column and a Kotlin [String] holding JSON text.
 *
 * The database stores these columns as structured JSON objects, but the app
 * treats them as opaque JSON strings (a frozen copy of a mode configuration).
 *
 *  - **decode**: a JSON object/array is re-encoded to its compact string form;
 *    a JSON string is passed through unchanged (older rows).
 *  - **encode**: the string is parsed back into a JSON element so the server
 *    receives a real `jsonb` object, not a quoted string.
 *
 * An unparseable string is sent as a JSON string literal rather than crashing.
 */
object JsonbAsStringSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.ratig.app.JsonbAsString")

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: run { encoder.encodeString(value); return }
        val element: JsonElement = runCatching { Json.parseToJsonElement(value) }
            .getOrElse { JsonPrimitive(value) }
        jsonEncoder.encodeJsonElement(element)
    }
}

/** Keeps the JSON text of a nullable jsonb column; see [JsonbAsStringSerializer]. */
object NullableJsonbAsStringSerializer : KSerializer<String?> {

    override val descriptor: SerialDescriptor = JsonbAsStringSerializer.descriptor

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.contentOrNull
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: run {
                if (value == null) encoder.encodeNull() else encoder.encodeString(value)
                return
            }
        val element: JsonElement = if (value == null) {
            JsonNull
        } else {
            runCatching { Json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }
        }
        jsonEncoder.encodeJsonElement(element)
    }
}
