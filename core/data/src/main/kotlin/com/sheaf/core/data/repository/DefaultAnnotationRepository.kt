package com.sheaf.core.data.repository

import com.sheaf.core.data.db.AnnotationDao
import com.sheaf.core.data.db.AnnotationEntity
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.core.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultAnnotationRepository @Inject constructor(
    private val dao: AnnotationDao,
) : AnnotationRepository {

    override fun observeForDocument(documentId: Long): Flow<List<Annotation>> =
        dao.observeForDocument(documentId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(annotation: Annotation): Long = dao.upsert(annotation.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clearPage(documentId: Long, pageIndex: Int) =
        dao.clearPage(documentId, pageIndex)
}

private fun AnnotationEntity.toDomain() = Annotation(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    type = runCatching { AnnotationType.valueOf(type) }.getOrDefault(AnnotationType.Ink),
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    points = decodePoints(points),
    note = note,
    createdAt = createdAt,
)

private fun Annotation.toEntity() = AnnotationEntity(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    type = type.name,
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    points = encodePoints(points),
    note = note,
    createdAt = createdAt,
)

private fun encodePoints(points: List<NormPoint>): String =
    points.joinToString(";") { "${it.x},${it.y}" }

private fun decodePoints(s: String): List<NormPoint> =
    if (s.isBlank()) emptyList() else s.split(";").mapNotNull { p ->
        val c = p.split(",")
        if (c.size == 2) {
            val x = c[0].toFloatOrNull(); val y = c[1].toFloatOrNull()
            if (x != null && y != null) NormPoint(x, y) else null
        } else null
    }
