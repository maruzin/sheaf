package com.sheaf.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflowLayoutTest {

    private val layout = ReflowLayout(
        listOf(
            listOf("p0a", "p0b"),
            emptyList(),
            listOf("p2a"),
        ),
    )

    @Test
    fun `one header per page followed by its paragraphs`() {
        assertEquals(6, layout.rows.size)
        assertEquals(ReflowLayout.Row.Header(0), layout.rows[0])
        assertEquals(ReflowLayout.Row.Header(1), layout.rows[3])
    }

    @Test
    fun `maps pages to rows and back`() {
        assertEquals(0, layout.rowForPage(0))
        assertEquals(3, layout.rowForPage(1))
        assertEquals(4, layout.rowForPage(2))
        assertEquals(0, layout.pageForRow(2))
        assertEquals(2, layout.pageForRow(5))
        assertEquals(0, layout.rowForPage(99)) // out of range falls back to the top
    }
}
