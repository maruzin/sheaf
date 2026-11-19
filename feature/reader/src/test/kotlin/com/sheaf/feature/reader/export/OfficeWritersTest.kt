package com.sheaf.feature.reader.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class OfficeWritersTest {

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val parts = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                parts[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts
    }

    private fun assertWellFormed(xml: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }

    @Test
    fun `docx has the required parts, escaped text and a page break between pages`() {
        val out = ByteArrayOutputStream()
        OfficeWriters.writeDocx(listOf("Terms & <conditions>\u0001", "Page two"), "Lease \"A\"", out)
        val parts = unzip(out.toByteArray())
        assertTrue(parts.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "docProps/core.xml")))
        parts.values.forEach(::assertWellFormed)
        val doc = parts.getValue("word/document.xml")
        assertTrue(doc.contains("Terms &amp; &lt;conditions&gt;"))
        assertTrue(doc.contains("w:type=\"page\""))
        assertTrue(doc.contains("Page two"))
    }

    @Test
    fun `xlsx puts each line in a row and splits table columns`() {
        val out = ByteArrayOutputStream()
        OfficeWriters.writeXlsx(listOf("Item    Qty   Price\nBolts\t10\t2.50", "\n"), "Quote", out)
        val parts = unzip(out.toByteArray())
        parts.values.forEach(::assertWellFormed)
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<c r=\"D2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Price</t>"))
        assertTrue(sheet.contains("<c r=\"C3\" t=\"inlineStr\"><is><t xml:space=\"preserve\">10</t>"))
        // Header + 2 lines; the blank page adds nothing.
        assertEquals(3, Regex("<row ").findAll(sheet).count())
    }

    @Test
    fun `column names and file names`() {
        assertEquals("A", OfficeWriters.columnName(0))
        assertEquals("Z", OfficeWriters.columnName(25))
        assertEquals("AA", OfficeWriters.columnName(26))
        assertEquals("AZ", OfficeWriters.columnName(51))
        assertEquals("a_b_c.pdf", DocumentExporter.safeName("a/b:c.pdf"))
        assertEquals("Lease", DocumentExporter.baseName("Lease.pdf"))
        assertEquals("Document", DocumentExporter.baseName(""))
    }
}
