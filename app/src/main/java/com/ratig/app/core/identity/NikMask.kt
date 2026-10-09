package com.ratig.app.core.identity

/**
 * Masks a NIK for every surface that does not strictly require the full value
 * (the confirm card also shows the masked form; the full NIK is never
 * re-displayed after input and never appears in logs or URLs).
 *
 * Rule: keep the first 4 + last 4 digits, e.g. a 16-digit NIK renders as
 * `3201**********99`. Shorter values are masked progressively harder:
 *  - 8..11 digits: first 2 + last 2 kept.
 *  - fewer than 8 digits: fully masked (too short to reveal anything safely).
 */
object NikMask {

    fun mask(nik: String): String {
        val length = nik.length
        return when {
            length >= 12 -> nik.take(4) + "*".repeat(length - 8) + nik.takeLast(4)
            length >= 8 -> nik.take(2) + "*".repeat(length - 4) + nik.takeLast(2)
            else -> "*".repeat(length)
        }
    }
}
