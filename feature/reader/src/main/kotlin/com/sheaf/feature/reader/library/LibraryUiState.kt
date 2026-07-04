package com.sheaf.feature.reader.library

import com.sheaf.core.domain.model.Document

data class LibraryUiState(
    val recents: List<Document> = emptyList(),
    val bookmarked: List<Document> = emptyList(),
    val isBusy: Boolean = false,
    val error: String? = null,
)
