package com.sheaf.feature.reader.merge

import android.content.Context
import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Appends each input to one destination with PdfBox's merger, which deep-clones pages, resources,
 * bookmarks and form fields. Inputs stay open until the destination is saved (PdfBox may still read
 * their streams); each uses the mixed RAM/disk policy, so big inputs spill to the cache.
 */
class PdfBoxMerger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfMerger {

    override suspend fun merge(uris: List<String>): MergeResult = withContext(io) {
        if (uris.size < 2) return@withContext MergeResult.Failed
        ensureInit()
        val dir = File(context.filesDir, "edited").apply { mkdirs() }
        val out = File(dir, "merged_${System.currentTimeMillis()}.pdf")
        val sources = ArrayList<PDDocument>(uris.size)
        val result = try {
            PDDocument(PdfBoxMemory.setting()).use { dst -> appendAll(dst, uris, sources, out) }
        } catch (_: Exception) {
            MergeResult.Failed
        } finally {
            sources.forEach { runCatching { it.close() } }
        }
        if (result is MergeResult.Success && out.length() > 0L) return@withContext result
        out.delete()
        if (result is MergeResult.Success) MergeResult.Failed else result
    }

    private fun appendAll(
        dst: PDDocument,
        uris: List<String>,
        sources: MutableList<PDDocument>,
        out: File,
    ): MergeResult {
        val merger = PDFMergerUtility()
        uris.forEachIndexed { index, uri ->
            val src = when (val loaded = load(uri)) {
                Loaded.Protected -> return MergeResult.Protected(index)
                Loaded.Unreadable -> return MergeResult.Unreadable(index)
                is Loaded.Ok -> loaded.document
            }
            sources += src
            // An empty user password opens without a prompt but stays encrypted; don't strip it.
            if (src.isEncrypted) return MergeResult.Protected(index)
            merger.appendDocument(dst, src)
        }
        dst.save(out)
        return MergeResult.Success(out.absolutePath, dst.numberOfPages)
    }

    private fun load(uri: String): Loaded = try {
        context.contentResolver.openInputStream(uri.toUri())?.use { input ->
            Loaded.Ok(PDDocument.load(input, PdfBoxMemory.setting()))
        } ?: Loaded.Unreadable
    } catch (_: InvalidPasswordException) {
        Loaded.Protected
    } catch (_: IOException) {
        Loaded.Unreadable
    } catch (_: SecurityException) {
        Loaded.Unreadable
    }

    private sealed interface Loaded {
        data class Ok(val document: PDDocument) : Loaded
        data object Protected : Loaded
        data object Unreadable : Loaded
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
