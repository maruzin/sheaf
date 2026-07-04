package com.sheaf.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC, addedAt DESC LIMIT :limit")
    fun observeRecents(limit: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isBookmarked = 1 ORDER BY displayName")
    fun observeBookmarked(): Flow<List<DocumentEntity>>

    @Upsert
    suspend fun upsert(entity: DocumentEntity): Long

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byId(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun byUri(uri: String): DocumentEntity?

    @Query("UPDATE documents SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query(
        "UPDATE documents SET posPageIndex = :page, posZoom = :zoom, " +
            "posOffsetX = :ox, posOffsetY = :oy, lastOpenedAt = :now WHERE id = :id",
    )
    suspend fun savePosition(id: Long, page: Int, zoom: Float, ox: Float, oy: Float, now: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}
