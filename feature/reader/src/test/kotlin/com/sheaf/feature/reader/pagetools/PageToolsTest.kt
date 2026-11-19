package com.sheaf.feature.reader.pagetools

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageToolsTest {
    private lateinit var context: Context
    private lateinit var tools: PageTools

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        tools = PageTools(context, Dispatchers.Unconfined)
    }

    private fun pdf(name: String, pages: Int, rotation: Int = 0): String {
        val f = File(context.cacheDir, name)
        PDDocument().use { d ->
            repeat(pages) { i ->
                val p = PDPage(PDRectangle.A4).apply { this.rotation = rotation }; d.addPage(p)
                PDPageContentStream(d, p).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 700f)
                    cs.showText("Body of page ${i + 1}"); cs.endText()
                }
            }
            d.save(f)
        }
        return Uri.fromFile(f).toString()
    }

    private fun load(path: String) = PDDocument.load(File(path))
    private fun pageText(d: PDDocument, page: Int) =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(d)

    @Test fun `page numbers go on every page, starting where asked`() = runTest {
        val out = tools.addPageNumbers(pdf("n.pdf", 3), NumberPosition.BottomCenter, startAt = 5, withTotal = true)!!
        load(out).use { d ->
            assertEquals(3, d.numberOfPages)
            assertTrue(pageText(d, 1).contains("Page 5 of 7"))
            assertTrue(pageText(d, 3).contains("Page 7 of 7"))
            assertTrue("the original text is kept", pageText(d, 2).contains("Body of page 2"))
        }
    }

    @Test fun `page numbers work on turned pages`() = runTest {
        val out = tools.addPageNumbers(pdf("r.pdf", 2, rotation = 90), NumberPosition.BottomRight)!!
        load(out).use { d -> assertTrue(pageText(d, 2).contains("2")) }
    }

    @Test fun `watermark text is on every page and blank text is refused`() = runTest {
        val out = tools.addWatermark(pdf("w.pdf", 2), "CONFIDENTIAL")!!
        load(out).use { d -> assertTrue(pageText(d, 1).contains("CONFIDENTIAL")); assertTrue(pageText(d, 2).contains("CONFIDENTIAL")) }
        assertNull(tools.addWatermark(pdf("w2.pdf", 1), "   "))
    }

    @Test fun `crop trims every side`() = runTest {
        val out = tools.cropMargins(pdf("c.pdf", 1), 0.1f)!!
        load(out).use { d ->
            val box = d.getPage(0).cropBox
            assertEquals(PDRectangle.A4.width * 0.8f, box.width, 0.5f)
            assertEquals(PDRectangle.A4.height * 0.8f, box.height, 0.5f)
        }
    }

    @Test fun `a blank page goes exactly where asked`() = runTest {
        val out = tools.insertBlank(pdf("b.pdf", 3), afterIndex = 0)!!
        load(out).use { d ->
            assertEquals(4, d.numberOfPages)
            assertTrue(pageText(d, 1).contains("Body of page 1"))
            assertTrue("page 2 is the new blank one", pageText(d, 2).isBlank())
            assertTrue(pageText(d, 3).contains("Body of page 2"))
        }
        load(tools.insertBlank(pdf("b2.pdf", 2), afterIndex = 1)!!).use { d -> assertTrue(pageText(d, 3).isBlank()) }
    }

    @Test fun `another PDF is inserted after the chosen page`() = runTest {
        val other = File(context.cacheDir, "other.pdf")
        PDDocument().use { d ->
            val p = PDPage(PDRectangle.A4); d.addPage(p)
            PDPageContentStream(d, p).use { cs -> cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 700f); cs.showText("Inserted"); cs.endText() }
            d.save(other)
        }
        val out = tools.insertPdf(pdf("i.pdf", 2), Uri.fromFile(other).toString(), afterIndex = 0)!!
        load(out).use { d ->
            assertEquals(3, d.numberOfPages)
            assertTrue(pageText(d, 2).contains("Inserted"))
            assertTrue(pageText(d, 3).contains("Body of page 2"))
        }
    }

    @Test fun `split makes two parts and splitting after the last page is refused`() = runTest {
        val src = pdf("s.pdf", 4)
        val (a, b) = tools.splitAfter(src, afterIndex = 0)!!
        load(a).use { assertEquals(1, it.numberOfPages) }
        load(b).use { d -> assertEquals(3, d.numberOfPages); assertTrue(pageText(d, 1).contains("Body of page 2")) }
        assertNull(tools.splitAfter(src, afterIndex = 3))
        assertNotNull(tools.splitAfter(src, afterIndex = 2))
    }
}
