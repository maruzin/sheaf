package com.sheaf.feature.reader.pagetools

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PageToolRemapTest {
    private fun ink(page: Int, vararg p: Pair<Float, Float>) = Annotation(
        id = 9, documentId = 1, pageIndex = page, type = AnnotationType.Ink, colorArgb = 0, strokeWidth = 0.004f,
        points = p.map { NormPoint(it.first, it.second) }, note = null, createdAt = 0,
    )

    @Test fun `numbers and watermark keep markup where it was`() {
        val a = ink(2, 0.3f to 0.4f)
        val r = PageToolRemap.remap(listOf(a), PageTool.Numbers(NumberPosition.BottomCenter, false), 7).single()
        assertEquals(2, r.pageIndex); assertEquals(a.points, r.points); assertEquals(7, r.documentId); assertEquals(0, r.id)
    }

    @Test fun `a blank page shifts later markup by one`() {
        val r = PageToolRemap.remap(listOf(ink(0, 0.1f to 0.1f), ink(1, 0.1f to 0.1f)), PageTool.Blank(0), 7)
        assertEquals(listOf(0, 2), r.map { it.pageIndex })
    }

    @Test fun `inserted pages shift later markup by their count`() {
        val r = PageToolRemap.remap(listOf(ink(0, 0.5f to 0.5f), ink(3, 0.5f to 0.5f)), PageTool.InsertPdf("x", 1, 4), 7)
        assertEquals(listOf(0, 7), r.map { it.pageIndex })
    }

    @Test fun `split sends each mark to its own part`() {
        val marks = listOf(ink(0, 0.5f to 0.5f), ink(2, 0.5f to 0.5f), ink(3, 0.5f to 0.5f))
        assertEquals(listOf(0), PageToolRemap.remap(marks, PageTool.Split(1), 7, part = 1).map { it.pageIndex })
        assertEquals(listOf(0, 1), PageToolRemap.remap(marks, PageTool.Split(1), 8, part = 2).map { it.pageIndex })
    }

    @Test fun `crop moves points so markup stays on the same words`() {
        val r = PageToolRemap.remap(listOf(ink(0, 0.1f to 0.1f, 0.5f to 0.5f)), PageTool.Crop(0.1f), 7).single()
        assertEquals(0f, r.points[0].x, 1e-5f)
        assertEquals(0.5f, r.points[1].x, 1e-5f)
        assertEquals(0.004f / 0.8f, r.strokeWidth, 1e-6f)
    }
}
