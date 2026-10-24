package com.sheaf.feature.reader.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    private val pages = listOf(
        "Introduction\nSheaf is a PDF reader.",
        "Nothing to see here.",
        "The quick brown\nfox jumps over the lazy dog. Sheaf again.",
    )

    @Test
    fun `finds one hit per matching page, case-insensitively`() {
        val hits = TextSearch.find(pages, "SHEAF")
        assertEquals(listOf(0, 2), hits.map { it.pageIndex })
    }

    @Test
    fun `matches a phrase split across a line break`() {
        val hits = TextSearch.find(pages, "brown fox")
        assertEquals(listOf(2), hits.map { it.pageIndex })
    }

    @Test
    fun `blank query and no match return nothing`() {
        assertTrue(TextSearch.find(pages, "   ").isEmpty())
        assertTrue(TextSearch.find(pages, "zebra").isEmpty())
        assertTrue(TextSearch.find(emptyList(), "sheaf").isEmpty())
    }

    @Test
    fun `snippet shows context with ellipses when trimmed`() {
        val long = "a".repeat(100) + " needle " + "b".repeat(100)
        val hit = TextSearch.find(listOf(long), "needle").single()
        assertTrue(hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
        assertTrue("needle" in hit.snippet)
    }

    @Test
    fun `paragraphs joins wrapped lines and repairs hyphenation`() {
        val text = """
            This is a long line of body text that wraps onto
            the next line and continues with a hyphen-
            ated word in the middle of the sentence here ok.

            Second paragraph.
        """.trimIndent()
        val paras = TextSearch.paragraphs(text)
        assertEquals(2, paras.size)
        assertTrue(paras[0].contains("wraps onto the next line"))
        assertTrue(paras[0].contains("hyphenated word"))
        assertEquals("Second paragraph.", paras[1])
    }

    @Test
    fun `paragraphs of an empty page is empty`() {
        assertTrue(TextSearch.paragraphs("").isEmpty())
        assertTrue(TextSearch.paragraphs("\n\n  \n").isEmpty())
    }

    @Test
    fun `keeps a real hyphen before a capitalised word`() {
        val paras = TextSearch.paragraphs("A line that ends with Jean-\nPaul Sartre and continues on.")
        assertTrue(paras.single().contains("Jean-Paul Sartre"))
    }
}
