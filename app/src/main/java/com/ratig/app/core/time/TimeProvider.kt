package com.ratig.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Central time helpers.
 *
 * Convention (see docs/architecture.md):
 *  - Storage: every timestamp is UTC (timestamptz in PostgreSQL).
 *  - Business/operational day boundaries: explicit timezone, default
 *    Asia/Jakarta for the plant (configurable per shift row).
 *  - Measurement: NEVER wall clock - see [com.ratig.app.core.timing.MonotonicClock].
 */
object TimeProvider {
    /** Operational timezone; shifts reference their own zone, this is the default. */
    val DEFAULT_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

    fun nowUtc(): Instant = Instant.now()

    fun toLocalUtc(instant: Instant): LocalDateTime =
        LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

    fun toOperational(instant: Instant, zone: ZoneId = DEFAULT_ZONE): LocalDateTime =
        LocalDateTime.ofInstant(instant, zone)

    fun operationalDay(instant: Instant, zone: ZoneId = DEFAULT_ZONE): LocalDate =
        LocalDate.ofInstant(instant, zone)

    fun formatForDisplay(instant: Instant?, zone: ZoneId = DEFAULT_ZONE): String {
        if (instant == null) return "-"
        return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")
            .withZone(zone).format(instant)
    }

    fun formatDateForDisplay(instant: Instant?, zone: ZoneId = DEFAULT_ZONE): String {
        if (instant == null) return "-"
        return DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(zone).format(instant)
    }
}
