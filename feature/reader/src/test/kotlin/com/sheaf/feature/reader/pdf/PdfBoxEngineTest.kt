package com.sheaf.feature.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sheaf.feature.reader.compress.PdfBoxCompressor
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
