package com.sheaf.feature.reader.search

import com.sheaf.core.domain.model.OutlineEntry

/** Extracts a document's outline (table of contents), empty if the PDF has none. */
interface PdfOutlineExtractor {
    suspend fun outline(uri: String): List<OutlineEntry>
}
