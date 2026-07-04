package com.sheaf.feature.reader

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.OutlineEntry
import com.sheaf.core.domain.model.SearchHit

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
    // Search
    val searchActive: Boolean = false,
    val searching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val searchIndex: Int = 0,
    // Annotations
    val annotating: Boolean = false,
    val inkColorArgb: Int = 0xFFE4713B.toInt(),
    val annotationsByPage: Map<Int, List<Annotation>> = emptyMap(),
    // One-shot page the UI should scroll to (consumed via ConsumeScroll)
    val pendingScrollPage: Int? = null,
    val error: String? = null,
)

enum class ReaderTheme { System, Light, Dark, Sepia }

sealed interface ReaderEvent {
    data class Open(val documentId: Long) : ReaderEvent
    data class PageChanged(val page: Int) : ReaderEvent
    data class ZoomChanged(val zoom: Float) : ReaderEvent
    data class SetTheme(val theme: ReaderTheme) : ReaderEvent
    data object ToggleOutline : ReaderEvent
    data object ToggleSearch : ReaderEvent
    data class Search(val query: String) : ReaderEvent
    data object NextResult : ReaderEvent
    data object PrevResult : ReaderEvent
    data object ToggleAnnotate : ReaderEvent
    data class SetInkColor(val argb: Int) : ReaderEvent
    data object ConsumeScroll : ReaderEvent
    data class JumpTo(val page: Int) : ReaderEvent
}
