package com.ratig.app.core.identity

import javax.inject.Inject

/** Outcome of NIK validation, carrying user-facing Indonesian copy on failure. */
sealed interface NikValidation {
    data object Valid : NikValidation
    data class Invalid(val message: String) : NikValidation
}

/**
 * Sanitizes + validates NIK input.
 *
 * Privacy & correctness rules (CONTRACT-2 §Identification):
 *  - NIK is processed ONLY as [String]; it is never parsed to a numeric type,
 *    so leading zeros are always preserved.
 *  - Digits only; length bounds come from OfflinePolicyStore
 *    (nikMinLength / nikMaxLength, default 16/16). Callers pass the current
 *    policy values so a policy change never requires a code change.
 */
class NikValidator @Inject constructor() {

    companion object {
        const val DEFAULT_MIN_LENGTH = 16
        const val DEFAULT_MAX_LENGTH = 16
    }

    /**
     * Keeps digits only, in original order (leading zeros survive), capped at
     * [maxLength] characters.
     */
    fun sanitize(raw: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (raw.isEmpty()) return raw
        val digits = raw.filter { it.isDigit() }
        return if (digits.length <= maxLength) digits else digits.take(maxLength)
    }

    fun validate(
        nik: String,
        minLength: Int = DEFAULT_MIN_LENGTH,
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ): NikValidation = when {
        nik.isEmpty() -> NikValidation.Invalid("NIK belum diisi.")
        nik.any { !it.isDigit() } -> NikValidation.Invalid("NIK hanya boleh berisi angka.")
        nik.length < minLength -> NikValidation.Invalid("NIK minimal $minLength digit.")
        nik.length > maxLength -> NikValidation.Invalid("NIK maksimal $maxLength digit.")
        else -> NikValidation.Valid
    }

    fun isValid(
        nik: String,
        minLength: Int = DEFAULT_MIN_LENGTH,
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ): Boolean = validate(nik, minLength, maxLength) is NikValidation.Valid
}
