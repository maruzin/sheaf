package com.sheaf.feature.reader

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.FormField
import com.sheaf.core.domain.model.OutlineEntry
import com.sheaf.core.domain.model.NormPoint
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
    val highlighter: Boolean = false,
    val noteMode: Boolean = false,
    val signatureMode: Boolean = false,
    val hasSignature: Boolean = false,
    val signaturePoints: List<NormPoint> = emptyList(),
    val annotationsListVisible: Boolean = false,
    val annotationsByPage: Map<Int, List<Annotation>> = emptyMap(),
    // Forms (AcroForm fill)
    val formFields: List<FormField> = emptyList(),
    val formMode: Boolean = false,
    val formValues: Map<String, String> = emptyMap(),
    val savingForm: Boolean = false,
    val filledUri: String? = null,
    // Security
    val protecting: Boolean = false,
    val protectedPath: String? = null,
    // Compression
    val compressing: Boolean = false,
    val compressedPath: String? = null,
    // OCR (make searchable)
    val ocrRunning: Boolean = false,
    val ocrDocumentId: Long? = null,
    // Freemium
    val isPro: Boolean = false,
    val showPaywall: Boolean = false,
    val billingMessage: String? = null,
    // Encrypted-document open flow
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
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
    data object ToggleAnnotationsList : ReaderEvent
    data object ToggleSearch : ReaderEvent
    data class Search(val query: String) : ReaderEvent
    data object NextResult : ReaderEvent
    data object PrevResult : ReaderEvent
    data object ToggleAnnotate : ReaderEvent
    data class SetInkColor(val argb: Int) : ReaderEvent
    data class SetHighlighter(val on: Boolean) : ReaderEvent
    data class SetNoteMode(val on: Boolean) : ReaderEvent
    data class SetSignatureMode(val on: Boolean) : ReaderEvent
    data object ToggleFormMode : ReaderEvent
    data class SetFormValue(val name: String, val value: String) : ReaderEvent
    data object SaveForm : ReaderEvent
    data object ConsumeFilled : ReaderEvent
    data class Protect(val password: String) : ReaderEvent
    data object ConsumeProtected : ReaderEvent
    data object Compress : ReaderEvent
    data object ConsumeCompressed : ReaderEvent
    data object Ocr : ReaderEvent
    data object ConsumeOcr : ReaderEvent
    data object ShowPaywall : ReaderEvent
    data object DismissPaywall : ReaderEvent
    data object ConsumeBillingMessage : ReaderEvent
    data class SubmitOpenPassword(val password: String) : ReaderEvent
    data object CancelOpenPassword : ReaderEvent
    data object ConsumeScroll : ReaderEvent
    data class JumpTo(val page: Int) : ReaderEvent
}
