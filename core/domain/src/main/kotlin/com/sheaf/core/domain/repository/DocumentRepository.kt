package com.sheaf.core.domain.repository

import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

/** Library index: recents, bookmarks, and saved reading positions. Implemented in :core:data. */
interface DocumentRepository {
    fun observeRecents(limit: Int = 50): Flow<List<Document>>
    fun observeBookmarked(): Flow<List<Document>>
    suspend fun upsert(document: Document): Long
    suspend fun setBookmarked(documentId: Long, bookmarked: Boolean)
    suspend fun saveReadingPosition(position: ReadingPosition)
    suspend fun readingPosition(documentId: Long): ReadingPosition?
    suspend fun delete(documentId: Long)
}
