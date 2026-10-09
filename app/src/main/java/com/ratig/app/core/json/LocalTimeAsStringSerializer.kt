package com.ratig.app.core.json

import java.time.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [LocalTime] as an ISO-8601 time string (e.g. "07:00" or "15:30"),
 * matching how Supabase returns `time` columns. Nullable properties simply
 * declare `= null` defaults - kotlinx serialization handles the null wrapper
 * around this serializer, so no extra code is needed here.
 *
 * Usage: `@Serializable(with = LocalTimeAsStringSerializer::class)`.
 */
object LocalTimeAsStringSerializer : KSerializer<LocalTime> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalTime =
        LocalTime.parse(decoder.decodeString())
}
