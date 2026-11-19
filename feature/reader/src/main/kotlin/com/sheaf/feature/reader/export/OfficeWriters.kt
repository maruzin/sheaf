package com.sheaf.feature.reader.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, standards-conformant Word (.docx) and Excel (.xlsx) writers for exported text.
 *
 * Both formats are ZIP packages of XML parts (ECMA-376). Exports carry the document's text, one
 * paragraph (Word) or row (Excel) per line, with a page break / page column between PDF pages.
 * Layout, fonts and pictures are not reproduced: the goal is editable text, not a facsimile.
 * Apache POI would do the same at ~10 MB of dex; these few parts are all that a valid file needs.
 */
object OfficeWriters {

    fun writeDocx(pages: List<String>, title: String, out: OutputStream) {
        val body = StringBuilder()
        pages.forEachIndexed { index, text ->
            val lines = text.replace("\r\n", "\n").split('\n')
            lines.forEach { line ->
                body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r></w:p>")
            }
            if (index < pages.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        ZipOutputStream(out).use { zip ->
            zip.part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>""")
            zip.part("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""")
            zip.part("docProps/core.xml", coreProps(title))
            zip.part("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="709" w:footer="709" w:gutter="0"/></w:sectPr></w:body></w:document>""")
        }
    }

    /**
     * One worksheet: column A = page number, then the line split into cells wherever the text has a
     * tab or a run of 2+ spaces (how table columns usually come out of a PDF).
     */
    fun writeXlsx(pages: List<String>, title: String, out: OutputStream) {
        val rows = StringBuilder()
        var r = 1
        rows.append(row(r++, listOf("Page", "Text")))
        pages.forEachIndexed { index, text ->
            text.replace("\r\n", "\n").split('\n').filter { it.isNotBlank() }.forEach { line ->
                rows.append(row(r++, listOf((index + 1).toString()) + splitColumns(line)))
            }
        }
        ZipOutputStream(out).use { zip ->
            zip.part("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>""")
            zip.part("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""")
            zip.part("docProps/core.xml", coreProps(title))
            zip.part("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Text" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.part("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            zip.part("xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>""")
        }
    }

    fun splitColumns(line: String): List<String> =
        line.trim().split(Regex("\\t| {2,}")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Excel column letters: 0 → A, 25 → Z, 26 → AA. */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun row(r: Int, cells: List<String>): String = buildString {
        append("<row r=\"").append(r).append("\">")
        cells.forEachIndexed { c, v ->
            val ref = columnName(c) + r
            if (c == 0 && v.isNotEmpty() && v.all { it.isDigit() }) {
                // The page number is a real number, so it sorts and filters as one.
                append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>")
            } else {
                append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                append(xml(cellText(v))).append("</t></is></c>")
            }
        }
        append("</row>")
    }

    /** Excel refuses (and "repairs") cells over 32,767 characters. */
    const val MAX_CELL_CHARS = 32_767

    fun cellText(v: String): String = if (v.length <= MAX_CELL_CHARS) v else v.take(MAX_CELL_CHARS - 1) + "…"

    private fun coreProps(title: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${xml(title)}</dc:title><dc:creator>Sheaf</dc:creator></cp:coreProperties>"""

    /** XML-escapes text and drops characters XML 1.0 forbids (PDF text often carries control codes). */
    fun xml(s: String): String = buildString(s.length) {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' -> append(' ')
                ch.code < 0x20 -> Unit
                ch in '￾'..'￿' -> Unit
                else -> append(ch)
            }
        }
    }

    private fun ZipOutputStream.part(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
