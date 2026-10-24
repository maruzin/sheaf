package com.sheaf.core.domain.repository

import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

/** Library index: recents, bookmarks, and saved reading positions. Implemented in :core:data. */
interface DocumentRepository {
    fun observeRecents(limit: Int = 50): Flow<List<Document>>
    fun observeBookmarked(): Flow<List<Document>>
    suspend fun document(id: Long): Document?
    suspend fun findByUri(uri: String): Document?
    suspend fun upsert(document: Document): Long
    suspend fun setBookmarked(documentId: Long, bookmarked: Boolean)
    suspend fun saveReadingPosition(position: ReadingPosition)
    suspend fun readingPosition(documentId: Long): ReadingPosition?
    /** Finds a document with the same display name and size (used to de-duplicate "Open with" copies). */
    suspend fun findByNameAndSize(displayName: String, sizeBytes: Long): Document?
    /** Records the real page count once known (protected PDFs are imported before unlocking). */
    suspend fun setPageCount(documentId: Long, pageCount: Int)
    /** Bumps the document to the top of recents. */
    suspend fun markOpened(documentId: Long)
    /** Removes the document and its annotations from the library index. */
    suspend fun delete(documentId: Long)
}
