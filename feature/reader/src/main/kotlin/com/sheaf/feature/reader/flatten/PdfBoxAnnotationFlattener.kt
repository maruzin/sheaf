package com.sheaf.feature.reader.flatten

import com.sheaf.core.data.log.AppLog
import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PageFrame
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import android.content.Context
import android.net.Uri
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import com.sheaf.feature.reader.signature.SignaturePlacement
import com.sheaf.feature.reader.signature.SignatureStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Draws overlay annotations into the PDF content so they travel with the file. Ink/highlight strokes
 * are drawn on the page content stream (highlights at 30% alpha, wider); notes become real PDF sticky
 * notes, signatures are drawn as transparent images (moved, scaled and turned as placed).
 * Coordinates are page-normalized (0..1, top-left origin) → PDF points (bottom-left origin).
 */
class PdfBoxAnnotationFlattener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
    private val signatures: SignatureStore,
) : AnnotationFlattener {

    override suspend fun flatten(
        uri: String,
        annotationsByPage: Map<Int, List<Annotation>>,
    ): String? = withContext(io) {
        if (annotationsByPage.isEmpty()) return@withContext null
        ensureInit()
        runCatching {
            // Share-only output: cacheDir (served by the FileProvider).
            val dir = File(context.cacheDir, "signed").apply { mkdirs() }
            val out = File(dir, "signed_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, PdfBoxMemory.setting()).use { doc ->
                    val pages = doc.pages.toList()
                    // One embedded image per signature file, however many times it is placed.
                    val images = HashMap<String, PDImageXObject>()
                    val redactPages = sortedSetOf<Int>()
                    val pendingFields = ArrayList<Pair<Int, com.sheaf.feature.reader.markup.ShapeSpec>>()
                    annotationsByPage.forEach { (pageIndex, list) ->
                        val page = pages.getOrNull(pageIndex) ?: return@forEach
                        // Annotations are measured on the page as displayed (crop box + /Rotate).
                        val frame = PageFrame.of(page)
                        val pw = frame.displayWidth

                        val strokes = list.filter {
                            it.type != AnnotationType.Note && it.type != AnnotationType.Signature &&
                                it.type != AnnotationType.Shape && it.points.size >= 2
                        }
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
                                    val (x0, y0) = frame.toPdf(a.points.first().x, a.points.first().y)
                                    cs.moveTo(x0, y0)
                                    for (i in 1 until a.points.size) {
                                        val (x, y) = frame.toPdf(a.points[i].x, a.points[i].y)
                                        cs.lineTo(x, y)
                                    }
                                    cs.stroke()
                                }
                            }
                            cs.close()
                        }

                        val shapes = list.mapNotNull { com.sheaf.feature.reader.markup.ShapeSpec.of(it) }
                        if (shapes.isNotEmpty()) {
                            val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                            try {
                                shapes.filter { !it.kind.isFormField }.forEach { drawShape(cs, it, frame) }
                            } finally {
                                cs.close()
                            }
                            // Fields are added at the very end, after any redaction replaced pages.
                            shapes.filter { it.kind.isFormField }.forEach { pendingFields += pageIndex to it }
                            if (shapes.any { it.kind == com.sheaf.feature.reader.markup.ShapeKind.Redact }) redactPages += pageIndex
                        }

                        val placed = list.mapNotNull { SignaturePlacement.of(it) }
                        if (placed.isNotEmpty()) {
                            val aspect = frame.displayHeight / frame.displayWidth.coerceAtLeast(1f)
                            val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                            try {
                                for (p in placed) {
                                    val image = images[p.image] ?: signatures.load(p.image)
                                        // Photos (no transparency) as JPEG; ink and stamps keep their alpha.
                                        ?.let { bmp ->
                                            if (bmp.hasAlpha()) LosslessFactory.createFromImage(doc, bmp)
                                            else com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, bmp, 0.9f)
                                        }
                                        ?.also { images[p.image] = it }
                                    if (image == null) {
                                        AppLog.w("Flatten", "A placed signature's image is missing; skipped")
                                        continue
                                    }
                                    val (bl, br, tl) = p.imageCorners(aspect)
                                    val (ox, oy) = frame.toPdf(bl.x, bl.y)
                                    val (rx, ry) = frame.toPdf(br.x, br.y)
                                    val (tx, ty) = frame.toPdf(tl.x, tl.y)
                                    // Unit square → the placed box: x axis along the bottom edge, y up the left edge.
                                    cs.drawImage(image, Matrix(rx - ox, ry - oy, tx - ox, ty - oy, ox, oy))
                                }
                            } finally {
                                cs.close()
                            }
                        }

                        list.filter { it.type == AnnotationType.Note }.forEach { a ->
                            val p = a.points.firstOrNull() ?: return@forEach
                            runCatching {
                                val note = PDAnnotationText()
                                note.contents = a.note.orEmpty()
                                note.name = PDAnnotationText.NAME_NOTE
                                val (x, y) = frame.toPdf(p.x, p.y)
                                note.rectangle = PDRectangle(x, y - 18f, 18f, 18f)
                                val (r, g, b) = rgb(a.colorArgb)
                                note.color = PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)
                                page.annotations.add(note)
                            }
                        }
                    }
                    // Redaction: the page becomes a picture of itself (boxes included), so the text,
                    // images and annotations under the black boxes are gone, not just covered.
                    if (redactPages.isNotEmpty()) rasterize(doc, redactPages)
                    pendingFields.forEach { (index, spec) ->
                        val page = doc.getPage(index)
                        addFormField(doc, page, PageFrame.of(page), spec)
                    }
                    doc.save(out)
                }
            }
            // Only report success if something was actually written (null stream = unreadable source).
            out.takeIf { it.length() > 0 }?.absolutePath
        }.onFailure { AppLog.e("Flatten", "Building the marked-up copy failed", it) }.getOrNull()
    }

    private fun drawShape(cs: PDPageContentStream, sh: com.sheaf.feature.reader.markup.ShapeSpec, frame: PageFrame) {
        val (r, g, b) = rgb(sh.drawColor)
        val pw = frame.displayWidth
        val (x0, y0) = frame.toPdf(sh.start.x, sh.start.y)
        val (x1, y1) = frame.toPdf(sh.end.x, sh.end.y)
        cs.saveGraphicsState()
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        cs.setStrokingColor(r, g, b)
        cs.setNonStrokingColor(r, g, b)
        cs.setLineWidth((sh.widthFrac * pw).coerceAtLeast(0.5f))
        when (sh.kind) {
            com.sheaf.feature.reader.markup.ShapeKind.Line -> { cs.moveTo(x0, y0); cs.lineTo(x1, y1); cs.stroke() }
            com.sheaf.feature.reader.markup.ShapeKind.Arrow -> {
                cs.moveTo(x0, y0); cs.lineTo(x1, y1); cs.stroke()
                // Head computed on the displayed page (y down), then mapped like every other point.
                val w = frame.displayWidth; val h = frame.displayHeight
                sh.arrowHead(w, h).forEach { (hx, hy) ->
                    val (px, py) = frame.toPdf(hx / w, hy / h)
                    cs.moveTo(x1, y1); cs.lineTo(px, py); cs.stroke()
                }
            }
            com.sheaf.feature.reader.markup.ShapeKind.Rectangle,
            com.sheaf.feature.reader.markup.ShapeKind.WhiteOut,
            com.sheaf.feature.reader.markup.ShapeKind.Redact -> {
                // Four corners through the frame, so a rotated page gets the right box.
                val (ax, ay) = frame.toPdf(sh.start.x, sh.end.y)
                val (bx, by) = frame.toPdf(sh.end.x, sh.start.y)
                cs.moveTo(x0, y0); cs.lineTo(bx, by); cs.lineTo(x1, y1); cs.lineTo(ax, ay); cs.closePath()
                if (sh.filled) cs.fill() else cs.stroke()
            }
            com.sheaf.feature.reader.markup.ShapeKind.Ellipse -> {
                // Four Bezier arcs around the box, in display space, mapped point by point.
                val l = minOf(sh.start.x, sh.end.x); val rr = maxOf(sh.start.x, sh.end.x)
                val t = minOf(sh.start.y, sh.end.y); val bb = maxOf(sh.start.y, sh.end.y)
                val cx = (l + rr) / 2; val cy = (t + bb) / 2; val rx = (rr - l) / 2; val ry = (bb - t) / 2
                val k = 0.5523f
                fun p(x: Float, y: Float) = frame.toPdf(x, y)
                val (sx, sy) = p(cx + rx, cy); cs.moveTo(sx, sy)
                val arcs = listOf(
                    floatArrayOf(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry),
                    floatArrayOf(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy),
                    floatArrayOf(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry),
                    floatArrayOf(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy),
                )
                arcs.forEach { a ->
                    val (ax1, ay1) = p(a[0], a[1]); val (ax2, ay2) = p(a[2], a[3]); val (ax3, ay3) = p(a[4], a[5])
                    cs.curveTo(ax1, ay1, ax2, ay2, ax3, ay3)
                }
                cs.stroke()
            }
            com.sheaf.feature.reader.markup.ShapeKind.FormText,
            com.sheaf.feature.reader.markup.ShapeKind.FormCheckbox -> Unit // become real fields, see addFormField
        }
        cs.restoreGraphicsState()
    }

    /** Turns a form-field shape into a real AcroForm field (text field or checkbox) on [page]. */
    private fun addFormField(doc: PDDocument, page: PDPage, frame: PageFrame, sh: com.sheaf.feature.reader.markup.ShapeSpec) {
        runCatching {
            val catalog = doc.documentCatalog
            val acro = catalog.acroForm ?: com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm(doc).also { catalog.acroForm = it }
            val helv = com.tom_roush.pdfbox.cos.COSName.getPDFName("Helv")
            val res = acro.defaultResources ?: com.tom_roush.pdfbox.pdmodel.PDResources().also { acro.defaultResources = it }
            if (res.getFont(helv) == null) res.put(helv, com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA)
            if (acro.defaultAppearance.isNullOrBlank()) acro.defaultAppearance = "/Helv 0 Tf 0 g"
            acro.needAppearances = true
            // Axis-aligned box in PDF space from two opposite corners (pages turn in quarter turns).
            val (ax, ay) = frame.toPdf(sh.start.x, sh.start.y)
            val (bx, by) = frame.toPdf(sh.end.x, sh.end.y)
            val rect = PDRectangle(minOf(ax, bx), minOf(ay, by), kotlin.math.abs(ax - bx), kotlin.math.abs(ay - by))
            val taken = acro.fieldTree.map { it.fullyQualifiedName }.toSet()
            fun name(base: String): String { var i = 1; while ("$base $i" in taken) i++; return "$base $i" }
            val border = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary(com.tom_roush.pdfbox.cos.COSDictionary()).apply {
                borderColour = PDColor(floatArrayOf(0.1f, 0.34f, 0.86f), PDDeviceRGB.INSTANCE)
            }
            if (sh.kind == com.sheaf.feature.reader.markup.ShapeKind.FormText) {
                val field = com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField(acro)
                field.partialName = name("Text")
                field.defaultAppearance = "/Helv 0 Tf 0 g" // 0 = auto size to the box
                field.isMultiline = rect.height > 40f
                val w = field.widgets.first()
                w.rectangle = rect; w.page = page; w.isPrinted = true; w.appearanceCharacteristics = border
                page.annotations.add(w); acro.fields.add(field)
            } else {
                val field = com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox(acro)
                field.partialName = name("Checkbox")
                val w = field.widgets.first()
                w.rectangle = rect; w.page = page; w.isPrinted = true; w.appearanceCharacteristics = border
                // Explicit on / off looks, so every viewer can show and toggle it.
                fun look(on: Boolean): com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream {
                    val ap = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream(doc)
                    ap.bBox = PDRectangle(rect.width, rect.height)
                    ap.resources = com.tom_roush.pdfbox.pdmodel.PDResources()
                    PDPageContentStream(doc, ap).use { cs ->
                        cs.setStrokingColor(0.1f, 0.34f, 0.86f); cs.setLineWidth(1f)
                        cs.addRect(0.5f, 0.5f, rect.width - 1f, rect.height - 1f); cs.stroke()
                        if (on) {
                            val s = minOf(rect.width, rect.height)
                            cs.setStrokingColor(0f, 0f, 0f); cs.setLineWidth((s * 0.1f).coerceAtLeast(1f))
                            cs.moveTo(rect.width * 0.22f, rect.height * 0.52f)
                            cs.lineTo(rect.width * 0.42f, rect.height * 0.28f)
                            cs.lineTo(rect.width * 0.8f, rect.height * 0.75f); cs.stroke()
                        }
                    }
                    return ap
                }
                val states = com.tom_roush.pdfbox.cos.COSDictionary().apply {
                    setItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("Yes"), look(true))
                    setItem(com.tom_roush.pdfbox.cos.COSName.Off, look(false))
                }
                w.appearance = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary().apply {
                    normalAppearance = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry(states)
                }
                w.setAppearanceState("Off")
                page.annotations.add(w); acro.fields.add(field)
            }
        }.onFailure { AppLog.e("Flatten", "Adding a form field failed", it) }
    }

    /** Replaces each page in [indexes] with a 200 dpi image of itself (what it shows, nothing hidden). */
    private fun rasterize(doc: PDDocument, indexes: Set<Int>) {
        val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
        val rendered = indexes.associateWith { i ->
            val page = doc.getPage(i)
            val frame = PageFrame.of(page)
            val bmp = renderer.renderImageWithDPI(i, REDACT_DPI)
            Triple(frame.displayWidth, frame.displayHeight, bmp)
        }
        // Rendered first, replaced after: replacing changes the page list the renderer reads.
        rendered.toSortedMap().forEach { (i, t) ->
            val (w, h, bmp) = t
            val old = doc.getPage(i)
            val fresh = PDPage(PDRectangle(w, h))
            val img = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, bmp, 0.92f)
            bmp.recycle()
            PDPageContentStream(doc, fresh).use { it.drawImage(img, 0f, 0f, w, h) }
            doc.pages.insertBefore(fresh, old)
            doc.pages.remove(old)
        }
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
        const val REDACT_DPI = 200f
        val initialized = AtomicBoolean(false)
    }
}
