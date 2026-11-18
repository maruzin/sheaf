package com.sheaf.feature.reader.merge

/** Result of a merge: the output file, or why it failed (so the UI can say which file was the problem). */
sealed interface MergeResult {
    data class Success(val path: String, val pageCount: Int) : MergeResult
    /** [index] is the position of the offending input in the list the caller passed. */
    data class Protected(val index: Int) : MergeResult
    data class Unreadable(val index: Int) : MergeResult
    data object Failed : MergeResult
}

/** Joins whole PDFs, in the given order, into one new PDF. */
interface PdfMerger {
    suspend fun merge(uris: List<String>): MergeResult
}
