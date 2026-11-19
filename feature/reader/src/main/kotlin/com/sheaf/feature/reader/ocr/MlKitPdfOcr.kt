package com.sheaf.feature.reader.ocr

import com.sheaf.core.data.log.AppLog
import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PageFrame
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * OCR via ML Kit (on-device, Latin). Renders each page to a bitmap, recognizes text, then draws it
 * back onto the page in invisible render mode so a standard text extractor (and in-app search) can
 * find it. Positions are approximate — the layer exists for searchability, not display.
 */
class MlKitPdfOcr @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfOcr {

    override suspend fun makeSearchable(uri: String): String? = withContext(io) {
        ensureInit()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val dir = File(context.filesDir, "searchable").apply { mkdirs() }
        val out = File(dir, "ocr_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, PdfBoxMemory.setting()).use { doc ->
                    val renderer = PDFRenderer(doc)
                    for (i in 0 until doc.numberOfPages) {
                        // Leaving the reader cancels the job: stop between pages instead of burning
                        // CPU for minutes on a result nobody will see.
                        ensureActive()
                        runCatching { ocrPage(doc, renderer, i, recognizer) }
                    }
                    doc.save(out)
                }
            }
            // Only report success if something was actually written (null stream = unreadable source).
            out.takeIf { it.length() > 0 }?.absolutePath ?: run { out.delete(); null }
        } catch (e: CancellationException) {
            out.delete()
            throw e
        } catch (e: Exception) {
            AppLog.e("OCR", "Making a document searchable failed", e)
            out.delete()
            null
        } finally {
            recognizer.close()
        }
    }

    override suspend fun recognizeText(uri: String, password: String?): List<String>? = withContext(io) {
        ensureInit()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                val doc = if (password != null) PDDocument.load(input, password, PdfBoxMemory.setting())
                else PDDocument.load(input, PdfBoxMemory.setting())
                doc.use {
                    val renderer = PDFRenderer(doc)
                    (0 until doc.numberOfPages).map { i ->
                        ensureActive()
                        val bitmap = renderer.renderImage(i, RENDER_SCALE)
                        try {
                            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                            // Blocks top to bottom, lines in reading order: plain text a person can edit.
                            result.textBlocks.joinToString("\n\n") { b -> b.lines.joinToString("\n") { it.text } }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("OCR", "Recognizing text failed", e)
            null
        } finally {
            recognizer.close()
        }
    }

    private fun ocrPage(
        doc: PDDocument,
        renderer: PDFRenderer,
        index: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
    ) {
        val page = doc.getPage(index)
        val bitmap = renderer.renderImage(index, RENDER_SCALE) ?: return
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0) return
        val result = try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        } finally {
            // Recognition is done with the pixels; free them now, not at the next GC (OOM on long scans).
            bitmap.recycle()
        }
        // The rendered bitmap shows the page as displayed (crop box + /Rotate); map through that.
        val frame = PageFrame.of(page)
        val sy = frame.displayHeight / bh
        val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        runCatching {
            cs.setRenderingMode(RenderingMode.NEITHER)
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val rect = line.boundingBox ?: continue
                    val text = sanitize(line.text)
                    if (text.isBlank()) continue
                    val fontSize = (rect.height() * sy).coerceIn(4f, 72f)
                    val (x, y) = frame.toPdf(
                        rect.left.toFloat() / bw,
                        rect.bottom.toFloat() / bh,
                    )
                    runCatching {
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, fontSize)
                        cs.newLineAtOffset(x, y)
                        cs.showText(text)
                        cs.endText()
                    }
                }
            }
        }
        cs.close()
    }

    /** Keep only characters the standard Helvetica (WinAnsi) encoding can render. */
    private fun sanitize(text: String): String =
        buildString { for (ch in text) append(if (ch.code in 0x20..0x7E) ch else ' ') }.trim()

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        const val RENDER_SCALE = 2f
        val initialized = AtomicBoolean(false)
    }
}
