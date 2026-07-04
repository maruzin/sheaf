package com.sheaf.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    indices = [Index(value = ["documentId", "pageIndex"])],
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val type: String,
    val colorArgb: Int,
    val strokeWidth: Float,
    // Points encoded as "x,y;x,y;..." in normalized 0..1 space.
    val points: String,
    val note: String?,
    val createdAt: Long,
)
