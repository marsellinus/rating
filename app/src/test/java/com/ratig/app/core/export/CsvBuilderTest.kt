package com.ratig.app.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the RFC 4180 CSV writer used by the export/report feature. */
class CsvBuilderTest {

    @Test
    fun `build prefixes bom and terminates rows with crlf`() {
        val csv = CsvBuilder().header("a", "b").row("1", "2").build()
        assertEquals("\uFEFFa,b\r\n1,2\r\n", csv)
    }

    @Test
    fun `fields containing the delimiter are quoted`() {
        val csv = CsvBuilder().row("a,b", "c").build()
        assertTrue(csv.contains("\"a,b\""))
    }

    @Test
    fun `double quotes are escaped by doubling and wrapping`() {
        assertEquals("\"He said \"\"hi\"\"\"", CsvBuilder.escape("He said \"hi\""))
    }

    @Test
    fun `newlines force quoting`() {
        assertEquals("\"line1\nline2\"", CsvBuilder.escape("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", CsvBuilder.escape("line1\r\nline2"))
    }

    @Test
    fun `plain and null values are not quoted`() {
        assertEquals("plain", CsvBuilder.escape("plain"))
        assertEquals("", CsvBuilder.escape(null))
        assertEquals("", CsvBuilder.escape(""))
    }

    @Test
    fun `null cells render as empty fields`() {
        val csv = CsvBuilder().row("a", null, 3).build()
        assertEquals("\uFEFFa,,3\r\n", csv)
    }

    @Test
    fun `custom delimiter is respected`() {
        val csv = CsvBuilder(delimiter = ';').row("a;b", "c").build()
        assertEquals("\uFEFF\"a;b\";c\r\n", csv)
    }
}
