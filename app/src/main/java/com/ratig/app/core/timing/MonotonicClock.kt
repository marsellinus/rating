package com.ratig.app.core.timing

/**
 * Abstraction over the Android monotonic clock.
 *
 * Reaction-time measurement MUST use a monotonic clock (SystemClock
 * elapsedRealtimeNanos): it keeps ticking uniformly, is unaffected by wall
 * clock/NTP changes, and is the only valid baseline for sub-100ms intervals.
 * Values are device-local and are NOT comparable across devices or sessions;
 * they are stored per-trial for on-device audit only.
 */
interface MonotonicClock {
    /** Nanoseconds since boot, including deep sleep. */
    fun nowNanos(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = android.os.SystemClock.elapsedRealtimeNanos()
}
