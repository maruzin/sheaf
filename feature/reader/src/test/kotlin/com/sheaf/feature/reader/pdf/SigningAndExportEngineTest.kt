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

    @Test
    fun `sweep deletes unused signature images but keeps saved, placed and brand-new ones`() = runTest {
        val dir = File(context.noBackupFilesDir, "signatures").apply { mkdirs() }
        val old = System.currentTimeMillis() - 60 * 60_000L
        fun sig(name: String, modified: Long) = File(dir, name).apply { writeText("png"); setLastModified(modified) }
        val kept = sig("sig_1_1.png", old)
        val unused = sig("sig_2_2.png", old)
        val fresh = sig("sig_3_3.png", System.currentTimeMillis())
        val other = File(dir, "notes.txt").apply { writeText("x"); setLastModified(old) }

        val deleted = store.sweep(keep = setOf("sig_1_1.png"))

        assertEquals(1, deleted)
        assertTrue(kept.exists())
        assertTrue("unused image deleted", !unused.exists())
        assertTrue("a signature saved moments ago is left alone", fresh.exists())
        assertTrue("files Sheaf didn't name are never touched", other.exists())
        // Removing a signature right now deletes it regardless of age.
        store.sweep(keep = setOf("sig_1_1.png"), now = Long.MAX_VALUE)
        assertTrue(!fresh.exists())
    }

    @Test
    fun `stamps render as ink on a transparent background`() = runTest {
        val tick = store.saveStamp(SignatureStore.Stamp.Tick)!!
        val tickBmp = store.load(tick)!!
        assertEquals(tickBmp.width, tickBmp.height)
        val px = IntArray(tickBmp.width * tickBmp.height).also { tickBmp.getPixels(it, 0, tickBmp.width, 0, 0, tickBmp.width, tickBmp.height) }
        assertTrue("tick has ink", px.count { (it ushr 24) > 200 } > 500)
        assertTrue("background is transparent", (px[0] ushr 24) == 0)

        val date = store.saveStamp(SignatureStore.Stamp.Date, "19/11/2026")!!
        val dateBmp = store.load(date)!!
        assertTrue("text stamp is wide", dateBmp.width > dateBmp.height * 3)

        assertEquals(null, store.saveStamp(SignatureStore.Stamp.Text, "   "))
    }

    @Test
    fun `date stamps follow the phone's day and month order with a 4-digit year`() {
        val d = java.time.LocalDate.of(2026, 11, 9)
        assertEquals("09/11/2026", com.sheaf.feature.reader.signature.FormDates.format(d, java.util.Locale("en", "IE")))
        assertEquals("11/09/2026", com.sheaf.feature.reader.signature.FormDates.format(d, java.util.Locale.US))
        assertEquals("09.11.2026", com.sheaf.feature.reader.signature.FormDates.format(d, java.util.Locale.GERMANY))
    }

    @Test
    fun `tick and date stamps are drawn into the exported PDF`() = runTest {
        val tick = store.saveStamp(SignatureStore.Stamp.Tick)!!
        val date = store.saveStamp(SignatureStore.Stamp.Date, "09/11/2026")!!
        val source = pdf()
        val a = SignaturePlacement.new(tick, NormPoint(0.2f, 0.3f), store.aspect(tick), 1.4142f, SignaturePlacement.STAMP_MARK_HEIGHT)
        val b = SignaturePlacement.new(date, NormPoint(0.6f, 0.8f), store.aspect(date), 1.4142f, SignaturePlacement.STAMP_TEXT_HEIGHT)
        val flattener = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store)
        val out = flattener.flatten(
            Uri.fromFile(source).toString(),
            mapOf(0 to listOf(a.toAnnotation(1, 0, id = 1), b.toAnnotation(1, 0, id = 2))),
        )
        assertNotNull(out)
        PDDocument.load(File(out!!)).use { d ->
            val page = d.getPage(0)
            val images = page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>()
            assertEquals("both stamps are in the page", 2, images.size)
            assertTrue(images.all { it.cosObject.getDictionaryObject(COSName.SMASK) != null })
        }
    }

    @Test
    fun `an unlocked copy of a protected PDF carries no encryption`() = runTest {
        val src = File(context.cacheDir, "locked.pdf")
        PDDocument().use { d ->
            d.addPage(PDPage(PDRectangle.A4))
            d.protect(
                com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                    "secret1", "secret1", com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission(),
                ).apply { encryptionKeyLength = 128 },
            )
            d.save(src)
        }
        val security = com.sheaf.feature.reader.security.PdfBoxSecurity(context, Dispatchers.Unconfined)
        val out = security.decrypt(Uri.fromFile(src).toString(), "secret1")
        assertNotNull(out)
        val bytes = File(out!!).readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        File("build/office-check").mkdirs(); File(out).copyTo(File("build/office-check/unlocked.pdf"), overwrite = true)
        src.copyTo(File("build/office-check/locked.pdf"), overwrite = true)
        assertTrue("no /Encrypt left in the unlocked copy", !text.contains("/Encrypt"))
        PDDocument.load(File(out)).use { assertTrue(!it.isEncrypted) }
    }

    private fun textPdf(name: String, text: String): File {
        val f = File(context.cacheDir, name)
        PDDocument().use { d ->
            val page = PDPage(PDRectangle.A4); d.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(d, page).use { cs ->
                cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(72f, 700f); cs.showText(text); cs.endText()
            }
            d.save(f)
        }
        return f
    }

    @Test
    fun `shapes are drawn as vectors and white-out as a filled box`() = runTest {
        val src = textPdf("shapes.pdf", "Account number 12345678")
        val anns = listOf(
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.Arrow, NormPoint(0.1f, 0.5f), NormPoint(0.6f, 0.5f), 0xFFC5221F.toInt(), 0.004f),
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.Ellipse, NormPoint(0.2f, 0.6f), NormPoint(0.5f, 0.7f), 0xFF1F3FA8.toInt(), 0.004f),
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.WhiteOut, NormPoint(0.1f, 0.14f), NormPoint(0.6f, 0.19f), 0, 0f),
        ).mapIndexed { i, s -> s.toAnnotation(1, 0, id = i + 1L) }
        val out = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store).flatten(Uri.fromFile(src).toString(), mapOf(0 to anns))
        assertNotNull(out)
        PDDocument.load(File(out!!)).use { d ->
            val ops = operators(d.getPage(0))
            assertTrue("strokes", ops.count { it == "S" } >= 4) // arrow line + 2 head strokes + ellipse
            assertTrue("bezier ellipse", ops.contains("c"))
            assertTrue("white-out fill", ops.contains("f"))
            // White-out only covers: the text is still in the file (that is what Redact is for).
            assertTrue(com.tom_roush.pdfbox.text.PDFTextStripper().getText(d).contains("12345678"))
        }
    }

    @Test
    fun `redaction removes the text under the box for good`() = runTest {
        val src = textPdf("redact.pdf", "Account number 12345678")
        val box = com.sheaf.feature.reader.markup.ShapeSpec(
            com.sheaf.feature.reader.markup.ShapeKind.Redact, NormPoint(0.05f, 0.1f), NormPoint(0.9f, 0.2f), 0, 0f,
        ).toAnnotation(1, 0, id = 1)
        val out = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store).flatten(Uri.fromFile(src).toString(), mapOf(0 to listOf(box)))
        assertNotNull(out)
        PDDocument.load(File(out!!)).use { d ->
            assertEquals(1, d.numberOfPages)
            val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(d)
            assertTrue("no extractable text left: '$text'", !text.contains("12345678") && !text.contains("Account"))
            val page = d.getPage(0)
            val images = page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>()
            assertEquals("the page is now one picture", 1, images.size)
            assertEquals(595.27563f, page.mediaBox.width, 0.5f)
        }
    }

    @Test
    fun `a text box keeps its words and style for editing`() = runTest {
        val box = SignatureStore.TextBox("Seán Ó Briain\nApt 4 | Dublin", size = 2, colorArgb = 0xFF1F3FA8.toInt())
        val name = store.saveTextBox(box)!!
        assertEquals(box, store.textBox(name))
        val bmp = store.load(name)!!
        assertTrue("two lines make a taller image than one", bmp.height > SignatureStore.TextBox.SIZES[2] * 1.8f)
        assertEquals(null, store.saveTextBox(box.copy(text = "  \n ")))
        // Removing it deletes the image and its words.
        store.sweep(keep = emptySet(), now = Long.MAX_VALUE)
        assertEquals(null, store.textBox(name))
    }

    @Test
    fun `a placed photo is embedded as compact JPEG, ink keeps transparency`() = runTest {
        val photo = File(context.cacheDir, "photo.jpg")
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 200)) }
        photo.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val name = store.importPicture(Uri.fromFile(photo))!!
        val placement = SignaturePlacement.new(name, NormPoint(0.5f, 0.5f), store.aspect(name), 1.4142f, 0.5f / store.aspect(name))
        val out = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store)
            .flatten(Uri.fromFile(pdf()).toString(), mapOf(0 to listOf(placement.toAnnotation(1, 0, id = 1))))
        PDDocument.load(File(out!!)).use { d ->
            val page = d.getPage(0)
            val img = page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
            assertEquals("jpg", img.suffix)
            assertEquals(null, img.cosObject.getDictionaryObject(COSName.SMASK))
        }
    }

    @Test
    fun `boxes drawn as form fields become a fillable form`() = runTest {
        val anns = listOf(
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.FormText, NormPoint(0.2f, 0.2f), NormPoint(0.7f, 0.24f), 0, 0f),
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.FormText, NormPoint(0.2f, 0.3f), NormPoint(0.7f, 0.34f), 0, 0f),
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.FormCheckbox, NormPoint(0.2f, 0.4f), NormPoint(0.24f, 0.43f), 0, 0f),
        ).mapIndexed { i, s -> s.toAnnotation(1, 0, id = i + 1L) }
        val made = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store).flatten(Uri.fromFile(pdf()).toString(), mapOf(0 to anns))!!
        val reader = com.sheaf.feature.reader.forms.PdfBoxFormReader(context, Dispatchers.Unconfined)
        val fields = reader.readFields(Uri.fromFile(File(made)).toString())
        assertEquals(listOf("Checkbox 1", "Text 1", "Text 2"), fields.map { it.name }.sorted())
        // The new form fills like any other form.
        val filled = reader.fillAndSave(Uri.fromFile(File(made)).toString(), mapOf("Text 1" to "Dean", "Checkbox 1" to "true"))!!
        PDDocument.load(File(filled)).use { d ->
            val acro = d.documentCatalog.acroForm
            if (acro != null && acro.fields.isNotEmpty()) {
                assertEquals("Dean", acro.getField("Text 1").valueAsString)
                assertTrue((acro.getField("Checkbox 1") as com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox).isChecked)
            } else {
                assertTrue(com.tom_roush.pdfbox.text.PDFTextStripper().getText(d).contains("Dean"))
            }
        }
    }

    @Test
    fun `a form field survives a redaction on the same page`() = runTest {
        val src = textPdf("redact-form.pdf", "Secret 999")
        val anns = listOf(
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.Redact, NormPoint(0.05f, 0.1f), NormPoint(0.9f, 0.2f), 0, 0f),
            com.sheaf.feature.reader.markup.ShapeSpec(com.sheaf.feature.reader.markup.ShapeKind.FormText, NormPoint(0.2f, 0.5f), NormPoint(0.7f, 0.54f), 0, 0f),
        ).mapIndexed { i, s -> s.toAnnotation(1, 0, id = i + 1L) }
        val out = PdfBoxAnnotationFlattener(context, Dispatchers.Unconfined, store).flatten(Uri.fromFile(src).toString(), mapOf(0 to anns))!!
        PDDocument.load(File(out)).use { d ->
            assertTrue(!com.tom_roush.pdfbox.text.PDFTextStripper().getText(d).contains("999"))
            val field = d.documentCatalog.acroForm.getField("Text 1")
            assertNotNull(field)
            // Its widget is on the page that is actually in the document.
            assertTrue(d.getPage(0).annotations.any { it.cosObject == field.widgets.first().cosObject })
        }
    }
}
