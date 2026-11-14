package com.ratig.app.core.timing

/**
 * Refinement of a provisional stimulus timestamp to the vsync instant at which
 * the stimulus frame was actually presented.
 *
 * Why this exists: the engine marks the stimulus as visible as soon as it hands
 * the new state to the UI, but the pixels only reach the screen on the next
 * frame. Measuring from that provisional mark therefore *underestimates* the
 * reaction time by up to one frame (16.7 ms at 60 Hz, 8.3 ms at 120 Hz), which
 * is large relative to the 80-500 ms interval being measured.
 *
 * The Compose `withFrameNanos {}` callback reports the frame time in the
 * `System.nanoTime()` base, which differs from [MonotonicClock] (elapsed
 * realtime) by the accumulated deep-sleep time. The frame's *age* is what
 * matters, and age is base-independent, so it is subtracted from the clock
 * sample taken at the same moment.
 *
 * The stimulus frame was composed **before** the provisional clock sample, so
 * a valid refinement is always at or before it. Anything later, in the future,
 * or older than [maxFrameLagMs] comes from a stale/unrelated frame callback and
 * is rejected so it can never distort a measurement.
 *
 * @param provisionalNanos clock sample taken when the stimulus state was set.
 * @param frameTimeNanos frame timestamp from `withFrameNanos {}`.
 * @param clockNowNanos monotonic clock sample taken alongside [nanoTimeNowNanos].
 * @param nanoTimeNowNanos `System.nanoTime()` sample taken alongside [clockNowNanos].
 * @param maxFrameLagMs oldest frame age still considered usable.
 * @return refined presentation instant in the [MonotonicClock] base, or null
 *   when the frame callback cannot be used.
 */
fun refineStimulusTimestamp(
    provisionalNanos: Long,
    frameTimeNanos: Long,
    clockNowNanos: Long,
    nanoTimeNowNanos: Long,
    maxFrameLagMs: Long = DEFAULT_MAX_FRAME_LAG_MS,
): Long? {
    val frameLagNanos = nanoTimeNowNanos - frameTimeNanos
    if (frameLagNanos < 0) return null
    if (frameLagNanos > maxFrameLagMs * 1_000_000L) return null
    val refined = clockNowNanos - frameLagNanos
    if (refined > provisionalNanos) return null
    return refined
}

/** Oldest frame age accepted by [refineStimulusTimestamp]; generous for slow frames. */
const val DEFAULT_MAX_FRAME_LAG_MS: Long = 100L

/**
 * Reaction time in whole milliseconds, rounded to nearest.
 *
 * Truncating would systematically bias every measurement downwards and would
 * push a 79.6 ms response below an 80 ms plausibility threshold. Rounding keeps
 * the reported value as close as possible to the true interval.
 */
fun reactionMillis(tapNanos: Long, shownAtNanos: Long): Long {
    val deltaNanos = tapNanos - shownAtNanos
    return if (deltaNanos >= 0) {
        (deltaNanos + 500_000L) / 1_000_000L
    } else {
        -((-deltaNanos + 500_000L) / 1_000_000L)
    }
}
