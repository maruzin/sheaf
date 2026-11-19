package com.sheaf.feature.reader

import android.graphics.Bitmap
import com.sheaf.core.domain.model.Annotation
import com.sheaf.feature.reader.export.ExportFormat
import java.io.File
import com.sheaf.core.domain.model.FormField
import com.sheaf.core.domain.model.OutlineEntry
import com.sheaf.core.domain.model.SearchHit

/** Immutable UI state for the reader screen (unidirectional data flow per Android Stack). */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val documentId: Long? = null,
    val uri: String = "",
    // The uri PdfBox/print work on: equals [uri], or the unlocked temp copy of a protected PDF.
    val workingUri: String = "",
    val displayName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val theme: ReaderTheme = ReaderTheme.System,
    // Reading mode (reflow): page text re-laid out as paragraphs at a comfortable size.
    val reflow: Boolean = false,
    val reflowLoading: Boolean = false,
    val reflowPages: List<List<String>> = emptyList(),
    val reflowScale: Float = 1f,
    // True when the document has no text layer (a scan): search/reflow suggest OCR instead.
    val textLayerMissing: Boolean = false,
    // Open documents shown as tabs (most recent last).
    val tabs: List<ReaderTab> = emptyList(),
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
    /** Drawing a shape (line, arrow, box, circle, white-out, redaction) instead of free ink. */
    val shapeTool: com.sheaf.feature.reader.markup.ShapeKind? = null,
    val signatureMode: Boolean = false,
    // Signatures: saved images (newest first), the one new placements use, decoded images for drawing.
    val savedSignatures: List<String> = emptyList(),
    val activeSignature: String? = null,
    val signatureImages: Map<String, Bitmap> = emptyMap(),
    val selectedSignatureId: Long? = null,
    val signatureBusy: Boolean = false,
    /** Why the last signature photo couldn't be used; shown in the signature dialog with a retry. */
    val signatureImportError: String? = null,
    /** The active placement is a stamp (date, tick, cross or text): its label, and its box height. */
    val activeStamp: String? = null,
    val activeStampHeight: Float? = null,
    /** Words and style of placed text boxes, by image name, so a selected one can be edited. */
    val textBoxes: Map<String, com.sheaf.feature.reader.signature.SignatureStore.TextBox> = emptyMap(),
    val annotationsListVisible: Boolean = false,
    val annotationsByPage: Map<Int, List<Annotation>> = emptyMap(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
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
    /** A new library document to open (OCR result or a page tool's copy). */
    val ocrDocumentId: Long? = null,
    val pageToolBusy: Boolean = false,
    // Flatten annotations/signature into the PDF for sharing
    val flattening: Boolean = false,
    val flattenedPath: String? = null,
    // Save a copy to the phone (system "Save" picker) and exports (JPG, TXT, Word, Excel)
    val pendingSave: PendingSave? = null,
    val exporting: Boolean = false,
    val exportReady: ExportReady? = null,
    // Extracted text, shown for copy / edit / translate
    val textView: TextViewState? = null,
    // Freemium
    val isPro: Boolean = false,
    val showPaywall: Boolean = false,
    val proPrice: String? = null,
    val billingMessage: String? = null,
    // Encrypted-document open flow
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
    // One-shot page the UI should scroll to (consumed via ConsumeScroll)
    val pendingScrollPage: Int? = null,
    val error: String? = null,
) {
    val hasSignature: Boolean get() = activeSignature != null

    /** True when a form value differs from what the file holds. */
    val formEdited: Boolean
        get() = formFields.any { f -> (formValues[f.name] ?: f.value) != f.value }

    /** Something the user added that the file doesn't contain yet (markup, signatures, form values). */
    val hasChanges: Boolean get() = annotationsByPage.isNotEmpty() || formEdited
}

/** A file ready for the system "Save" picker. [temp] files are deleted once written. */
data class PendingSave(val source: String, val suggestedName: String, val mime: String, val temp: Boolean)

/** Exported files, waiting for the user to share them or save them to the phone. */
data class ExportReady(val files: List<File>, val format: ExportFormat)

data class TextViewState(
    val loading: Boolean = true,
    val pages: List<String> = emptyList(),
    /** The document is a scan and its text needs OCR (a Pro feature) first. */
    val needsOcr: Boolean = false,
)

enum class ReaderTheme { System, Light, Dark, Sepia }

data class ReaderTab(val documentId: Long, val title: String)

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
    data object Undo : ReaderEvent
    data object Redo : ReaderEvent
    data class SetInkColor(val argb: Int) : ReaderEvent
    data class SetHighlighter(val on: Boolean) : ReaderEvent
    data class SetNoteMode(val on: Boolean) : ReaderEvent
    data class SetSignatureMode(val on: Boolean) : ReaderEvent
    data class SetShapeTool(val kind: com.sheaf.feature.reader.markup.ShapeKind?) : ReaderEvent
    data class RunPageTool(val tool: com.sheaf.feature.reader.pagetools.PageTool) : ReaderEvent
    data object StartSigning : ReaderEvent
    data class SelectSignature(val id: Long?) : ReaderEvent
    data class ChooseSignature(val name: String) : ReaderEvent
    data class ForgetSignature(val name: String) : ReaderEvent
    data object SaveToPhone : ReaderEvent
    data object ConsumeSave : ReaderEvent
    data class Export(val format: ExportFormat) : ReaderEvent
    data object ConsumeExport : ReaderEvent
    data object OpenTextView : ReaderEvent
    data object CloseTextView : ReaderEvent
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
    data object FlattenShare : ReaderEvent
    data object ConsumeFlattened : ReaderEvent
    data object ShowPaywall : ReaderEvent
    data object DismissPaywall : ReaderEvent
    data object RestorePurchase : ReaderEvent
    data object ConsumeBillingMessage : ReaderEvent
    data class SubmitOpenPassword(val password: String) : ReaderEvent
    data object CancelOpenPassword : ReaderEvent
    data object ConsumeScroll : ReaderEvent
    data class JumpTo(val page: Int) : ReaderEvent
    data object ToggleReflow : ReaderEvent
    data class SetReflowScale(val scale: Float) : ReaderEvent
    data class CloseTab(val documentId: Long) : ReaderEvent
}
