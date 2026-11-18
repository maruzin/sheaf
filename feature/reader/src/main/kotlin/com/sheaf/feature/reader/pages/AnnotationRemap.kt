package com.sheaf.feature.reader.pages

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.NormPoint

/**
 * Carries a document's annotations over to an edited copy ("Manage pages" → Save copy): each kept
 * page's annotations move to its new position, and their points turn with any rotation applied, so
 * ink and notes stay on the same words. Annotations on deleted pages are dropped.
 */
object AnnotationRemap {

    fun remap(annotations: List<Annotation>, items: List<PageItem>, newDocumentId: Long): List<Annotation> {
        val bySource = annotations.groupBy { it.pageIndex }
        return items.flatMapIndexed { newIndex, item ->
            bySource[item.sourceIndex].orEmpty().map { a ->
                a.copy(
                    id = 0,
                    documentId = newDocumentId,
                    pageIndex = newIndex,
                    points = a.points.map { rotate(it, item.rotation) },
                )
            }
        }
    }

    /** Turns a page-normalized point clockwise by [degrees] (a multiple of 90). */
    fun rotate(p: NormPoint, degrees: Int): NormPoint = when (((degrees % 360) + 360) % 360) {
        90 -> NormPoint(1f - p.y, p.x)
        180 -> NormPoint(1f - p.x, 1f - p.y)
        270 -> NormPoint(p.y, 1f - p.x)
        else -> p
    }
}
