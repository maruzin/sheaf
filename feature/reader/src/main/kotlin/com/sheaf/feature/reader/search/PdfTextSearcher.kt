package com.sheaf.feature.reader.search

import com.sheaf.core.domain.model.SearchHit

/** Full-text search over a PDF. Implemented with a text-capable engine (PdfBox-Android). */
interface PdfTextSearcher {
    /** Case-insensitive search; returns one hit per matching page with a short snippet. */
    suspend fun search(uri: String, query: String): List<SearchHit>
}
