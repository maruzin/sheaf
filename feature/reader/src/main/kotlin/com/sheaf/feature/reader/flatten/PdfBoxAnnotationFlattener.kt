package com.sheaf.feature.reader.flatten

import android.content.Context
import android.net.Uri
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Draws overlay annotations into the PDF content so they travel with the file. Ink/highlight strokes
 * are drawn on the page content stream (highlights at 30% alpha, wider); notes become real PDF sticky
 * notes. Coordinates are page-normalized (0..1, top-left origin) → PDF points (bottom-left origin).
 */
class PdfBoxAnnotationFlattener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : AnnotationFlattener {

    override suspend fun flatten(
        uri: String,
        annotationsByPage: Map<Int, List<Annotation>>,
    ): String? = withContext(io) {
        if (annotationsByPage.isEmpty()) return@withContext null
        ensureInit()
        runCatching {
            val dir = File(context.filesDir, "signed").apply { mkdirs() }
            val out = File(dir, "signed_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { doc ->
                    val pages = doc.pages.toList()
                    annotationsByPage.forEach { (pageIndex, list) ->
                        val page = pages.getOrNull(pageIndex) ?: return@forEach
                        val box = page.mediaBox
                        val pw = box.width
                        val ph = box.height
                        val llx = box.lowerLeftX
                        val lly = box.lowerLeftY
                        fun px(nx: Float) = llx + nx * pw
                        fun py(ny: Float) = lly + (1f - ny) * ph

                        val strokes = list.filter { it.type != AnnotationType.Note && it.points.size >= 2 }
                        if (strokes.isNotEmpty()) {
                            val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                            runCatching {
                                cs.setLineCapStyle(1)
                                strokes.forEach { a ->
                                    val (r, g, b) = rgb(a.colorArgb)
                                    val gs = PDExtendedGraphicsState()
                                    gs.strokingAlphaConstant = if (a.type == AnnotationType.Highlight) 0.3f else 1f
                                    cs.setGraphicsStateParameters(gs)
                                    cs.setStrokingColor(r, g, b)
                                    cs.setLineWidth((a.strokeWidth * pw).coerceAtLeast(0.5f))
                                    val p0 = a.points.first()
                                    cs.moveTo(px(p0.x), py(p0.y))
                                    for (i in 1 until a.points.size) {
                                        val p = a.points[i]
                                        cs.lineTo(px(p.x), py(p.y))
                                    }
                                    cs.stroke()
                                }
                            }
                            cs.close()
                        }

                        list.filter { it.type == AnnotationType.Note }.forEach { a ->
                            val p = a.points.firstOrNull() ?: return@forEach
                            runCatching {
                                val note = PDAnnotationText()
                                note.contents = a.note.orEmpty()
                                note.name = PDAnnotationText.NAME_NOTE
                                val x = px(p.x)
                                val y = py(p.y)
                                note.rectangle = PDRectangle(x, y - 18f, 18f, 18f)
                                val (r, g, b) = rgb(a.colorArgb)
                                note.color = PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)
                                page.annotations.add(note)
                            }
                        }
                    }
                    doc.save(out)
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    private fun rgb(argb: Int): Triple<Float, Float, Float> =
        Triple(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
