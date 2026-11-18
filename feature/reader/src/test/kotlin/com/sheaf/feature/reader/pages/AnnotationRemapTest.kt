package com.sheaf.feature.reader.pages

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationRemapTest {

    private fun ann(page: Int, vararg pts: NormPoint) = Annotation(
        id = 7, documentId = 1, pageIndex = page, type = AnnotationType.Ink,
        colorArgb = 0, strokeWidth = 0.004f, points = pts.toList(),
    )

    @Test
    fun `annotations follow their page to its new position and deleted pages drop theirs`() {
        val anns = listOf(ann(0, NormPoint(0.1f, 0.1f)), ann(1, NormPoint(0.5f, 0.5f)), ann(2, NormPoint(0.9f, 0.9f)))
        // New order: page 2 first, then page 0; page 1 deleted.
        val out = AnnotationRemap.remap(anns, listOf(PageItem(2, 0), PageItem(0, 0)), newDocumentId = 99)
        assertEquals(listOf(0, 1), out.map { it.pageIndex })
        assertEquals(listOf(NormPoint(0.9f, 0.9f), NormPoint(0.1f, 0.1f)), out.map { it.points.single() })
        assertEquals(setOf(99L), out.map { it.documentId }.toSet())
        assertEquals(setOf(0L), out.map { it.id }.toSet()) // fresh rows
    }

    @Test
    fun `points turn clockwise with the page`() {
        val topLeft = NormPoint(0f, 0f)
        assertEquals(NormPoint(1f, 0f), AnnotationRemap.rotate(topLeft, 90))
        assertEquals(NormPoint(1f, 1f), AnnotationRemap.rotate(topLeft, 180))
        assertEquals(NormPoint(0f, 1f), AnnotationRemap.rotate(topLeft, 270))
        assertEquals(NormPoint(0f, 1f), AnnotationRemap.rotate(topLeft, -90))
        val p = NormPoint(0.2f, 0.3f)
        assertEquals(p, AnnotationRemap.rotate(AnnotationRemap.rotate(p, 90), 270))
    }
}
