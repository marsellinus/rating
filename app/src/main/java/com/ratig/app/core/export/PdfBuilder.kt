package com.ratig.app.core.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import java.io.ByteArrayOutputStream

/**
 * Dependency-free A4 portrait PDF writer on top of the platform
 * `android.graphics.pdf.PdfDocument`. All measurements are PostScript points
 * (1/72 inch); A4 portrait is 595 x 842 pt.
 *
 * Content is a linear flow: [addTitle], [addMetaLine], [addSpacer], [addTable].
 * Rows never split across pages; a new page starts when the cursor would pass
 * the bottom limit (keeping room for the footer). Pages stay open until
 * [build] so footers can contain the total page count.
 */
class PdfBuilder(
    private val pageWidthPt: Int = A4_WIDTH_PT,
    private val pageHeightPt: Int = A4_HEIGHT_PT,
    private val marginPt: Float = 36f,
) {

    private class OpenPage(val page: PdfDocument.Page, val canvas: Canvas)

    private val document = PdfDocument()
    private val pages = mutableListOf<OpenPage>()
    private var current: OpenPage? = null
    private var cursorY = 0f

    private val contentWidth: Float get() = pageWidthPt - 2 * marginPt
    private val contentBottom: Float get() = pageHeightPt - marginPt - FOOTER_RESERVE_PT

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.BLACK
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = Color.DKGRAY
    }
    private val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.BLACK
    }
    private val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = Color.BLACK
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f
        color = Color.GRAY
    }
    private val headerBackgroundPaint = Paint().apply { color = Color.rgb(232, 232, 232) }
    private val zebraBackgroundPaint = Paint().apply { color = Color.rgb(247, 247, 247) }
    private val linePaint = Paint().apply {
        strokeWidth = 0.6f
        color = Color.rgb(200, 200, 200)
    }

    /** Adds the document title (bold, 16 pt). */
    fun addTitle(text: String) {
        ensureSpace(26f)
        drawClipped(text, marginPt, cursorY + 16f, contentWidth, titlePaint, current?.canvas)
        cursorY += 26f
    }

    /** Adds a gray meta line (period, filters, generator info, ...). */
    fun addMetaLine(text: String) {
        ensureSpace(15f)
        drawClipped(text, marginPt, cursorY + 11f, contentWidth, metaPaint, current?.canvas)
        cursorY += 15f
    }

    fun addSpacer(heightPt: Float = 8f) {
        cursorY += heightPt
    }

    /**
     * Draws a non-breaking table. [columnWidthsPt] are absolute x-extents
     * starting at the left margin; their sum must fit the content width.
     */
    fun addTable(headers: List<String>, rows: List<List<String>>, columnWidthsPt: List<Float>) {
        require(headers.size == columnWidthsPt.size) {
            "Jumlah header dan lebar kolom harus sama"
        }
        require(rows.all { it.size == columnWidthsPt.size }) {
            "Jumlah sel dan lebar kolom harus sama"
        }
        require(columnWidthsPt.sum() <= contentWidth + 0.5f) {
            "Total lebar kolom melebihi lebar halaman"
        }
        val xOffsets = FloatArray(columnWidthsPt.size)
        var acc = 0f
        for (i in columnWidthsPt.indices) {
            xOffsets[i] = acc
            acc += columnWidthsPt[i]
        }
        drawRow(headers, xOffsets, columnWidthsPt.toFloatArray(), tableHeaderPaint, header = true, zebra = false)
        rows.forEachIndexed { index, row ->
            drawRow(row, xOffsets, columnWidthsPt.toFloatArray(), cellPaint, header = false, zebra = index % 2 == 1)
        }
        // Separator line under the table.
        ensureSpace(4f)
        current?.canvas?.drawLine(marginPt, cursorY, marginPt + contentWidth, cursorY, linePaint)
        cursorY += 4f
    }

    /**
     * Finishes every page (drawing footers with the total count first),
     * writes the document bytes and releases native resources. The builder
     * must not be reused afterwards.
     */
    fun build(): ByteArray {
        if (pages.isEmpty()) newPage()
        val total = pages.size
        pages.forEachIndexed { index, open ->
            val label = "Halaman ${index + 1} dari $total"
            val labelWidth = footerPaint.measureText(label)
            open.canvas.drawText(label, (pageWidthPt - labelWidth) / 2f, pageHeightPt - marginPt / 2f, footerPaint)
            document.finishPage(open.page)
        }
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun drawRow(
        values: List<String>,
        xOffsets: FloatArray,
        widths: FloatArray,
        paint: TextPaint,
        header: Boolean,
        zebra: Boolean,
    ) {
        val rowHeight = paint.textSize + 6f
        ensureSpace(rowHeight + 2f)
        val canvas = current!!.canvas
        val top = cursorY
        when {
            header -> canvas.drawRect(marginPt, top, marginPt + contentWidth, top + rowHeight, headerBackgroundPaint)
            zebra -> canvas.drawRect(marginPt, top, marginPt + contentWidth, top + rowHeight, zebraBackgroundPaint)
        }
        for (i in values.indices) {
            drawClipped(values[i], marginPt + xOffsets[i] + 4f, top + paint.textSize + 3f, widths[i] - 8f, paint, canvas)
        }
        cursorY = top + rowHeight
        if (!header) {
            canvas.drawLine(marginPt, cursorY, marginPt + contentWidth, cursorY, linePaint)
            cursorY += 1f
        }
    }

    private fun ensureSpace(heightPt: Float) {
        if (current == null || cursorY + heightPt > contentBottom) newPage()
    }

    private fun newPage() {
        val info = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, pages.size + 1).create()
        val page = document.startPage(info)
        val open = OpenPage(page, page.canvas)
        pages.add(open)
        current = open
        cursorY = marginPt
    }

    /** Single-line draw with ellipsis when the text is wider than [maxWidthPt]. */
    private fun drawClipped(
        text: String,
        x: Float,
        baselineY: Float,
        maxWidthPt: Float,
        paint: TextPaint,
        canvas: Canvas?,
    ) {
        if (text.isEmpty() || canvas == null) return
        val fitted = TextUtils.ellipsize(text, paint, maxWidthPt, TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted, x, baselineY, paint)
    }

    companion object {
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842
        const val FOOTER_RESERVE_PT = 20f
    }
}
