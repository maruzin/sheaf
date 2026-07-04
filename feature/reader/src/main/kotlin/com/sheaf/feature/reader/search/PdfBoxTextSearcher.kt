package com.sheaf.feature.reader.search

import android.content.Context
import android.net.Uri
import com.sheaf.core.domain.model.SearchHit
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Text search using PdfBox-Android. Extracts text page-by-page and reports the pages that contain
 * the query, with a short surrounding snippet. Runs on [io]. Heavy for very large PDFs — acceptable
 * for M1; can be pre-indexed later.
 */
class PdfBoxTextSearcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfTextSearcher {

    override suspend fun search(uri: String, query: String): List<SearchHit> = withContext(io) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        ensureInit()
        val hits = ArrayList<SearchHit>()
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                val total = doc.numberOfPages
                for (page in 1..total) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = runCatching { stripper.getText(doc) }.getOrDefault("")
                    val idx = text.indexOf(needle, ignoreCase = true)
                    if (idx >= 0) {
                        hits += SearchHit(
                            pageIndex = page - 1,
                            snippet = snippetAround(text, idx, needle.length),
                            boundsOnPage = emptyList(),
                        )
                    }
                }
            }
        }
        hits
    }

    private fun snippetAround(text: String, index: Int, length: Int): String {
        val start = max(0, index - 30)
        val end = min(text.length, index + length + 30)
        return text.substring(start, end).replace('\n', ' ').trim()
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
