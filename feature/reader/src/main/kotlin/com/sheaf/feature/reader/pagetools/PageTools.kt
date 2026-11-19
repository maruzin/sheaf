package com.sheaf.feature.reader.pagetools

import android.content.Context
import androidx.core.net.toUri
import com.sheaf.core.data.log.AppLog
import com.sheaf.feature.reader.pdf.PageFrame
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Where page numbers go. */
enum class NumberPosition(val label: String) { BottomCenter("Bottom centre"), BottomRight("Bottom right"), TopRight("Top right") }

/**
 * Whole-document page tools. Each writes a new PDF in app storage (`edited/`) and returns its path,
 * or null on failure; the source is never changed. Placement uses each page as displayed
 * ([PageFrame]: crop box and /Rotate), so numbers and watermarks sit upright on turned pages too.
 */
class PageTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) {
    private fun output(tag: String) =
        File(File(context.filesDir, "edited").apply { mkdirs() }, "${tag}_${System.currentTimeMillis()}.pdf")

    private inline fun edit(uri: String, tag: String, crossinline block: (PDDocument) -> Unit): String? {
        ensureInit()
        val out = output(tag)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, PdfBoxMemory.setting()).use { doc ->
                    block(doc)
                    doc.save(out)
                }
                true
            } ?: false
        }.onFailure { AppLog.e("PageTools", "Page tool '$tag' failed", it) }.getOrDefault(false)
        return if (ok && out.length() > 0) out.absolutePath else { out.delete(); null }
    }

    /** "1", "2"… (or "Page 1 of 9") on every page, starting at [startAt]. */
    suspend fun addPageNumbers(uri: String, position: NumberPosition, startAt: Int = 1, withTotal: Boolean = false): String? =
        withContext(io) {
            edit(uri, "numbered") { doc ->
                val total = doc.numberOfPages + startAt - 1
                doc.pages.forEachIndexed { i, page ->
                    val n = i + startAt
                    val label = if (withTotal) "Page $n of $total" else "$n"
                    stampText(doc, page, label, size = 10f, alpha = 1f) { frame, w ->
                        val margin = 28f
                        when (position) {
                            NumberPosition.BottomCenter -> (frame.displayWidth - w) / 2 to margin
                            NumberPosition.BottomRight -> frame.displayWidth - w - margin to margin
                            NumberPosition.TopRight -> frame.displayWidth - w - margin to frame.displayHeight - margin - 10f
                        }
                    }
                }
            }
        }

    /** Large diagonal [text] across every page at [opacity] (0.05..0.6). */
    suspend fun addWatermark(uri: String, text: String, opacity: Float = 0.18f): String? = withContext(io) {
        val clean = text.filter { it.code in 0x20..0x7E }.trim().take(40)
        if (clean.isEmpty()) return@withContext null
        edit(uri, "watermarked") { doc ->
            doc.pages.forEach { page ->
                val frame = PageFrame.of(page)
                val size = (minOf(frame.displayWidth, frame.displayHeight) / 8f).coerceIn(18f, 96f)
                val font = PDType1Font.HELVETICA_BOLD
                val textW = font.getStringWidth(clean) / 1000f * size
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 0.6f) })
                    cs.setNonStrokingColor(0.55f, 0.55f, 0.55f)
                    // Centre of the displayed page, rotated 35 degrees on screen, then mapped to PDF space.
                    val (cx, cy) = frame.toPdf(0.5f, 0.5f)
                    val angle = Math.toRadians(35.0 + frame.rotation)
                    cs.beginText()
                    cs.setFont(font, size)
                    val m = Matrix.getRotateInstance(angle, cx, cy)
                    m.translate(-textW / 2f, -size / 3f)
                    cs.setTextMatrix(m)
                    cs.showText(clean)
                    cs.endText()
                    cs.restoreGraphicsState()
                }
            }
        }
    }

    /** Trims [fraction] (0..0.3) off every side of every page (crop box; nothing is deleted from the file). */
    suspend fun cropMargins(uri: String, fraction: Float): String? = withContext(io) {
        val f = fraction.coerceIn(0f, 0.3f)
        edit(uri, "cropped") { doc ->
            doc.pages.forEach { page ->
                val box = page.cropBox
                val dx = box.width * f; val dy = box.height * f
                page.cropBox = PDRectangle(box.lowerLeftX + dx, box.lowerLeftY + dy, box.width - 2 * dx, box.height - 2 * dy)
            }
        }
    }

    /** A blank page the size of page [afterIndex], inserted after it (-1 = at the start). */
    suspend fun insertBlank(uri: String, afterIndex: Int): String? = withContext(io) {
        edit(uri, "blank") { doc ->
            val ref = doc.getPage(afterIndex.coerceIn(0, doc.numberOfPages - 1))
            val frame = PageFrame.of(ref)
            val blank = PDPage(PDRectangle(frame.displayWidth, frame.displayHeight))
            if (afterIndex < 0) doc.pages.insertBefore(blank, doc.getPage(0))
            else if (afterIndex >= doc.numberOfPages - 1) doc.addPage(blank)
            else doc.pages.insertAfter(blank, doc.getPage(afterIndex))
        }
    }

    /** Every page of [otherUri], inserted after page [afterIndex] of [uri]. */
    suspend fun insertPdf(uri: String, otherUri: String, afterIndex: Int): String? = withContext(io) {
        ensureInit()
        val out = output("inserted")
        val ok = runCatching {
            PDDocument.load(context.contentResolver.openInputStream(uri.toUri()), PdfBoxMemory.setting()).use { doc ->
                PDDocument.load(context.contentResolver.openInputStream(otherUri.toUri()), PdfBoxMemory.setting()).use { other ->
                    val result = PDDocument(PdfBoxMemory.setting())
                    result.use {
                        val merger = PDFMergerUtility()
                        val head = PDDocument(PdfBoxMemory.setting()); val tail = PDDocument(PdfBoxMemory.setting())
                        head.use { tail.use {
                            doc.pages.forEachIndexed { i, p -> if (i <= afterIndex) head.importPage(p) else tail.importPage(p) }
                            merger.appendDocument(result, head)
                            merger.appendDocument(result, other)
                            merger.appendDocument(result, tail)
                            result.save(out)
                        } }
                    }
                }
            }
            true
        }.onFailure { AppLog.e("PageTools", "Inserting a PDF failed", it) }.getOrDefault(false)
        if (ok && out.length() > 0) out.absolutePath else { out.delete(); null }
    }

    /** Two PDFs: pages up to and including [afterIndex], and the rest. */
    suspend fun splitAfter(uri: String, afterIndex: Int): Pair<String, String>? = withContext(io) {
        ensureInit()
        val a = output("part1"); val b = output("part2")
        val ok = runCatching {
            PDDocument.load(context.contentResolver.openInputStream(uri.toUri()), PdfBoxMemory.setting()).use { doc ->
                if (afterIndex < 0 || afterIndex >= doc.numberOfPages - 1) return@runCatching false
                PDDocument(PdfBoxMemory.setting()).use { first ->
                    PDDocument(PdfBoxMemory.setting()).use { second ->
                        doc.pages.forEachIndexed { i, p -> if (i <= afterIndex) first.importPage(p) else second.importPage(p) }
                        first.save(a); second.save(b)
                    }
                }
            }
            true
        }.onFailure { AppLog.e("PageTools", "Splitting failed", it) }.getOrDefault(false)
        if (ok && a.length() > 0 && b.length() > 0) a.absolutePath to b.absolutePath else { a.delete(); b.delete(); null }
    }

    /** Draws [text] on [page] at the display position [at] returns (bottom-left of the text, display space, y up). */
    private fun stampText(
        doc: PDDocument,
        page: PDPage,
        text: String,
        size: Float,
        alpha: Float,
        at: (PageFrame, Float) -> Pair<Float, Float>,
    ) {
        val frame = PageFrame.of(page)
        val font = PDType1Font.HELVETICA
        val w = font.getStringWidth(text) / 1000f * size
        val (dx, dyUp) = at(frame, w)
        // Display coordinates (y up from the bottom) → normalized (y down) → PDF space.
        val (px, py) = frame.toPdf(dx / frame.displayWidth, 1f - dyUp / frame.displayHeight)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.saveGraphicsState()
            if (alpha < 1f) cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha })
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f)
            cs.beginText()
            cs.setFont(font, size)
            cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(frame.rotation.toDouble()), px, py))
            cs.showText(text)
            cs.endText()
            cs.restoreGraphicsState()
        }
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
