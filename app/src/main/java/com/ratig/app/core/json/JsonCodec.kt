package com.ratig.app.core.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Single shared JSON configuration for DTOs and configuration payloads. */
object JsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    inline fun <reified T> decode(raw: String): T = json.decodeFromString(raw)

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    fun <T> decode(raw: String, serializer: KSerializer<T>): T = json.decodeFromString(serializer, raw)

    fun <T> encode(value: T, serializer: KSerializer<T>): String = json.encodeToString(serializer, value)
}
