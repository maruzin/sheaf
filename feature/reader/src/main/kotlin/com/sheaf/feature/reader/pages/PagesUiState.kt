package com.sheaf.feature.reader.pages

/** A page card in the editor: its original index in the source doc and applied rotation (deg). */
data class PageItem(val sourceIndex: Int, val rotation: Int)

data class PagesUiState(
    val isLoading: Boolean = true,
    val documentId: Long? = null,
    val uri: String = "",
    val displayName: String = "",
    val items: List<PageItem> = emptyList(),
    val saving: Boolean = false,
    val savedDocumentId: Long? = null,
    val error: String? = null,
)
