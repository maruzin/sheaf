package com.sheaf.core.data

import com.sheaf.core.data.db.DocumentEntity
import com.sheaf.core.domain.model.Document
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the entity<->domain mapping used by the library index. */
class DocumentMappingTest {
    @Test
    fun entity_core_fields_round_trip() {
        val entity = DocumentEntity(
            id = 7, uri = "content://doc/7", displayName = "Spec.pdf",
            sizeBytes = 1234, pageCount = 42, lastOpenedAt = 100, addedAt = 50,
            isBookmarked = true,
        )
        val domain = Document(
            id = entity.id, uri = entity.uri, displayName = entity.displayName,
            sizeBytes = entity.sizeBytes, pageCount = entity.pageCount,
            lastOpenedAt = entity.lastOpenedAt, addedAt = entity.addedAt,
            isBookmarked = entity.isBookmarked,
        )
        assertEquals(entity.id, domain.id)
        assertEquals(entity.uri, domain.uri)
        assertEquals(entity.pageCount, domain.pageCount)
        assertEquals(entity.isBookmarked, domain.isBookmarked)
    }
}
