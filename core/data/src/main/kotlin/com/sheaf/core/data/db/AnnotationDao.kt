package com.sheaf.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY createdAt")
    fun observeForDocument(documentId: Long): Flow<List<AnnotationEntity>>

    @Upsert
    suspend fun upsert(entity: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM annotations WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun clearPage(documentId: Long, pageIndex: Int)
}
