package com.sheaf.feature.reader.search

import android.content.Context
import android.net.Uri
import com.sheaf.core.domain.model.OutlineEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Reads the PDF outline (bookmarks) via PdfBox-Android and maps each entry to a 0-based page. */
class PdfBoxOutlineExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfOutlineExtractor {

    override suspend fun outline(uri: String): List<OutlineEntry> = withContext(io) {
        ensureInit()
        val entries = ArrayList<OutlineEntry>()
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { doc ->
                    val root = doc.documentCatalog?.documentOutline ?: return@use
                    collect(root, 0, doc, entries)
                }
            }
        }
        entries
    }

    private fun collect(
        node: PDOutlineNode,
        depth: Int,
        doc: PDDocument,
        out: MutableList<OutlineEntry>,
    ) {
        var item: PDOutlineItem? = node.firstChild
        while (item != null) {
            val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
            val idx = if (page != null) runCatching { doc.pages.indexOf(page) }.getOrDefault(-1) else -1
            out += OutlineEntry(
                title = item.title?.trim().orEmpty(),
                pageIndex = if (idx >= 0) idx else 0,
                depth = depth,
            )
            if (out.size > 2000) return // guard against pathological outlines
            collect(item, depth + 1, doc, out)
            item = item.nextSibling
        }
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
