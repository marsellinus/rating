package com.ratig.app.core.json

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [Instant] as an ISO-8601 string (e.g. "2026-10-09T07:15:00Z"),
 * matching how Supabase returns `timestamptz` values. Nullable properties
 * simply declare `= null` defaults - kotlinx serialization handles the null
 * wrapper around this serializer, so no extra code is needed here.
 *
 * Usage: `@Serializable(with = InstantAsStringSerializer::class)`.
 */
object InstantAsStringSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            // Android's java.time.Instant.parse accepts only the 'Z' form, while
            // Supabase/PostgREST returns '+00:00'. Desktop JDK accepts both, so
            // JVM unit tests never exposed this - only a real device did.
            // OffsetDateTime handles both spellings.
            OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        }
    }
}
