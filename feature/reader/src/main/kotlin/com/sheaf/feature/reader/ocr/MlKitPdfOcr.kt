package com.sheaf.feature.reader.ocr

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
import kotlinx.coroutines.withContext
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
        runCatching {
            val dir = File(context.filesDir, "searchable").apply { mkdirs() }
            val out = File(dir, "ocr_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { doc ->
                    val renderer = PDFRenderer(doc)
                    for (i in 0 until doc.numberOfPages) {
                        runCatching { ocrPage(doc, renderer, i, recognizer) }
                    }
                    doc.save(out)
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    private fun ocrPage(
        doc: PDDocument,
        renderer: PDFRenderer,
        index: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
    ) {
        val page = doc.getPage(index)
        val bitmap = renderer.renderImage(index, RENDER_SCALE) ?: return
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val box = page.mediaBox
        val pageW = box.width
        val pageH = box.height
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val sx = pageW / bitmap.width
        val sy = pageH / bitmap.height
        val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        runCatching {
            cs.setRenderingMode(RenderingMode.NEITHER)
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val rect = line.boundingBox ?: continue
                    val text = sanitize(line.text)
                    if (text.isBlank()) continue
                    val fontSize = (rect.height() * sy).coerceIn(4f, 72f)
                    val x = rect.left * sx
                    val y = pageH - rect.bottom * sy
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
