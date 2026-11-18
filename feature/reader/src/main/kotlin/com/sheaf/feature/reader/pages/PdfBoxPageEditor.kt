package com.sheaf.feature.reader.pages

import com.sheaf.core.data.log.AppLog
import android.content.Context
import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Writes the edited arrangement into a brand-new document, so a deleted page can never survive in the
 * saved file through a leftover reference (bookmark, link, form field, structure tree). What is carried
 * across is chosen explicitly:
 * - form fields are flattened into the page first (values stay visible; the copy is a print-ready PDF),
 * - bookmarks are rebuilt for pages that were kept,
 * - internal links are re-pointed at the kept page, or dropped if their target was deleted,
 * - every annotation's back-reference to its old page (/P) is removed.
 * A fresh document is also unencrypted, so owner-restricted sources save fine.
 */
class PdfBoxPageEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfPageEditor {

    override suspend fun save(uri: String, plan: List<PageOp>): String? = withContext(io) {
        if (plan.isEmpty()) return@withContext null
        ensureInit()
        val dir = File(context.filesDir, "edited").apply { mkdirs() }
        val out = File(dir, "pages_${System.currentTimeMillis()}.pdf")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, PdfBoxMemory.setting()).use { src ->
                    // Bake form values into the page content and remove the widgets + field tree.
                    runCatching { src.documentCatalog.acroForm?.flatten() }
                    PDDocument(PdfBoxMemory.setting()).use { dst ->
                        val srcPages = src.pages.toList()
                        val newPageFor = IdentityHashMap<Any, PDPage>()
                        plan.forEach { op ->
                            val page = srcPages.getOrNull(op.sourceIndex) ?: return@forEach
                            val imported = dst.importPage(page)
                            val base = ((page.rotation % 360) + 360) % 360
                            imported.rotation = ((base + op.rotation) % 360 + 360) % 360
                            // Article beads point into the source's thread tree.
                            imported.cosObject.removeItem(COSName.B)
                            newPageFor[page.cosObject] = imported
                        }
                        dst.pages.forEach { cleanAnnotations(it, newPageFor) }
                        rebuildOutline(src, dst, newPageFor)
                        src.documentInformation?.let { dst.documentInformation = it }
                        dst.save(out)
                    }
                }
                true
            } ?: false
        }.onFailure { AppLog.e("Pages", "Saving the edited page arrangement failed", it) }.getOrDefault(false)
        if (ok && out.length() > 0) {
            out.absolutePath
        } else {
            out.delete()
            null
        }
    }

    private fun cleanAnnotations(page: PDPage, newPageFor: Map<Any, PDPage>) {
        val annotations = runCatching { page.annotations }.getOrNull() ?: return
        val kept = annotations.filter { annotation ->
            if (annotation is PDAnnotationWidget) return@filter false // flattened above; drop strays
            annotation.cosObject.removeItem(COSName.P)
            if (annotation is PDAnnotationLink) retargetLink(annotation, newPageFor) else true
        }
        page.annotations = kept
    }

    /** Points an internal link at the kept copy of its target page; false drops the link. */
    private fun retargetLink(link: PDAnnotationLink, newPageFor: Map<Any, PDPage>): Boolean {
        val dest = runCatching {
            link.destination ?: (link.action as? PDActionGoTo)?.destination
        }.getOrNull() ?: return true // external (URI) links carry no page reference
        val pageDest = dest as? PDPageDestination ?: return false // named dest into the old catalog
        val target = pageDest.page?.cosObject ?: return false
        val replacement = newPageFor[target] ?: return false
        pageDest.setPage(replacement)
        return true
    }

    private fun rebuildOutline(src: PDDocument, dst: PDDocument, newPageFor: Map<Any, PDPage>) {
        val outline = runCatching { src.documentCatalog.documentOutline }.getOrNull() ?: return
        val rebuilt = PDDocumentOutline()
        copyChildren(outline, rebuilt, newPageFor)
        if (rebuilt.hasChildren()) dst.documentCatalog.documentOutline = rebuilt
    }

    private fun copyChildren(from: PDOutlineNode, to: PDOutlineNode, newPageFor: Map<Any, PDPage>) {
        for (item in from.children()) {
            val target = runCatching {
                val dest = item.destination ?: (item.action as? PDActionGoTo)?.destination
                (dest as? PDPageDestination)?.page?.cosObject
            }.getOrNull()
            val copy = PDOutlineItem().apply { title = item.title }
            target?.let { newPageFor[it] }?.let { copy.setDestination(it) }
            copyChildren(item, copy, newPageFor)
            // Keep an entry if it still leads somewhere, or if it groups surviving children.
            if (copy.destination != null || copy.hasChildren()) to.addLast(copy)
        }
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
