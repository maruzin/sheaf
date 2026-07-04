package com.sheaf.core.domain.repository

import com.sheaf.core.domain.model.Annotation
import kotlinx.coroutines.flow.Flow

/** Persistence for page annotations (ink, highlight, notes). Implemented in :core:data. */
interface AnnotationRepository {
    fun observeForDocument(documentId: Long): Flow<List<Annotation>>
    suspend fun upsert(annotation: Annotation): Long
    suspend fun delete(id: Long)
    suspend fun clearPage(documentId: Long, pageIndex: Int)
}
