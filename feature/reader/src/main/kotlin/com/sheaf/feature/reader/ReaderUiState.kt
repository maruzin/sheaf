package com.sheaf.feature.reader

import com.sheaf.core.domain.model.OutlineEntry

/** Immutable UI state for the reader screen (unidirectional data flow per Android Stack). */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val documentId: Long? = null,
    val uri: String = "",
    val displayName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val theme: ReaderTheme = ReaderTheme.System,
    val reflow: Boolean = false,
    val outline: List<OutlineEntry> = emptyList(),
    val outlineVisible: Boolean = false,
    val error: String? = null,
)

enum class ReaderTheme { System, Light, Dark, Sepia }

sealed interface ReaderEvent {
    data class Open(val documentId: Long) : ReaderEvent
    data class PageChanged(val page: Int) : ReaderEvent
    data class ZoomChanged(val zoom: Float) : ReaderEvent
    data class SetTheme(val theme: ReaderTheme) : ReaderEvent
    data object ToggleOutline : ReaderEvent
    data class JumpTo(val page: Int) : ReaderEvent
}
