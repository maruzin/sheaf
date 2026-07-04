package com.sheaf.core.data.repository

import com.sheaf.core.data.db.DocumentDao
import com.sheaf.core.data.db.DocumentEntity
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.model.ReadingPosition
import com.sheaf.core.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultDocumentRepository @Inject constructor(
    private val dao: DocumentDao,
) : DocumentRepository {

    override fun observeRecents(limit: Int): Flow<List<Document>> =
        dao.observeRecents(limit).map { list -> list.map { it.toDomain() } }

    override fun observeBookmarked(): Flow<List<Document>> =
        dao.observeBookmarked().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(document: Document): Long = dao.upsert(document.toEntity())

    override suspend fun setBookmarked(documentId: Long, bookmarked: Boolean) =
        dao.setBookmarked(documentId, bookmarked)

    override suspend fun saveReadingPosition(position: ReadingPosition) = with(position) {
        dao.savePosition(documentId, pageIndex, zoom, offsetX, offsetY, System.currentTimeMillis())
    }

    override suspend fun readingPosition(documentId: Long): ReadingPosition? =
        dao.byId(documentId)?.let { e ->
            val page = e.posPageIndex ?: return null
            ReadingPosition(e.id, page, e.posZoom ?: 1f, e.posOffsetX ?: 0f, e.posOffsetY ?: 0f)
        }

    override suspend fun delete(documentId: Long) = dao.delete(documentId)
}

private fun DocumentEntity.toDomain() = Document(
    id = id, uri = uri, displayName = displayName, sizeBytes = sizeBytes,
    pageCount = pageCount, lastOpenedAt = lastOpenedAt, addedAt = addedAt,
    isBookmarked = isBookmarked,
)

private fun Document.toEntity() = DocumentEntity(
    id = id, uri = uri, displayName = displayName, sizeBytes = sizeBytes,
    pageCount = pageCount, lastOpenedAt = lastOpenedAt, addedAt = addedAt,
    isBookmarked = isBookmarked,
)
