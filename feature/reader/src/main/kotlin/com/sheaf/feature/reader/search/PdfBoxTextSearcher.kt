package com.sheaf.feature.reader.search

import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Page text extraction using PdfBox-Android, run on [io]. The document is parsed once per call with
 * a disk-backed memory policy; callers cache the returned list (see ReaderViewModel).
 */
class PdfBoxTextSearcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfTextSearcher {

    override suspend fun pageTexts(uri: String): List<String> = withContext(io) {
        ensureInit()
        val pages = ArrayList<String>()
        context.contentResolver.openInputStream(uri.toUri())?.use { input ->
            PDDocument.load(input, PdfBoxMemory.setting()).use { doc ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                for (page in 1..doc.numberOfPages) {
                    ensureActive() // stop promptly if the reader closes mid-extraction
                    stripper.startPage = page
                    stripper.endPage = page
                    pages += runCatching { stripper.getText(doc) }.getOrDefault("")
                }
            }
        }
        pages
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
