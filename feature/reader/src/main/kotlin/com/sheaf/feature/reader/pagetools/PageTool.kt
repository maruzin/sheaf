package com.sheaf.feature.reader.pagetools

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.NormPoint

/** A page tool the user picked in the reader, with its options. */
sealed interface PageTool {
    val pro: Boolean
    /** Suffix for the new copy's name: "Lease (numbered).pdf". */
    val suffix: String

    data class Numbers(val position: NumberPosition, val withTotal: Boolean) : PageTool {
        override val pro = true; override val suffix = "numbered"
    }
    data class Watermark(val text: String, val opacity: Float) : PageTool {
        override val pro = true; override val suffix = "watermarked"
    }
    data class Crop(val fraction: Float) : PageTool { override val pro = false; override val suffix = "cropped" }
    data class Blank(val afterIndex: Int) : PageTool { override val pro = false; override val suffix = "with blank page" }
    data class InsertPdf(val uri: String, val afterIndex: Int, val insertedPages: Int) : PageTool {
        override val pro = true; override val suffix = "with pages added"
    }
    data class Split(val afterIndex: Int) : PageTool { override val pro = false; override val suffix = "part" }
}

/**
 * Carries markup into the copy a page tool makes: same pages for numbers / watermark, shifted pages
 * after an insert, and points moved for a crop (they are measured over the visible page, which shrinks).
 * [part] picks the half of a split: 1 = pages up to the split, 2 = the rest.
 */
object PageToolRemap {
    fun remap(annotations: List<Annotation>, tool: PageTool, newDocumentId: Long, part: Int = 1): List<Annotation> =
        annotations.mapNotNull { a ->
            val moved = when (tool) {
                is PageTool.Numbers, is PageTool.Watermark -> a
                is PageTool.Crop -> {
                    val f = tool.fraction.coerceIn(0f, 0.3f); val span = 1f - 2 * f
                    a.copy(points = a.points.mapIndexed { i, p ->
                        // A signature's second point is its size (fractions of page width): scale, don't shift.
                        if (a.type == com.sheaf.core.domain.model.AnnotationType.Signature && i == 1) NormPoint(p.x / span, p.y / span)
                        else NormPoint((p.x - f) / span, (p.y - f) / span)
                    }, strokeWidth = a.strokeWidth / span)
                }
                is PageTool.Blank -> if (a.pageIndex > tool.afterIndex) a.copy(pageIndex = a.pageIndex + 1) else a
                is PageTool.InsertPdf -> if (a.pageIndex > tool.afterIndex) a.copy(pageIndex = a.pageIndex + tool.insertedPages) else a
                is PageTool.Split -> when {
                    part == 1 && a.pageIndex <= tool.afterIndex -> a
                    part == 2 && a.pageIndex > tool.afterIndex -> a.copy(pageIndex = a.pageIndex - tool.afterIndex - 1)
                    else -> null
                }
            }
            moved?.copy(id = 0, documentId = newDocumentId)
        }
}
