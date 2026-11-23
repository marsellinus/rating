package com.ratig.app.core.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free .xlsx writer (SpreadsheetML 2007+).
 *
 * Why not a library: adding Apache POI to Android is heavy and unnecessary for
 * a flat report table. This writes the small set of parts Excel needs, uses
 * inline strings (no sharedStrings table) and emits numeric cells for numbers
 * so Excel can sort and chart them.
 *
 * Escaping is strict: any user text (worker names, notes) must be XML-escaped
 * before it reaches a cell, otherwise the resulting file is corrupt.
 */
class XlsxBuilder {
    private data class Cell(val text: String, val numeric: Boolean)
    private var header: List<String> = emptyList()
    private val dataRows = mutableListOf<List<Cell>>()
    private var widths: List<Int> = emptyList()

    fun header(columns: List<String>, columnWidths: List<Int> = emptyList()): XlsxBuilder = apply {
        header = columns
        widths = columnWidths.ifEmpty { columns.map { DEFAULT_WIDTH } }
    }

    fun row(values: List<String>, numericColumns: Set<Int> = emptySet()): XlsxBuilder = apply {
        require(values.size == header.size) {
            "Row has ${values.size} values but header has ${header.size}"
        }
        dataRows += values.mapIndexed { index, value ->
            Cell(value, index in numericColumns && value.isNotBlank() && value.toDoubleOrNull() != null)
        }
    }

    fun build(): ByteArray {
        require(header.isNotEmpty()) { "XLSX header must not be empty" }
        val sheet = buildSheet()
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.put(" [Content_Types].xml".trim(), CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", WORKBOOK)
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", STYLES)
            zip.put("xl/worksheets/sheet1.xml", sheet)
        }
        return baos.toByteArray()
    }

    private fun buildSheet(): String {
        val sb = StringBuilder(SHEET_HEADER)
        // Column widths first.
        sb.append("<cols>")
        widths.forEachIndexed { i, width ->
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")

        // Row 1 = styled header.
        sb.append("""<row r="1" ht="22" customHeight="1">""")
        header.forEachIndexed { i, text ->
            sb.append(cellXml(i, 1, Cell(text, false), header = true))
        }
        sb.append("</row>")

        dataRows.forEachIndexed { rowIndex, cells ->
            val r = rowIndex + 2
            sb.append("""<row r="$r">""")
            cells.forEachIndexed { colIndex, cell ->
                sb.append(cellXml(colIndex, r, cell, header = false))
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>").append(SHEET_FOOTER)
        return sb.toString()
    }

    private fun cellXml(col: Int, row: Int, cell: Cell, header: Boolean): String {
        val ref = "${columnName(col)}$row"
        val style = if (header) 1 else if (cell.numeric) 2 else 0
        val styleAttr = if (style == 0) "" else """ s="$style""""
        return if (cell.numeric) {
            """<c r="$ref"$styleAttr><v>${cell.text}</v></c>"""
        } else {
            """<c r="$ref"$styleAttr t="inlineStr"><is><t xml:space="preserve">${escape(cell.text)}</t></is></c>"""
        }
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        }
        return sb.toString()
    }

    private fun escape(value: String): String = buildString(value.length + 16) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code < 0x20 && ch !in listOf('\t', '\n', '\r')) {
                    append("&#${ch.code};")
                } else append(ch)
            }
        }
    }

    private fun ZipOutputStream.put(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    companion object {
        private const val DEFAULT_WIDTH = 18
        private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
            """</Types>"""
        private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""
        private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="Laporan RATIG" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            """</Relationships>"""
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="3"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF1B5E20"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="3">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
            """<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """</cellXfs></styleSheet>"""
        private const val SHEET_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">"""
        private const val SHEET_FOOTER = "</worksheet>"
    }
}
