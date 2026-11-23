package com.ratig.app.domain.usecase

import com.ratig.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the working shift from the time a test was actually taken.
 *
 * The previous behaviour took the shift from an examiner dropdown (or the
 * worker profile), so a test run at 18:23 WIB could be recorded as "Pagi"
 * simply because the profile shift was empty. A shift is a property of TIME,
 * not of a dropdown answer.
 *
 * Rules:
 *  - Convert the instant to the shift's own timezone (default Asia/Jakarta).
 *  - A normal range [start, end) with start < end contains the time directly.
 *  - An overnight range (start > end) wraps past midnight, e.g. 22:00–06:00
 *    contains 23:30 and 02:15.
 *  - Exact end boundary belongs to the NEXT shift (half-open interval).
 *  - Ties are impossible in valid data; if configured ranges overlap, the first
 *    active match wins (deterministic).
 *
 * Returns null when no active shift matches; callers then fall back to the
 * worker's profile shift so historical data stays readable.
 */
@Singleton
class ShiftResolver @Inject constructor() {

    fun resolve(at: Instant, shifts: List<Shift>): Shift? =
        shifts.firstOrNull { shift -> shift.activeStatus && contains(shift, at) }

    fun contains(shift: Shift, at: Instant): Boolean {
        val zone = runCatching { ZoneId.of(shift.timezone) }.getOrElse { FALLBACK_ZONE }
        val local = ZonedDateTime.ofInstant(at, zone).toLocalTime()
        val start = shift.startTime
        val end = shift.endTime
        return if (start == end) {
            // A 24-hour shift covers the whole day.
            true
        } else if (start < end) {
            local >= start && local < end
        } else {
            // Overnight: >= start OR < end.
            local >= start || local < end
        }
    }

    companion object {
        const val DEFAULT_TIMEZONE = "Asia/Jakarta"
        private val FALLBACK_ZONE: ZoneId = ZoneId.of(DEFAULT_TIMEZONE)
    }
}

/** Convenience for tests and for default scheduling UI labels. */
fun defaultShiftBounds(): Pair<LocalTime, LocalTime> =
    LocalTime.of(6, 0) to LocalTime.of(14, 0)
