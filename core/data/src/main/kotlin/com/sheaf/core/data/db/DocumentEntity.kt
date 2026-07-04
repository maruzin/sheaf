package com.sheaf.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index(value = ["uri"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedAt: Long?,
    val addedAt: Long,
    val isBookmarked: Boolean,
    // Saved reading position (nullable until first opened)
    val posPageIndex: Int? = null,
    val posZoom: Float? = null,
    val posOffsetX: Float? = null,
    val posOffsetY: Float? = null,
)
