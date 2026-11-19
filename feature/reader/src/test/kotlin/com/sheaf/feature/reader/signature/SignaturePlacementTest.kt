package com.sheaf.feature.reader.signature

import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignaturePlacementTest {

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.0005f)

    @Test
    fun `round-trips through an annotation, rotation included`() {
        val p = SignaturePlacement("sig_1_2.png", 0.4f, 0.7f, 0.3f, 0.1f, 17.5f)
        val a = p.toAnnotation(documentId = 9, pageIndex = 2, id = 5)
        assertEquals(AnnotationType.Signature, a.type)
        assertEquals(p, SignaturePlacement.of(a))
    }

    @Test
    fun `non-signature annotations and broken notes are ignored`() {
        val ink = SignaturePlacement("sig_1.png", 0.5f, 0.5f, 0.2f, 0.1f).toAnnotation(1, 0).copy(type = AnnotationType.Ink)
        assertNull(SignaturePlacement.of(ink))
        val noNote = SignaturePlacement("sig_1.png", 0.5f, 0.5f, 0.2f, 0.1f).toAnnotation(1, 0).copy(note = null)
        assertNull(SignaturePlacement.of(noNote))
    }

    @Test
    fun `a new placement keeps the picture's proportions and stays on the page`() {
        val p = SignaturePlacement.new("sig_1.png", NormPoint(0.99f, 0.5f), imageAspect = 3f, pageAspect = 1.4142f)
        near(SignaturePlacement.DEFAULT_WIDTH, p.w)
        near(p.w / 3f, p.h)
        // Tapped at the right edge: pulled in so the whole box is on the page.
        near(1f - p.w / 2f, p.cx)
        // On a landscape page a tall picture is shrunk to under a third of the page height.
        val q = SignaturePlacement.new("sig_1.png", NormPoint(0.5f, 0.5f), imageAspect = 0.5f, pageAspect = 0.7f)
        near(0.7f / 3f, q.h)
    }

    @Test
    fun `image corners of an unturned box`() {
        // A4-ish page (aspect 1.5 for round numbers): box centred, 0.4 wide, 0.2 tall (of page width).
        val p = SignaturePlacement("s", 0.5f, 0.5f, 0.4f, 0.2f)
        val (bl, br, tl) = p.imageCorners(1.5f)
        near(0.3f, bl.x); near(0.5f + 0.1f / 1.5f, bl.y)
        near(0.7f, br.x); near(bl.y, br.y)
        near(0.3f, tl.x); near(0.5f - 0.1f / 1.5f, tl.y)
    }

    @Test
    fun `a quarter turn puts the image's bottom edge on the left`() {
        val p = SignaturePlacement("s", 0.5f, 0.5f, 0.4f, 0.2f, rotation = 90f)
        val (bl, br, tl) = p.imageCorners(1f)
        // Clockwise 90°: bottom-left → top-left of the turned box, bottom-right → bottom-left.
        near(0.4f, bl.x); near(0.3f, bl.y)
        near(0.4f, br.x); near(0.7f, br.y)
        near(0.6f, tl.x); near(0.3f, tl.y)
    }

    @Test
    fun `turning the page turns the signature with it and keeps its real size`() {
        val p = SignaturePlacement("s", 0.2f, 0.1f, 0.3f, 0.1f, rotation = 10f)
        val r = SignaturePlacement.rotateWithPage(p, 90, oldAspect = 1.5f)
        near(0.9f, r.cx); near(0.2f, r.cy)
        // Width was a fraction of the old width; the new width is the old height (1.5x larger).
        near(0.2f, r.w)
        near(100f, r.rotation)
        assertEquals(p, SignaturePlacement.rotateWithPage(p, 0, 1.5f))
    }
}
