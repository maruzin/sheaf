package com.sheaf.feature.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sheaf.feature.reader.compress.PdfBoxCompressor
import com.sheaf.feature.reader.merge.MergeResult
import com.sheaf.feature.reader.merge.PdfBoxMerger
import com.sheaf.feature.reader.pages.PageOp
import com.sheaf.feature.reader.pages.PdfBoxPageEditor
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.sheaf.feature.reader.search.PdfBoxOutlineExtractor
import com.sheaf.feature.reader.search.PdfBoxTextSearcher
import com.sheaf.feature.reader.search.TextSearch
import com.sheaf.feature.reader.security.PdfBoxSecurity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Runs the real PdfBox-backed engines against real PDF files (Robolectric provides the Context). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfBoxEngineTest {

    private lateinit var context: Context
    private lateinit var sample: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        sample = File(context.cacheDir, "sample.pdf")
        writeSamplePdf(sample)
    }

    private fun uriOf(file: File) = Uri.fromFile(file).toString()

    @Test
    fun `extracts the text of every page`() = runTest {
        val pages = PdfBoxTextSearcher(context, Dispatchers.IO).pageTexts(uriOf(sample))
        assertEquals(3, pages.size)
        assertTrue(pages[0].contains("Welcome to Sheaf"))
        assertTrue(pages[2].contains("zebracorn"))
        assertEquals(listOf(2), TextSearch.find(pages, "ZEBRACORN").map { it.pageIndex })
    }

    @Test
    fun `reads the outline with zero-based page targets`() = runTest {
        val outline = PdfBoxOutlineExtractor(context, Dispatchers.IO).outline(uriOf(sample))
        assertEquals(listOf("Chapter 2"), outline.map { it.title })
        assertEquals(1, outline.single().pageIndex)
    }

    @Test
    fun `encrypt then decrypt round-trips and wrong passwords leave nothing behind`() = runTest {
        val security = PdfBoxSecurity(context, Dispatchers.IO)
        val protectedPath = security.encrypt(uriOf(sample), "s3cret")
        assertNotNull(protectedPath)
        val protectedFile = File(protectedPath!!)

        // The protected copy really needs the password.
        try {
            PDDocument.load(protectedFile).close()
            fail("expected the protected copy to require a password")
        } catch (expected: InvalidPasswordException) {
            // ok
        }

        val unlockedDir = File(context.cacheDir, "unlocked")
        assertNull(security.decrypt(uriOf(protectedFile), "wrong"))
        assertTrue(unlockedDir.listFiles().orEmpty().isEmpty())

        val unlocked = security.decrypt(uriOf(protectedFile), "s3cret")
        assertNotNull(unlocked)
        val pages = PdfBoxTextSearcher(context, Dispatchers.IO).pageTexts(uriOf(File(unlocked!!)))
        assertTrue(pages[2].contains("zebracorn"))

        security.purgeUnlockedCopies()
        assertFalse(File(unlocked).exists())
    }

    @Test
    fun `engines report failure instead of a phantom file when the source is unreadable`() = runTest {
        val missing = uriOf(File(context.cacheDir, "does-not-exist.pdf"))
        assertNull(PdfBoxSecurity(context, Dispatchers.IO).encrypt(missing, "pw"))
        assertNull(PdfBoxSecurity(context, Dispatchers.IO).decrypt(missing, "pw"))
        assertNull(PdfBoxCompressor(context, Dispatchers.IO).compress(missing))
    }

    @Test
    fun `merge joins every page in order and keeps the text`() = runTest {
        val second = File(context.cacheDir, "second.pdf").also { writeSamplePdf(it) }
        val result = PdfBoxMerger(context, Dispatchers.IO).merge(listOf(uriOf(sample), uriOf(second)))
        assertTrue(result is MergeResult.Success)
        result as MergeResult.Success
        assertEquals(6, result.pageCount)
        val pages = PdfBoxTextSearcher(context, Dispatchers.IO).pageTexts(uriOf(File(result.path)))
        assertEquals(6, pages.size)
        assertTrue(pages[0].contains("Welcome to Sheaf"))
        assertTrue(pages[5].contains("zebracorn"))
    }

    @Test
    fun `merge names the protected or unreadable input and writes nothing`() = runTest {
        val merger = PdfBoxMerger(context, Dispatchers.IO)
        val locked = PdfBoxSecurity(context, Dispatchers.IO).encrypt(uriOf(sample), "pw")!!
        val editedDir = File(context.filesDir, "edited")
        val before = editedDir.listFiles().orEmpty().size

        assertEquals(MergeResult.Protected(1), merger.merge(listOf(uriOf(sample), uriOf(File(locked)))))
        val missing = uriOf(File(context.cacheDir, "nope.pdf"))
        assertEquals(MergeResult.Unreadable(0), merger.merge(listOf(missing, uriOf(sample))))
        assertEquals(MergeResult.Failed, merger.merge(listOf(uriOf(sample))))
        assertEquals(before, editedDir.listFiles().orEmpty().size)
    }

    @Test
    fun `a deleted page is really gone even when a bookmark, link and form field point at it`() = runTest {
        val src = File(context.cacheDir, "linked.pdf").also { writeLinkedPdf(it) }
        // Keep pages 0 and 2, delete page 1 (the one with "Second page body text").
        val path = PdfBoxPageEditor(context, Dispatchers.IO).save(uriOf(src), listOf(PageOp(0, 0), PageOp(2, 90)))
        assertNotNull(path)
        PDDocument.load(File(path!!)).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(90, doc.getPage(1).rotation)
            // The bookmark and the link both targeted the deleted page: neither survives.
            assertNull(doc.documentCatalog.documentOutline)
            assertTrue(doc.getPage(0).annotations.none { it is PDAnnotationLink })
            // No object anywhere in the file still holds the deleted page's text.
            val leaked = doc.document.objects.any { obj ->
                val stream = obj.`object` as? COSStream ?: return@any false
                runCatching { stream.createInputStream().use { it.readBytes() } }.getOrNull()
                    ?.toString(Charsets.ISO_8859_1)?.contains("Second page body text") == true
            }
            assertFalse("deleted page content leaked into the edited copy", leaked)
        }
    }

    @Test
    fun `bookmarks and links to kept pages follow them to their new position`() = runTest {
        val src = File(context.cacheDir, "linked2.pdf").also { writeLinkedPdf(it) }
        // Page 1 moves to the front; page 0 is deleted.
        val path = PdfBoxPageEditor(context, Dispatchers.IO).save(uriOf(src), listOf(PageOp(1, 0), PageOp(2, 0)))
        assertNotNull(path)
        val outline = PdfBoxOutlineExtractor(context, Dispatchers.IO).outline(uriOf(File(path!!)))
        assertEquals(listOf("Chapter 2" to 0), outline.map { it.title to it.pageIndex })
    }

    /** Sample PDF plus: a link on page 0 to page 1, and a form field on page 1. */
    private fun writeLinkedPdf(out: File) {
        writeSamplePdf(out)
        PDDocument.load(out).use { doc ->
            val target = doc.getPage(1)
            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(72f, 600f, 200f, 20f)
                destination = PDPageFitDestination().apply { page = target }
            }
            doc.getPage(0).annotations.add(link)
            val form = PDAcroForm(doc)
            doc.documentCatalog.acroForm = form
            val field = PDTextField(form).apply { partialName = "secret" }
            val widget = field.widgets.first().apply {
                rectangle = PDRectangle(72f, 500f, 200f, 20f)
                page = target
            }
            target.annotations.add(widget)
            form.fields.add(field)
            val second = File(out.parentFile, out.name + ".tmp")
            doc.save(second)
            second.renameTo(out)
        }
    }

    @Test
    fun `blank password is refused`() = runTest {
        assertNull(PdfBoxSecurity(context, Dispatchers.IO).encrypt(uriOf(sample), "  "))
    }

    private fun writeSamplePdf(out: File) {
        PDDocument().use { doc ->
            val lines = listOf("Welcome to Sheaf", "Second page body text", "The secret word is zebracorn")
            lines.forEach { text ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText(text)
                    cs.endText()
                }
            }
            val outline = PDDocumentOutline()
            outline.addLast(PDOutlineItem().apply { title = "Chapter 2"; setDestination(doc.getPage(1)) })
            doc.documentCatalog.documentOutline = outline
            doc.save(out)
        }
    }
}
