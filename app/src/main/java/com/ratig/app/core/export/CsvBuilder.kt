package com.ratig.app.core.export

/**
 * Minimal RFC 4180-style CSV writer. Pure Kotlin (no Android dependencies) so
 * it stays unit-testable under `unitTests.isReturnDefaultValues`.
 *
 * Fields containing the delimiter, double quotes, CR or LF are quoted with
 * doubled inner quotes. Output is CRLF-delimited and prefixed with a UTF-8
 * BOM so Microsoft Excel detects the encoding on double-click.
 */
class CsvBuilder(private val delimiter: Char = ',') {

    private val lines = mutableListOf<String>()

    fun header(columns: List<String>): CsvBuilder {
        lines.add(encodeRow(columns))
        return this
    }

    fun header(vararg columns: String): CsvBuilder = header(columns.toList())

    fun row(values: List<Any?>): CsvBuilder {
        lines.add(encodeRow(values.map { it?.toString().orEmpty() }))
        return this
    }

    fun row(vararg values: Any?): CsvBuilder = row(values.toList())

    /** Builds the final CSV text including the BOM prefix and trailing CRLF. */
    fun build(): String = BOM + lines.joinToString(CRLF) + CRLF

    private fun encodeRow(values: List<String>): String =
        values.joinToString(delimiter.toString()) { escape(it, delimiter) }

    companion object {
        const val BOM = "\uFEFF"
        const val CRLF = "\r\n"

        /** Escapes a single CSV field per RFC 4180. */
        fun escape(value: String?, delimiter: Char = ','): String {
            if (value.isNullOrEmpty()) return ""
            val needsQuoting = value.contains(delimiter) ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')
            if (!needsQuoting) return value
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
    }
}
