package com.sheaf.feature.reader.annotate

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.core.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationHistoryTest {

    private fun ann(id: Long, page: Int = 0, note: String? = null) = Annotation(
        id = id, documentId = 1, pageIndex = page, type = AnnotationType.Ink,
        colorArgb = 0, strokeWidth = 0.004f, points = listOf(NormPoint(0f, 0f), NormPoint(1f, 1f)),
        note = note, createdAt = id,
    )

    @Test
    fun `undo then redo returns the same op`() {
        val h = AnnotationHistory()
        val op = AnnotationOp.Added(ann(1))
        h.record(op)
        assertTrue(h.canUndo)
        assertEquals(op, h.popUndo())
        assertFalse(h.canUndo)
        assertTrue(h.canRedo)
        assertEquals(op, h.popRedo())
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun `a new edit clears redo`() {
        val h = AnnotationHistory()
        h.record(AnnotationOp.Added(ann(1)))
        h.popUndo()
        h.record(AnnotationOp.Added(ann(2)))
        assertFalse(h.canRedo)
        assertNull(h.popRedo())
    }

    @Test
    fun `stack keeps only the latest edits`() {
        val h = AnnotationHistory(limit = 3)
        (1L..5L).forEach { h.record(AnnotationOp.Added(ann(it))) }
        val popped = generateSequence { h.popUndo() }.toList()
        assertEquals(listOf(5L, 4L, 3L), popped.map { (it as AnnotationOp.Added).annotation.id })
    }

    @Test
    fun `a failed undo or redo puts the op back where it was`() {
        val h = AnnotationHistory()
        val op = AnnotationOp.Added(ann(1))
        h.record(op)
        h.popUndo(); h.undoFailed()
        assertTrue(h.canUndo); assertFalse(h.canRedo)
        h.popUndo(); h.popRedo(); h.redoFailed()
        assertFalse(h.canUndo); assertTrue(h.canRedo)
        assertEquals(op, h.popRedo())
    }

    @Test
    fun `clearing an empty page records nothing`() {
        val h = AnnotationHistory()
        h.record(AnnotationOp.Removed(emptyList()))
        assertFalse(h.canUndo)
    }

    @Test
    fun `revert and apply round-trip every op through the repository`() = runTest {
        val repo = FakeRepo()
        val a = ann(1); val b = ann(2); val c = ann(3, page = 1)
        listOf(a, b, c).forEach { repo.upsert(it) }

        // Clear page 0, then undo restores both strokes with their ids; redo clears again.
        val cleared = AnnotationOp.Removed(listOf(a, b))
        repo.apply(cleared)
        assertEquals(setOf(3L), repo.ids())
        repo.revert(cleared)
        assertEquals(setOf(1L, 2L, 3L), repo.ids())
        repo.apply(cleared)
        assertEquals(setOf(3L), repo.ids())

        // Edit a note, undo restores the old text.
        val noted = c.copy(note = "old")
        repo.upsert(noted)
        val edit = AnnotationOp.Updated(before = noted, after = noted.copy(note = "new"))
        repo.apply(edit)
        assertEquals("new", repo.rows[3L]?.note)
        repo.revert(edit)
        assertEquals("old", repo.rows[3L]?.note)

        // Undo an added stroke removes exactly that row.
        val added = AnnotationOp.Added(c)
        repo.revert(added)
        assertEquals(emptySet<Long>(), repo.ids())
    }

    private class FakeRepo : AnnotationRepository {
        val rows = linkedMapOf<Long, Annotation>()
        private val flow = MutableStateFlow(0)
        fun ids() = rows.keys.toSet()
        override fun observeForDocument(documentId: Long): Flow<List<Annotation>> =
            flow.map { rows.values.filter { it.documentId == documentId } }
        override suspend fun upsert(annotation: Annotation): Long {
            rows[annotation.id] = annotation; flow.value++; return annotation.id
        }
        override suspend fun delete(id: Long) { rows.remove(id); flow.value++ }
        override suspend fun clearPage(documentId: Long, pageIndex: Int) {
            rows.values.removeAll { it.documentId == documentId && it.pageIndex == pageIndex }; flow.value++
        }
    }
}
