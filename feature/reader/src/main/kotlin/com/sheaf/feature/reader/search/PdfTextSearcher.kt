package com.sheaf.feature.reader.search

import com.sheaf.core.domain.model.SearchHit

/** Text access for a PDF. Implemented with a text-capable engine (PdfBox-Android). */
interface PdfTextSearcher {
    /**
     * Extracts the plain text of every page (index = page). Pages without a text layer (scans) are
     * empty strings. Callers cache the result: search and reflow both read from it.
     */
    suspend fun pageTexts(uri: String): List<String>

    /** Case-insensitive search; returns one hit per matching page with a short snippet. */
    suspend fun search(uri: String, query: String): List<SearchHit> = TextSearch.find(pageTexts(uri), query)
}
