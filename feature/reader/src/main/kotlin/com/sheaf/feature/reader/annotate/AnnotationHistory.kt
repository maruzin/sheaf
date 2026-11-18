package com.sheaf.feature.reader.annotate

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.repository.AnnotationRepository

/**
 * One reversible annotation edit. Ops hold full snapshots, ids included: Room's upsert re-inserts a
 * deleted row under its original id, so undo/redo never has to track new ids.
 */
sealed interface AnnotationOp {
    data class Added(val annotation: Annotation) : AnnotationOp
    data class Removed(val annotations: List<Annotation>) : AnnotationOp
    data class Updated(val before: Annotation, val after: Annotation) : AnnotationOp
}

/**
 * Undo/redo stacks for annotation edits in one reader session. The stack rules are pure so they
 * are unit-tested without Android.
 *
 * A new edit clears the redo stack. The undo stack keeps the latest [limit] edits.
 */
class AnnotationHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undo = ArrayDeque<AnnotationOp>()
    private val redo = ArrayDeque<AnnotationOp>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(op: AnnotationOp) {
        if (op is AnnotationOp.Removed && op.annotations.isEmpty()) return
        undo.addLast(op)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    /** The latest edit, moved to the redo stack. The caller reverts it with [revert]. */
    fun popUndo(): AnnotationOp? = undo.removeLastOrNull()?.also { redo.addLast(it) }

    /** The latest undone edit, moved back to the undo stack. The caller re-applies it with [apply]. */
    fun popRedo(): AnnotationOp? = redo.removeLastOrNull()?.also { undo.addLast(it) }

    /** Reverses the last [popUndo] when reverting it failed. */
    fun undoFailed() {
        redo.removeLastOrNull()?.let { undo.addLast(it) }
    }

    /** Reverses the last [popRedo] when re-applying it failed. */
    fun redoFailed() {
        undo.removeLastOrNull()?.let { redo.addLast(it) }
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/** Performs [op] (used by redo). */
suspend fun AnnotationRepository.apply(op: AnnotationOp) {
    when (op) {
        is AnnotationOp.Added -> upsert(op.annotation)
        is AnnotationOp.Removed -> op.annotations.forEach { delete(it.id) }
        is AnnotationOp.Updated -> upsert(op.after)
    }
}

/** Reverts [op] (used by undo). */
suspend fun AnnotationRepository.revert(op: AnnotationOp) {
    when (op) {
        is AnnotationOp.Added -> delete(op.annotation.id)
        is AnnotationOp.Removed -> op.annotations.forEach { upsert(it) }
        is AnnotationOp.Updated -> upsert(op.before)
    }
}
