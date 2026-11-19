package com.sheaf.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.feature.reader.export.DocumentExporter
import com.sheaf.feature.reader.export.ExportFormat
import com.sheaf.feature.reader.export.IdKind
import com.sheaf.feature.reader.export.ImagePdfBuilder
import com.sheaf.feature.reader.flatten.PdfBoxAnnotationFlattener
import com.sheaf.feature.reader.signature.SignaturePlacement
import com.sheaf.feature.reader.signature.SignatureStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile

/** Signing, ID copies and picture → PDF against real PdfBox output (Robolectric, native graphics). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SigningAndExportEngineTest {

    private lateinit var context: Context
    private lateinit var store: SignatureStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        store = SignatureStore(context, Dispatchers.Unconfined)
    }

    private fun pdf(rotation: Int = 0): File {
        val f = File(context.cacheDir, "doc_$rotation.pdf")
        PDDocument().use { d ->
            d.addPage(PDPage(PDRectangle.A4).apply { this.rotation = rotation })
            d.save(f)
        }
        return f
    }

    private fun picture(name: String, w: Int, h: Int): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            drawRect(w * 0.2f, h * 0.45f, w * 0.8f, h * 0.55f, Paint().apply { color = Color.BLACK })
        }
        val f = File(context.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    private fun operators(page: PDPage): List<String> {
        val out = ArrayList<String>()
        page.contentStreams.forEach { stream ->
            val parser = PDFStreamParser(stream)
            parser.parse()
            parser.tokens.filterIsInstance<Operator>().forEach { out += it.name }
        }
        return out
    }

    @Test
    fun `a drawn signature with several strokes is saved and stamped into the shared PDF`() = runTest {
        val name = store.saveDrawn(
            strokes = listOf(
                listOf(NormPoint(0.1f, 0.6f), NormPoint(0.4f, 0.4f), NormPoint(0.5f, 0.7f)),
                listOf(NormPoint(0.6f, 0.5f), NormPoint(0.9f, 0.5f)),
            ),
            padW = 960f, padH = 400f, lineWidthPx = 8f, colorArgb = 0xFF15171C.toInt(),
        )
        assertNotNull(name)
        val bmp = store.load(name!!)!!
        assertTrue(bmp.hasAlpha())
        // Transparent paper, opaque ink.
        assertEquals(0, Color.alpha(bmp.getPixel(0, 0)))

        val source = pdf(rotation = 90)
        val placement = SignaturePlacement(name, 0.5f, 0.8f, 0.3f, 0.1f, rotation = 12f)
        val flattener = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store)
        val out = flattener.flatten(Uri.fromFile(source).toString(), mapOf(0 to listOf(placement.toAnnotation(1, 0, id = 1))))
        assertNotNull(out)
        PDDocument.load(File(out!!)).use { d ->
            val page = d.getPage(0)
            val images = page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>()
            assertEquals(1, images.size)
            // Transparency travels as a soft mask.
            assertNotNull(images[0].cosObject.getDictionaryObject(COSName.SMASK))
            val ops = operators(page)
            assertTrue(ops.containsAll(listOf("cm", "Do")))
        }
    }

    @Test
    fun `a photo of a signature becomes ink on a transparent background`() = runTest {
        val r = store.import(picture("sig_photo.png", 600, 300))
        assertTrue(r is SignatureStore.ImportResult.Ok)
        val bmp = store.load((r as SignatureStore.ImportResult.Ok).name)!!
        // Cropped close to the bar (360 x 30 px) instead of the whole 600 x 300 photo.
        assertTrue("width ${bmp.width}", bmp.width in 360..420)
        assertTrue("height ${bmp.height}", bmp.height in 30..70) // 30 px bar + 4% margin of the long side
        assertEquals(0, Color.alpha(bmp.getPixel(0, 0)))
        assertEquals(255, Color.alpha(bmp.getPixel(bmp.width / 2, bmp.height / 2)))
        // Blank paper is refused with a clear reason.
        val blank = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val f = File(context.cacheDir, "blank.png").apply { outputStream().use { blank.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        assertEquals(SignatureStore.ImportResult.NoInk, store.import(Uri.fromFile(f)))
    }

    @Test
    fun `pictures become one page each with their own shape`() = runTest {
        val builder = ImagePdfBuilder(context, Dispatchers.Unconfined)
        val out = builder.imagesToPdf(listOf(picture("a.png", 400, 600), picture("b.png", 800, 400)))
        assertNotNull(out)
        PDDocument.load(out!!).use { d ->
            assertEquals(2, d.numberOfPages)
            val p0 = d.getPage(0).mediaBox
            assertEquals(400f / 600f, p0.width / p0.height, 0.01f)
            assertEquals(842f, p0.height, 1f)
            val p1 = d.getPage(1).mediaBox
            assertEquals(2f, p1.width / p1.height, 0.01f)
        }
    }

    @Test
    fun `an ID copy puts both sides on one A4 page at real size, with a watermark`() = runTest {
        val builder = ImagePdfBuilder(context, Dispatchers.Unconfined)
        val out = builder.idCopy(picture("front.png", 856, 540), picture("back.png", 856, 540), IdKind.IdCard, "Copy for bank only")
        assertNotNull(out)
        PDDocument.load(out!!).use { d ->
            assertEquals(1, d.numberOfPages)
            val page = d.getPage(0)
            assertEquals(PDRectangle.A4.width, page.mediaBox.width, 0.5f)
            val images = page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>()
            assertEquals(2, images.size)
            assertTrue(operators(page).contains("Tj")) // watermark text
        }
        // 85.6 mm card = 242.6 pt wide, centred.
        val slots = ImagePdfBuilder.idSlots(IdKind.IdCard, 2)
        assertEquals(242.65f, slots[0].width, 0.1f)
        assertTrue(slots[0].lowerLeftY > slots[1].upperRightY)
    }

    @Test
    fun `text exports are valid Word and Excel packages`() = runTest {
        val exporter = DocumentExporter(context, Dispatchers.Unconfined, mockk(relaxed = true))
        val docx = exporter.textFile(ExportFormat.Word, listOf("Hello", "World"), "Report")!!
        ZipFile(docx).use { z -> assertNotNull(z.getEntry("word/document.xml")) }
        val xlsx = exporter.textFile(ExportFormat.Excel, listOf("A  B"), "Report")!!
        ZipFile(xlsx).use { z -> assertNotNull(z.getEntry("xl/worksheets/sheet1.xml")) }
        val txt = exporter.textFile(ExportFormat.Txt, listOf("one", "two"), "Report")!!
        assertTrue(txt.readText().contains("--- Page 2 ---\ntwo"))
    }
}
