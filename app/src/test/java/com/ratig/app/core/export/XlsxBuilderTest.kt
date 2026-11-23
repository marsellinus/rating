package com.ratig.app.core.export

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxBuilderTest {

    @Test
    fun `build produces a valid xlsx package with all required parts`() {
        val bytes = XlsxBuilder()
            .header(listOf("Nama", "Mean (ms)"), listOf(24, 12))
            .row(listOf("Budi & <Santoso>", "345.5"), numericColumns = setOf(1))
            .build()

        val names = mutableSetOf<String>()
        var sheet = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                val content = zip.readBytes().toString(Charsets.UTF_8)
                if (entry.name == "xl/worksheets/sheet1.xml") sheet = content
                entry = zip.nextEntry
            }
        }

        assertTrue("[Content_Types].xml" in names)
        assertTrue("xl/workbook.xml" in names)
        assertTrue("xl/worksheets/sheet1.xml" in names)
        assertTrue("xl/styles.xml" in names)
        assertTrue("special characters are XML-escaped", "Budi &amp; &lt;Santoso&gt;" in sheet)
        assertTrue("numeric cell uses a value element", "<v>345.5</v>" in sheet)
    }
}
