package com.seebudget.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.seebudget.app.domain.export.ExpenseExportRow

/**
 * RF-06 (PDF) — arma un `PdfDocument` con el historial de gastos:
 * título + subtítulo (rango de fechas) en la primera página, y una
 * tabla de 7 columnas (mismas que el CSV, ver `ExpenseExportRow`) que
 * se pagina sola cuando no entran todas las filas — repite el
 * encabezado de columnas en cada página y numera "Página X de Y".
 *
 * Tamaño de página A4 en puntos (595x842, 72pt/pulgada) — no hay un
 * tamaño de papel definido en el documento de requerimientos, A4 es el
 * estándar fuera de EE.UU./Canadá. Los anchos de columna (`COLUMN_WIDTHS`)
 * sumados dan el ancho disponible entre márgenes (515pt); "Monto" se
 * alinea a la derecha (convención universal para columnas numéricas en
 * tablas), el resto a la izquierda. Celdas que no entran en su columna
 * se truncan con "…" (ej. notas largas) — nunca se recorta sin avisar.
 */
internal object ExpensePdfBuilder {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 18f
    private const val HEADER_ROW_HEIGHT = 20f
    private const val FOOTER_RESERVED_HEIGHT = 30f
    private const val CELL_PADDING = 4f

    // Fecha, Tipo, Categoría, Monto, Moneda, Nota, Método de pago — mismo orden que ExpenseExportRow.fields().
    private val COLUMN_WIDTHS = floatArrayOf(72f, 52f, 82f, 62f, 42f, 124f, 81f)
    private const val AMOUNT_COLUMN_INDEX = 3

    /**
     * RNF-07 (i18n, agregado en el chat): [header] ya viene resuelto por
     * `ExportViewModel` (`ExpenseExportRow.header(labels)`) — ver KDoc de
     * `FileExporter.exportPdf`. [pageLabels] es el "Página X de Y" de
     * cada página, ya traducido — `FileExporterAndroid.exportPdf` los
     * resuelve con `getString()` (suspend) ANTES de llamar acá, porque
     * `build()` corre dentro de un `writer` no-suspend (ver
     * `FileExporterAndroid.exportBytes`); tiene que haber exactamente uno
     * por página, en orden — ver [totalPages].
     */
    fun build(
        title: String,
        subtitle: String,
        header: ExpenseExportRow,
        rows: List<ExpenseExportRow>,
        pageLabels: List<String>,
    ): PdfDocument {
        val document = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.DKGRAY }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; isFakeBoldText = true; color = Color.BLACK }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.BLACK }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 0.75f }

        val firstPageTableTop = MARGIN + 50f
        val otherPageTableTop = MARGIN
        val firstPageCapacity = rowCapacity(firstPageTableTop)
        val otherPageCapacity = rowCapacity(otherPageTableTop)

        val pages = chunkRows(rows, firstPageCapacity, otherPageCapacity)
        val totalPages = pages.size

        pages.forEachIndexed { index, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = MARGIN
            if (index == 0) {
                canvas.drawText(title, MARGIN, y + 18f, titlePaint)
                canvas.drawText(subtitle, MARGIN, y + 36f, subtitlePaint)
                y = firstPageTableTop
            }

            y = drawRow(canvas, y, header, headerPaint, isHeaderRow = true)
            canvas.drawLine(MARGIN, y - 4f, PAGE_WIDTH - MARGIN, y - 4f, linePaint)
            for (row in pageRows) {
                y = drawRow(canvas, y, row, cellPaint)
            }

            canvas.drawText(pageLabels[index], PAGE_WIDTH / 2f, PAGE_HEIGHT - 16f, footerPaint)
            document.finishPage(page)
        }

        return document
    }

    /** Cantidad de páginas que va a ocupar un export de [rowCount] filas — permite a `FileExporterAndroid.exportPdf` resolver los [pageLabels] de `build()` (uno por página) antes de invocar `build()`, ver su KDoc. Misma lógica de paginado que [chunkRows], sin construir las páginas. */
    fun totalPages(rowCount: Int): Int {
        val firstCapacity = rowCapacity(MARGIN + 50f)
        val restCapacity = rowCapacity(MARGIN)
        if (rowCount <= firstCapacity) return 1
        val remaining = rowCount - firstCapacity
        return 1 + (remaining + restCapacity - 1) / restCapacity
    }

    private fun rowCapacity(tableTop: Float): Int {
        val available = PAGE_HEIGHT - FOOTER_RESERVED_HEIGHT - tableTop - HEADER_ROW_HEIGHT
        return (available / ROW_HEIGHT).toInt().coerceAtLeast(1)
    }

    /** Al menos 1 página, aunque no haya filas (queda solo título + encabezado de tabla vacía). */
    private fun chunkRows(rows: List<ExpenseExportRow>, firstCapacity: Int, restCapacity: Int): List<List<ExpenseExportRow>> {
        if (rows.isEmpty()) return listOf(emptyList())
        val pages = mutableListOf<List<ExpenseExportRow>>()
        var index = 0
        var capacity = firstCapacity
        while (index < rows.size) {
            val end = (index + capacity).coerceAtMost(rows.size)
            pages.add(rows.subList(index, end))
            index = end
            capacity = restCapacity
        }
        return pages
    }

    private fun drawRow(canvas: Canvas, startY: Float, row: ExpenseExportRow, basePaint: Paint, isHeaderRow: Boolean = false): Float {
        val height = if (isHeaderRow) HEADER_ROW_HEIGHT else ROW_HEIGHT
        val baselineY = startY + height - 6f
        var x = MARGIN
        row.fields().forEachIndexed { i, field ->
            val width = COLUMN_WIDTHS[i]
            if (i == AMOUNT_COLUMN_INDEX) {
                val rightAligned = Paint(basePaint).apply { textAlign = Paint.Align.RIGHT }
                val drawX = x + width - CELL_PADDING
                canvas.drawText(truncate(field, rightAligned, width - CELL_PADDING), drawX, baselineY, rightAligned)
            } else {
                canvas.drawText(truncate(field, basePaint, width - CELL_PADDING), x, baselineY, basePaint)
            }
            x += width
        }
        return startY + height
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        val fitChars = paint.breakText(text, true, (maxWidth - ellipsisWidth).coerceAtLeast(0f), null)
        return text.take(fitChars) + ellipsis
    }
}
