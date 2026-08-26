package com.seebudget.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.seebudget.app.domain.export.ExpenseExportRow
import com.seebudget.app.domain.export.ExportResult
import com.seebudget.app.domain.export.FileExporter
import com.seebudget.app.generated.resources.Res
import com.seebudget.app.generated.resources.export_error_create_file_downloads
import com.seebudget.app.generated.resources.export_error_generic
import com.seebudget.app.generated.resources.export_error_write_file
import com.seebudget.app.generated.resources.export_pdf_page_label
import com.seebudget.app.generated.resources.export_result_saved_to_cache_share_only
import com.seebudget.app.generated.resources.export_result_saved_to_downloads
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * RF-06 — única plataforma con implementación real por ahora (ver KDoc
 * de `FileExporter`). Decidido con el usuario en el chat: además de
 * guardar el archivo, siempre se ofrece "Compartir" (share sheet). El
 * PDF en sí lo arma `ExpensePdfBuilder` — esta clase solo decide DÓNDE
 * y CÓMO se guardan los bytes, sin importar si son de un CSV o un PDF
 * (ver `exportBytes`, compartido por ambos).
 *
 * **Android 10+ (API 29, `Build.VERSION_CODES.Q`):** se guarda directo
 * en la carpeta pública Descargas vía `MediaStore.Downloads` — no
 * requiere pedir ningún permiso de almacenamiento (scoped storage). El
 * `Uri` que devuelve MediaStore ya es compartible tal cual (es un
 * content provider), así que `share()` lo reusa sin pasar por
 * `FileProvider`.
 *
 * **Android 8-9 (API 26-28, el `minSdk` de este proyecto):** todavía no
 * existe `MediaStore.Downloads`, y guardar en la carpeta pública de
 * Descargas de la forma vieja requeriría pedir el permiso en tiempo de
 * ejecución `WRITE_EXTERNAL_STORAGE` — eso exige un `Activity` (esta
 * clase solo tiene `Context`, ver `platformModule()`/Koin) y hoy en día
 * son versiones de Android muy minoritarias, así que se optó por no
 * armar ese flujo de permiso todavía: en su lugar se escribe en el
 * caché privado de la app y se ofrece directo por "Compartir" (vía
 * `FileProvider` — desde el share sheet el usuario puede elegir
 * "Guardar en Archivos/Descargas" igual). El mensaje de éxito deja claro
 * que en este caso el guardado directo no ocurrió.
 */
class FileExporterAndroid(private val context: Context) : FileExporter {

    override suspend fun exportCsv(fileName: String, content: String): ExportResult =
        exportBytes(fileName, "text/csv") { output -> output.write(content.toByteArray(Charsets.UTF_8)) }

    override suspend fun exportPdf(
        fileName: String,
        title: String,
        subtitle: String,
        header: ExpenseExportRow,
        rows: List<ExpenseExportRow>,
    ): ExportResult {
        // RNF-07 (i18n, agregado en el chat): los labels de "Página X de
        // Y" se resuelven acá (getString es suspend) ANTES de entrar al
        // `writer` de exportBytes (no-suspend, corre dentro del
        // MediaStore/FileOutputStream) — ver KDoc de ExpensePdfBuilder.build.
        val totalPages = ExpensePdfBuilder.totalPages(rows.size)
        val pageLabels = (1..totalPages).map { page ->
            getString(Res.string.export_pdf_page_label, page, totalPages)
        }
        return exportBytes(fileName, "application/pdf") { output ->
            val document = ExpensePdfBuilder.build(title, subtitle, header, rows, pageLabels)
            try {
                document.writeTo(output)
            } finally {
                document.close()
            }
        }
    }

    override fun share(handle: Any, chooserTitle: String) {
        val uri = handle as? Uri ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // RNF-07 (i18n, agregado en el chat): chooserTitle ya viene resuelto
        // desde ExportViewModel.onShare() (getString es suspend, esta función
        // no lo es) — ver KDoc de FileExporter.share.
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private suspend fun exportBytes(fileName: String, mimeType: String, writer: (OutputStream) -> Unit): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = writeToDownloadsViaMediaStore(fileName, mimeType, writer)
                    ExportResult.Success(
                        message = getString(Res.string.export_result_saved_to_downloads, fileName),
                        handle = uri,
                    )
                } else {
                    val uri = writeToCacheForSharing(fileName, writer)
                    ExportResult.Success(
                        message = getString(Res.string.export_result_saved_to_cache_share_only),
                        handle = uri,
                    )
                }
            } catch (e: Exception) {
                ExportResult.Error(e.message ?: getString(Res.string.export_error_generic))
            }
        }

    private suspend fun writeToDownloadsViaMediaStore(fileName: String, mimeType: String, writer: (OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error(getString(Res.string.export_error_create_file_downloads))
        resolver.openOutputStream(uri)?.use { stream -> writer(stream) }
            ?: error(getString(Res.string.export_error_write_file))
        return uri
    }

    private fun writeToCacheForSharing(fileName: String, writer: (OutputStream) -> Unit): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { stream -> writer(stream) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
