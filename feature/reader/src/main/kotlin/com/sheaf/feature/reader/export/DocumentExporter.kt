package com.sheaf.feature.reader.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.sheaf.core.data.log.AppLog
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/** The formats a document can be exported to (besides PDF). */
enum class ExportFormat(val extension: String, val mime: String, val label: String) {
    /** The document itself, with signatures, markup and form values built in. */
    Pdf("pdf", "application/pdf", "PDF (with signatures and markup)"),
    Jpg("jpg", "image/jpeg", "JPG images"),
    Txt("txt", "text/plain", "Text (TXT)"),
    Word("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (DOCX)"),
    Excel("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (XLSX)"),
}

/**
 * Builds export files in cache/share (served by the FileProvider, purged after an hour) and writes
 * finished files to a place the user picked with the system file picker (Downloads, Drive, SD card…).
 * The picker grants access to just that file or folder, so Sheaf needs no storage permission.
 */
class DocumentExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
    private val renderFactory: PdfRenderSourceFactory,
) {
    /** A fresh folder for one export, so its share URI is never reused (see [newShareFolder]). */
    private fun shareDir(): File = newShareFolder(File(context.cacheDir, "share"))

    /** One JPG per page at about 200 dpi, named "<name> - page N.jpg". Empty list on failure. */
    suspend fun pagesToJpg(uri: String, baseName: String, password: String? = null): List<File> = withContext(io) {
        val dir = shareDir()
        val files = ArrayList<File>()
        runCatching {
            val source = renderFactory.open(uri, password)
            try {
                for (i in 0 until source.pageCount) {
                    ensureActive()
                    val aspect = source.pageAspectRatio(i).takeIf { it > 0f } ?: 1.4142f
                    // ~200 dpi on A4 width, capped for very tall pages so the bitmap fits in memory.
                    var w = JPG_WIDTH
                    var h = (w * aspect).roundToInt()
                    if (h > JPG_MAX_HEIGHT) { h = JPG_MAX_HEIGHT; w = (h / aspect).roundToInt() }
                    val bmp = source.renderPage(i, w, h)
                    val name = if (source.pageCount == 1) "$baseName.jpg" else "$baseName - page ${i + 1}.jpg"
                    val f = File(dir, safeName(name))
                    try {
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPG_QUALITY, it) }
                    } finally {
                        bmp.recycle()
                    }
                    files += f
                }
            } finally {
                source.close()
            }
        }.onFailure {
            AppLog.e("Export", "Exporting pages as JPG failed", it)
            dir.deleteRecursively()
            return@withContext emptyList()
        }
        files
    }

    /** A TXT, DOCX or XLSX file from page texts. Null on failure. */
    suspend fun textFile(format: ExportFormat, pages: List<String>, baseName: String): File? = withContext(io) {
        val f = File(shareDir(), safeName("$baseName.${format.extension}"))
        runCatching {
            f.outputStream().use { out ->
                when (format) {
                    ExportFormat.Txt -> out.write(
                        pages.mapIndexed { i, t -> if (pages.size > 1) "--- Page ${i + 1} ---\n$t" else t }
                            .joinToString("\n\n").toByteArray(Charsets.UTF_8),
                    )
                    ExportFormat.Word -> OfficeWriters.writeDocx(pages, baseName, out)
                    ExportFormat.Excel -> OfficeWriters.writeXlsx(pages, baseName, out)
                    ExportFormat.Jpg, ExportFormat.Pdf -> error("${format.name} is not a text format")
                }
            }
            f.takeIf { it.length() > 0 }
        }.onFailure {
            AppLog.e("Export", "Writing the ${format.extension} export failed", it)
            f.delete()
        }.getOrNull()
    }

    /** A copy of [source] (path or uri) in cache/share named [name], for sharing or saving. Null on failure. */
    suspend fun copyForExport(source: String, name: String): File? = withContext(io) {
        val f = File(shareDir(), safeName(name))
        runCatching {
            val input = if (source.startsWith("/")) File(source).inputStream()
            else context.contentResolver.openInputStream(source.toUri())
            input?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return@runCatching null
            f.takeIf { it.length() > 0 }
        }.onFailure { AppLog.e("Export", "Copying the PDF for export failed", it) }.getOrNull()
    }

    /**
     * Copies [source] (a file path or content/file uri) into [target], a uri from the system "Save"
     * picker. The picker creates the file before Sheaf writes it, so on failure the empty file is
     * removed again rather than left in the user's folder.
     */
    suspend fun writeTo(target: Uri, source: String): Boolean = withContext(io) {
        val ok = runCatching {
            val input = if (source.startsWith("/")) File(source).inputStream()
            else context.contentResolver.openInputStream(source.toUri())
            val output = context.contentResolver.openOutputStream(target, "wt")
            if (input == null || output == null) {
                input?.close(); output?.close()
                return@runCatching false
            }
            input.use { i -> output.use { o -> i.copyTo(o) } }
            true
        }.onFailure { AppLog.e("Export", "Saving to the chosen location failed", it) }.getOrDefault(false)
        if (!ok) discard(target)
        ok
    }

    /** Removes an empty file the "Save" picker created (pass null if the user cancelled). */
    suspend fun discard(target: Uri?) = withContext(io) {
        if (target != null) runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
    }

    /** Writes every file into the folder [tree] the user picked. Returns how many were saved. */
    suspend fun writeAllTo(tree: Uri, files: List<File>, mime: String): Int = withContext(io) {
        var saved = 0
        runCatching {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            for (f in files) {
                val doc = DocumentsContract.createDocument(context.contentResolver, parent, mime, f.name) ?: continue
                context.contentResolver.openOutputStream(doc, "wt")?.use { o -> f.inputStream().use { it.copyTo(o) } }
                    ?.let { saved++ }
            }
        }.onFailure { AppLog.e("Export", "Saving files to the chosen folder failed", it) }
        saved
    }

    /**
     * Android 10+: saves pictures straight into Pictures/Sheaf through MediaStore. An app may add its
     * own pictures there without any permission or folder prompt. Returns how many were saved.
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    suspend fun saveToPictures(files: List<File>): Int = withContext(io) {
        var saved = 0
        val resolver = context.contentResolver
        for (f in files) {
            runCatching {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Sheaf")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val item = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching
                val ok = resolver.openOutputStream(item)?.use { o -> f.inputStream().use { it.copyTo(o) }; true } ?: false
                if (ok) {
                    resolver.update(item, android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                    saved++
                } else {
                    resolver.delete(item, null, null)
                }
            }.onFailure { AppLog.e("Export", "Saving a picture to the gallery failed", it) }
        }
        saved
    }

    companion object {
        private const val JPG_WIDTH = 1654
        private const val JPG_MAX_HEIGHT = 6000
        private const val JPG_QUALITY = 90

        /**
         * A new, uniquely named subfolder of [root]. The file inside keeps a readable name ("Lease
         * (signed).pdf") but its FileProvider URI is unique, so an app that received an earlier share
         * can never read a later file through its old URI grant, and two exports never overwrite each other.
         */
        fun newShareFolder(root: File): File =
            File(root, java.util.UUID.randomUUID().toString().take(12)).apply { mkdirs() }

        /** File names other apps and file systems accept. */
        fun safeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().take(120).ifBlank { "Document" }

        /** "Lease.pdf" → "Lease"; used to name exports after the document. */
        fun baseName(displayName: String): String =
            displayName.substringBeforeLast('.', displayName).ifBlank { "Document" }
    }
}
