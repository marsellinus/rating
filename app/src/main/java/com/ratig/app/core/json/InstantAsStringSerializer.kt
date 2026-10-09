package com.ratig.app.core.json

import java.time.Instant
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

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
