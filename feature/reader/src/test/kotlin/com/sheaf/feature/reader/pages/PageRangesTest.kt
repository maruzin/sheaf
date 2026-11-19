package com.sheaf.feature.reader.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRangesTest {
    @Test
    fun `runs collapse into ranges in page order`() {
        assertEquals("pages 2-4, 7", PageRanges.label(listOf(7, 3, 2, 4)))
        assertEquals("page 5", PageRanges.label(listOf(5)))
        assertEquals("pages 1, 3, 5", PageRanges.label(listOf(5, 1, 3)))
        assertEquals("pages 1-2", PageRanges.label(listOf(2, 1, 2)))
        assertEquals("", PageRanges.label(emptyList()))
    }
}
