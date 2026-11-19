package com.sheaf.feature.reader.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.sheaf.core.data.log.AppLog
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** ID document sizes for true-to-size copies (ISO/IEC 7810). */
enum class IdKind(val label: String, val widthMm: Float, val heightMm: Float) {
    /** ID-1: national ID cards, driving licences, bank cards. */
    IdCard("ID card / licence", 85.6f, 53.98f),
    /** ID-3: passport data page. */
    Passport("Passport", 125f, 88f),
}

/**
 * Turns pictures into PDFs Sheaf can open, sign and share:
 * - [imagesToPdf]: each photo or image becomes one page (so any picture can be signed);
 * - [idCopy]: the front and back of an ID card or passport, true to size on one A4 page, with an
 *   optional watermark ("Copy for … only") across both sides, the standard way to stop a copy being reused.
 */
class ImagePdfBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) {

    /** A PDF with one page per image (in order), saved under [folder] in app storage. Null on failure. */
    suspend fun imagesToPdf(images: List<Uri>, folder: String = "opened"): File? = withContext(io) {
        if (images.isEmpty()) return@withContext null
        ensureInit()
        val out = File(File(context.filesDir, folder).apply { mkdirs() }, "image_${System.currentTimeMillis()}.pdf")
        runCatching {
            PDDocument(PdfBoxMemory.setting()).use { doc ->
                for (uri in images) {
                    val bmp = decode(uri, MAX_SIDE) ?: continue
                    try {
                        // Long side = A4 long side (842 pt): prints at a sensible size, keeps the shape.
                        val scale = A4.height / max(bmp.width, bmp.height)
                        val page = PDPage(PDRectangle(bmp.width * scale, bmp.height * scale))
                        doc.addPage(page)
                        val img = JPEGFactory.createFromImage(doc, bmp, JPEG_QUALITY)
                        PDPageContentStream(doc, page).use { cs ->
                            cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                        }
                    } finally {
                        bmp.recycle()
                    }
                }
                if (doc.numberOfPages == 0) return@runCatching null
                doc.save(out)
            }
            out.takeIf { it.length() > 0 }
        }.onFailure { AppLog.e("ImagePdf", "Turning pictures into a PDF failed", it) }.getOrNull()
            ?: run { out.delete(); null }
    }

    /** Front (and back) of an ID document, true to size on one A4 page. Null on failure. */
    suspend fun idCopy(front: Uri, back: Uri?, kind: IdKind, watermark: String): File? = withContext(io) {
        ensureInit()
        val out = File(File(context.filesDir, "scanned").apply { mkdirs() }, "id_${System.currentTimeMillis()}.pdf")
        runCatching {
            PDDocument(PdfBoxMemory.setting()).use { doc ->
                val page = PDPage(A4)
                doc.addPage(page)
                val sides = listOfNotNull(front, back)
                val slots = idSlots(kind, sides.size)
                PDPageContentStream(doc, page).use { cs ->
                    sides.forEachIndexed { i, uri ->
                        val bmp = decode(uri, MAX_SIDE) ?: return@forEachIndexed
                        try {
                            // Card photos may come out portrait; turn them to the document's landscape shape.
                            val turned = bmp.height > bmp.width
                            val img = JPEGFactory.createFromImage(doc, bmp, JPEG_QUALITY)
                            val r = slots[i]
                            if (turned) {
                                cs.drawImage(img, Matrix(0f, -r.height, r.width, 0f, r.lowerLeftX, r.upperRightY))
                            } else {
                                cs.drawImage(img, r.lowerLeftX, r.lowerLeftY, r.width, r.height)
                            }
                            if (watermark.isNotBlank()) drawWatermark(cs, r, watermark)
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
                doc.save(out)
            }
            out.takeIf { it.length() > 0 }
        }.onFailure { AppLog.e("ImagePdf", "Building the ID copy failed", it) }.getOrNull()
            ?: run { out.delete(); null }
    }

    /** Diagonal, semi-transparent text repeated across [area]. */
    private fun drawWatermark(cs: PDPageContentStream, area: PDRectangle, text: String) {
        val clean = text.filter { it.code in 0x20..0x7E }.trim().ifBlank { return }
        cs.saveGraphicsState()
        cs.addRect(area.lowerLeftX, area.lowerLeftY, area.width, area.height)
        cs.clip()
        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.32f }
        cs.setGraphicsStateParameters(gs)
        cs.setNonStrokingColor(0.75f, 0.1f, 0.1f)
        val size = (area.height / 7f).coerceIn(8f, 20f)
        val font = PDType1Font.HELVETICA_BOLD
        val textWidth = font.getStringWidth(clean) / 1000f * size
        val stepY = size * 2.6f
        var y = area.lowerLeftY - area.width * 0.5f
        while (y < area.upperRightY + area.width * 0.5f) {
            var x = area.lowerLeftX - area.width
            while (x < area.upperRightX) {
                cs.beginText()
                cs.setFont(font, size)
                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(25.0), x, y))
                cs.showText(clean)
                cs.endText()
                x += textWidth + size * 2f
            }
            y += stepY
        }
        cs.restoreGraphicsState()
    }

    private fun decode(uri: Uri, maxSide: Int): Bitmap? =
        runCatching { decodeWith(uri, maxSide, preferImageDecoder = true) }.getOrNull()
            // Some sources ImageDecoder can't open (and test environments) still decode the classic way.
            ?: decodeWith(uri, maxSide, preferImageDecoder = false)

    private fun decodeWith(uri: Uri, maxSide: Int, preferImageDecoder: Boolean): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && preferImageDecoder) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val s = maxSide.toFloat() / longest
                    d.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.onFailure { AppLog.e("ImagePdf", "Reading a picture failed", it) }.getOrNull()

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    companion object {
        private val A4 = PDRectangle.A4
        private const val MAX_SIDE = 3000
        private const val JPEG_QUALITY = 0.88f
        private const val MM = 72f / 25.4f

        /** Where each side goes on A4: true size, centred, front above back. */
        fun idSlots(kind: IdKind, sides: Int): List<PDRectangle> {
            val w = kind.widthMm * MM
            val h = kind.heightMm * MM
            val x = (A4.width - w) / 2f
            val gap = 18f * MM
            return if (sides <= 1) {
                listOf(PDRectangle(x, A4.height - 60f * MM - h, w, h))
            } else {
                val total = h * 2 + gap
                val top = (A4.height + total) / 2f
                listOf(
                    PDRectangle(x, top - h, w, h),
                    PDRectangle(x, top - h - gap - h, w, h),
                ).map { r -> PDRectangle(r.lowerLeftX, max(0f, min(r.lowerLeftY, A4.height - h)), r.width, r.height) }
            }
        }

        private val initialized = AtomicBoolean(false)
    }
}
