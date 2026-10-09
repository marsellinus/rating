package com.ratig.app.core.identity

import javax.inject.Inject

/** Result of strict QR payload parsing. */
sealed interface QrParseResult {
    /** Payload had the expected prefix and carried a structurally valid NIK. */
    data class Valid(val nik: String) : QrParseResult

    /**
     * Unknown prefix or non-digit remainder — the code is NOT a RATIG payload.
     * A scan must never be treated as authentication or trigger any action
     * beyond showing "QR tidak dikenali".
     */
    data object Unrecognized : QrParseResult

    /** Known prefix but the remainder is not a policy-valid NIK. */
    data class InvalidNik(val message: String) : QrParseResult
}

/**
 * Encodes/decodes the worker-identification QR payload `RATIG1:<NIK>`.
 *
 * The prefix comes from OfflinePolicyStore (`qrPrefix`, default "RATIG1:");
 * callers pass the active policy so a policy change never requires a code
 * change. Parsing is strict: anything that does not start with the exact
 * prefix is rejected outright.
 */
class QrPayload @Inject constructor() {

    companion object {
        const val DEFAULT_PREFIX = "RATIG1:"
    }

    fun encode(nik: String, prefix: String = DEFAULT_PREFIX): String = "$prefix$nik"

    fun parse(
        raw: String,
        prefix: String = DEFAULT_PREFIX,
        minLength: Int = NikValidator.DEFAULT_MIN_LENGTH,
        maxLength: Int = NikValidator.DEFAULT_MAX_LENGTH,
    ): QrParseResult {
        if (!raw.startsWith(prefix)) return QrParseResult.Unrecognized
        val nik = raw.removePrefix(prefix).trim()
        if (nik.isEmpty() || nik.any { !it.isDigit() }) return QrParseResult.Unrecognized
        if (nik.length < minLength || nik.length > maxLength) {
            return QrParseResult.InvalidNik("QR berisi NIK dengan panjang tidak sesuai ketentuan.")
        }
        return QrParseResult.Valid(nik)
    }
}
