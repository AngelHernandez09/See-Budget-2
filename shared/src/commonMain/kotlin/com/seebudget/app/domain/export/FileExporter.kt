package com.seebudget.app.domain.export

import com.seebudget.app.generated.resources.Res
import com.seebudget.app.generated.resources.export_error_platform_not_available
import org.jetbrains.compose.resources.getString

/**
 * Puente hacia la API de archivos/almacenamiento de cada plataforma —
 * mismo criterio que `ReminderScheduler` (Fase 8, RF-05): la interfaz
 * vive en `commonMain`, la implementación real se registra por
 * plataforma en `platformModule()`. RF-06 ("Solo Android" para la
 * primera pasada real, decidido en el chat, mismo alcance que RF-05):
 * Android registra `FileExporterAndroid` (ver androidMain); iOS/
 * Desktop/Web registran [NoOpFileExporter] — la app compila y corre en
 * las 5 plataformas, pero solo Android exporta de verdad.
 *
 * `ExportViewModel` es el único llamador — arma el contenido (CSV vía
 * `ExpenseCsvExporter`, o las filas de `ExpenseExportRow` para el PDF —
 * ninguna de las dos depende de esta interfaz) y decide QUÉ exportar;
 * esta interfaz solo sabe CÓMO entregarlo en la plataforma actual.
 */
interface FileExporter {
    /**
     * Escribe [content] (ya armado, ej. por `ExpenseCsvExporter`) como un
     * archivo llamado [fileName]. Decidido con el usuario en el chat:
     * en Android intenta guardar directo en Descargas (Android 10+, vía
     * MediaStore) y siempre deja disponible compartirlo (`share`) —
     * ver KDoc de `FileExporterAndroid` para el detalle de versiones
     * más viejas.
     */
    suspend fun exportCsv(fileName: String, content: String): ExportResult

    /**
     * Igual que [exportCsv] pero arma un PDF a partir de [rows] (mismo
     * origen de datos que el CSV, ver `ExpenseExportRow`) con [title]/
     * [subtitle] como encabezado de la primera página. La construcción
     * del PDF en sí (paginado, columnas, etc. — `PdfDocument`, API de
     * Android) vive en `FileExporterAndroid`/`ExpensePdfBuilder`, no acá.
     *
     * [header] (RNF-07, i18n, agregado en el chat): fila de encabezado ya
     * armada por `ExpenseExportRow.header(labels)` en `ExportViewModel`
     * (mismo criterio que [exportCsv]/`ExpenseCsvExporter.buildCsv` — ver
     * KDoc ahí) — se repite en cada página de la tabla.
     */
    suspend fun exportPdf(
        fileName: String,
        title: String,
        subtitle: String,
        header: ExpenseExportRow,
        rows: List<ExpenseExportRow>,
    ): ExportResult

    /**
     * Ofrece "Compartir" el archivo de un [ExportResult.Success] previo
     * (share sheet nativo). [handle] es el mismo objeto opaco que vino en
     * ese resultado — no se interpreta acá, cada plataforma sabe qué
     * guardó adentro. No-op en plataformas sin implementación real
     * todavía (ver [NoOpFileExporter]).
     */
    fun share(handle: Any, chooserTitle: String)
}

/**
 * Resultado de un intento de exportación. [ExportResult.Success.handle]
 * es un objeto opaco (en Android, un `android.net.Uri`) que `ExportViewModel`
 * simplemente reenvía a `FileExporter.share` si el usuario toca
 * "Compartir" — `commonMain` no necesita saber su tipo real.
 */
sealed interface ExportResult {
    data class Success(val message: String, val handle: Any) : ExportResult
    data class Error(val message: String) : ExportResult
}

/** RF-06 — sin implementación real en esta plataforma todavía (ver KDoc de la interfaz). */
class NoOpFileExporter : FileExporter {
    override suspend fun exportCsv(fileName: String, content: String): ExportResult =
        ExportResult.Error(getString(Res.string.export_error_platform_not_available))

    override suspend fun exportPdf(
        fileName: String,
        title: String,
        subtitle: String,
        header: ExpenseExportRow,
        rows: List<ExpenseExportRow>,
    ): ExportResult =
        ExportResult.Error(getString(Res.string.export_error_platform_not_available))

    override fun share(handle: Any, chooserTitle: String) = Unit
}
