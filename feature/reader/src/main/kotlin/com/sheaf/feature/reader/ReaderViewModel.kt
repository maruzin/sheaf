package com.sheaf.feature.reader

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.model.ReadingPosition
import com.sheaf.core.domain.repository.AnnotationRepository
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.core.domain.repository.SettingsRepository
import com.sheaf.feature.reader.compress.PdfCompressor
import com.sheaf.feature.reader.forms.PdfFormReader
import com.sheaf.core.data.billing.BillingManager
import com.sheaf.feature.reader.ocr.PdfOcr
import com.sheaf.feature.reader.security.PdfSecurity
import com.sheaf.feature.reader.render.PdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import com.sheaf.feature.reader.search.PdfOutlineExtractor
import com.sheaf.feature.reader.search.PdfTextSearcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renderFactory: PdfRenderSourceFactory,
    private val searcher: PdfTextSearcher,
    private val outlineExtractor: PdfOutlineExtractor,
    private val annotationRepo: AnnotationRepository,
    private val settings: SettingsRepository,
    private val formReader: PdfFormReader,
    private val security: PdfSecurity,
    private val compressor: PdfCompressor,
    private val ocr: PdfOcr,
    private val billing: BillingManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var source: PdfRenderSource? = null

    init {
        viewModelScope.launch {
            settings.signature.collect { enc ->
                val pts = decodePoints(enc)
                _state.update { it.copy(signaturePoints = pts, hasSignature = pts.isNotEmpty()) }
            }
        }
        viewModelScope.launch {
            settings.isPro.collect { pro ->
                _state.update { it.copy(isPro = pro, showPaywall = if (pro) false else it.showPaywall) }
            }
        }
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.Open -> open(event.documentId)
            is ReaderEvent.PageChanged -> _state.update { it.copy(currentPage = event.page) }
            is ReaderEvent.ZoomChanged -> _state.update { it.copy(zoom = event.zoom) }
            is ReaderEvent.SetTheme -> _state.update { it.copy(theme = event.theme) }
            ReaderEvent.ToggleAnnotate -> _state.update { it.copy(annotating = !it.annotating) }
            is ReaderEvent.SetInkColor -> _state.update { it.copy(inkColorArgb = event.argb) }
            is ReaderEvent.SetHighlighter -> _state.update { it.copy(highlighter = event.on, noteMode = false, signatureMode = false) }
            is ReaderEvent.SetNoteMode -> _state.update { it.copy(noteMode = event.on, signatureMode = false) }
            is ReaderEvent.SetSignatureMode -> _state.update { it.copy(signatureMode = event.on, noteMode = false) }
            ReaderEvent.ToggleFormMode -> _state.update { it.copy(formMode = !it.formMode) }
            is ReaderEvent.SetFormValue ->
                _state.update { it.copy(formValues = it.formValues + (event.name to event.value)) }
            ReaderEvent.SaveForm -> saveForm()
            ReaderEvent.ConsumeFilled -> _state.update { it.copy(filledUri = null) }
            is ReaderEvent.Protect -> protect(event.password)
            ReaderEvent.ConsumeProtected -> _state.update { it.copy(protectedPath = null) }
            ReaderEvent.Compress -> compress()
            ReaderEvent.ConsumeCompressed -> _state.update { it.copy(compressedPath = null) }
            ReaderEvent.Ocr -> runOcr()
            ReaderEvent.ConsumeOcr -> _state.update { it.copy(ocrDocumentId = null) }
            ReaderEvent.ShowPaywall -> _state.update { it.copy(showPaywall = true) }
            ReaderEvent.DismissPaywall -> _state.update { it.copy(showPaywall = false) }
            ReaderEvent.ConsumeBillingMessage -> _state.update { it.copy(billingMessage = null) }
            is ReaderEvent.SubmitOpenPassword -> submitOpenPassword(event.password)
            ReaderEvent.CancelOpenPassword ->
                _state.update { it.copy(needsPassword = false, error = "Couldn't open this document") }
            ReaderEvent.ToggleOutline -> _state.update { it.copy(outlineVisible = !it.outlineVisible) }
            ReaderEvent.ToggleAnnotationsList -> _state.update { it.copy(annotationsListVisible = !it.annotationsListVisible) }
            ReaderEvent.ToggleSearch -> _state.update {
                if (it.searchActive) it.copy(searchActive = false, searchResults = emptyList(), searchQuery = "")
                else it.copy(searchActive = true)
            }
            is ReaderEvent.Search -> runSearch(event.query)
            ReaderEvent.NextResult -> stepResult(+1)
            ReaderEvent.PrevResult -> stepResult(-1)
            ReaderEvent.ConsumeScroll -> _state.update { it.copy(pendingScrollPage = null) }
            is ReaderEvent.JumpTo ->
                _state.update { it.copy(currentPage = event.page, pendingScrollPage = event.page) }
        }
    }

    private var openPassword: String? = null

    private fun open(documentId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, documentId = documentId, error = null) }
            val doc = repository.document(documentId)
            if (doc == null) {
                _state.update { it.copy(isLoading = false, error = "Document not found") }
                return@launch
            }
            val opened = runCatching { renderFactory.open(doc.uri) }.getOrNull()
            if (opened == null) {
                _state.update { it.copy(isLoading = false, error = "Couldn't open this document") }
                return@launch
            }
            source?.close() // release the previous renderer + file descriptor before replacing it
            source = opened
            val restored = repository.readingPosition(documentId)
            _state.update {
                it.copy(
                    isLoading = false,
                    needsPassword = false,
                    passwordError = null,
                    uri = doc.uri,
                    displayName = doc.displayName,
                    pageCount = opened.pageCount,
                    currentPage = restored?.pageIndex ?: 0,
                    zoom = restored?.zoom ?: 1f,
                    outline = opened.outline(),
                )
            }
            loadOutline(doc.uri)
            observeAnnotations(documentId)
            loadForms(doc.uri)
        }
    }

    private fun submitOpenPassword(password: String) {
        openPassword = password
        _state.value.documentId?.let { open(it) }
    }

    private fun runSearch(query: String) {
        val uri = _state.value.uri
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank() || uri.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            val results = runCatching { searcher.search(uri, query) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    searching = false,
                    searchResults = results,
                    searchIndex = 0,
                    pendingScrollPage = results.firstOrNull()?.pageIndex,
                    currentPage = results.firstOrNull()?.pageIndex ?: it.currentPage,
                )
            }
        }
    }

    private fun stepResult(delta: Int) {
        val s = _state.value
        if (s.searchResults.isEmpty()) return
        val next = (s.searchIndex + delta + s.searchResults.size) % s.searchResults.size
        val page = s.searchResults[next].pageIndex
        _state.update { it.copy(searchIndex = next, pendingScrollPage = page, currentPage = page) }
    }

    private fun loadOutline(uri: String) {
        viewModelScope.launch {
            val toc = runCatching { outlineExtractor.outline(uri) }.getOrDefault(emptyList())
            if (toc.isNotEmpty()) _state.update { it.copy(outline = toc) }
        }
    }

    private fun loadForms(uri: String) {
        viewModelScope.launch {
            val fields = runCatching { formReader.readFields(uri) }.getOrDefault(emptyList())
            val initial = fields.associate { it.name to it.value }
            _state.update { it.copy(formFields = fields, formValues = initial) }
        }
    }

    private fun saveForm() {
        val uri = _state.value.uri
        if (uri.isBlank() || _state.value.formFields.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(savingForm = true) }
            val out = runCatching { formReader.fillAndSave(uri, _state.value.formValues) }.getOrNull()
            _state.update { it.copy(savingForm = false, filledUri = out) }
        }
    }

    /** Returns true and raises the paywall if the current user isn't Pro. */
    private fun blockedByPaywall(): Boolean {
        if (_state.value.isPro) return false
        _state.update { it.copy(showPaywall = true) }
        return true
    }

    fun upgrade(activity: Activity) {
        viewModelScope.launch {
            // purchasePro returns null when the Play purchase flow launches OK, else a short error.
            val error = runCatching { billing.purchasePro(activity) }.getOrElse { "Couldn't start the purchase" }
            _state.update { it.copy(billingMessage = error) }
            // On success the Play sheet takes over; the isPro collector closes the paywall when it completes.
        }
    }

    private fun protect(password: String) {
        val uri = _state.value.uri
        if (uri.isBlank() || password.isBlank()) return
        if (blockedByPaywall()) return
        viewModelScope.launch {
            _state.update { it.copy(protecting = true) }
            val path = runCatching { security.encrypt(uri, password) }.getOrNull()
            _state.update { it.copy(protecting = false, protectedPath = path) }
        }
    }

    private fun compress() {
        val uri = _state.value.uri
        if (uri.isBlank()) return
        if (blockedByPaywall()) return
        viewModelScope.launch {
            _state.update { it.copy(compressing = true) }
            val path = runCatching { compressor.compress(uri) }.getOrNull()
            _state.update { it.copy(compressing = false, compressedPath = path) }
        }
    }

    private fun runOcr() {
        val s = _state.value
        if (s.uri.isBlank() || s.ocrRunning) return
        if (blockedByPaywall()) return
        viewModelScope.launch {
            _state.update { it.copy(ocrRunning = true) }
            val path = runCatching { ocr.makeSearchable(s.uri) }.getOrNull()
            if (path == null) {
                _state.update { it.copy(ocrRunning = false, error = "Couldn't OCR this document") }
                return@launch
            }
            val fileUri = Uri.fromFile(java.io.File(path)).toString()
            val id = runCatching {
                repository.upsert(
                    Document(
                        id = 0,
                        uri = fileUri,
                        displayName = s.displayName.substringBeforeLast(".").ifBlank { "Document" } + " (searchable).pdf",
                        sizeBytes = java.io.File(path).length(),
                        pageCount = s.pageCount,
                        lastOpenedAt = System.currentTimeMillis(),
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }.getOrNull()
            _state.update { it.copy(ocrRunning = false, ocrDocumentId = id) }
        }
    }

    private var annotationsStarted = false
    private fun observeAnnotations(documentId: Long) {
        if (annotationsStarted) return
        annotationsStarted = true
        viewModelScope.launch {
            annotationRepo.observeForDocument(documentId).collect { list ->
                _state.update { it.copy(annotationsByPage = list.groupBy { a -> a.pageIndex }) }
            }
        }
    }

    /** Persist a finished ink stroke (points are page-normalized 0..1). */
    fun saveStroke(pageIndex: Int, points: List<NormPoint>) {
        val id = _state.value.documentId ?: return
        if (points.size < 2) return
        viewModelScope.launch {
            val hi = _state.value.highlighter
            annotationRepo.upsert(
                Annotation(
                    documentId = id,
                    pageIndex = pageIndex,
                    type = if (hi) AnnotationType.Highlight else AnnotationType.Ink,
                    colorArgb = _state.value.inkColorArgb,
                    strokeWidth = if (hi) 0.02f else 0.004f,
                    points = points,
                ),
            )
        }
    }

    fun clearPageAnnotations(pageIndex: Int) {
        val id = _state.value.documentId ?: return
        viewModelScope.launch { annotationRepo.clearPage(id, pageIndex) }
    }

    fun saveNote(pageIndex: Int, point: NormPoint, text: String) {
        val id = _state.value.documentId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            annotationRepo.upsert(
                Annotation(
                    documentId = id,
                    pageIndex = pageIndex,
                    type = AnnotationType.Note,
                    colorArgb = _state.value.inkColorArgb,
                    strokeWidth = 0f,
                    points = listOf(point),
                    note = text.trim(),
                ),
            )
        }
    }

    fun updateNote(annotation: Annotation, text: String) {
        viewModelScope.launch {
            if (text.isBlank()) annotationRepo.delete(annotation.id)
            else annotationRepo.upsert(annotation.copy(note = text.trim()))
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch { annotationRepo.delete(id) }
    }

    fun saveSignature(points: List<NormPoint>) {
        if (points.size < 2) return
        viewModelScope.launch { settings.setSignature(encodePoints(points)) }
    }

    /** Stamp the saved signature centered at [at] on [pageIndex], in a fixed-size box. */
    fun stampSignature(pageIndex: Int, at: NormPoint) {
        val id = _state.value.documentId ?: return
        val sig = _state.value.signaturePoints
        if (sig.size < 2) return
        val boxW = 0.4f
        val boxH = 0.16f
        val mapped = sig.map { p ->
            NormPoint(
                x = (at.x - boxW / 2f + p.x * boxW).coerceIn(0f, 1f),
                y = (at.y - boxH / 2f + p.y * boxH).coerceIn(0f, 1f),
            )
        }
        viewModelScope.launch {
            annotationRepo.upsert(
                Annotation(
                    documentId = id,
                    pageIndex = pageIndex,
                    type = AnnotationType.Ink,
                    colorArgb = 0xFF15171C.toInt(),
                    strokeWidth = 0.003f,
                    points = mapped,
                ),
            )
        }
    }

    private fun encodePoints(points: List<NormPoint>): String =
        points.joinToString(";") { "${it.x},${it.y}" }

    private fun decodePoints(s: String): List<NormPoint> =
        if (s.isBlank()) emptyList() else s.split(";").mapNotNull { p ->
            val c = p.split(",")
            val x = c.getOrNull(0)?.toFloatOrNull()
            val y = c.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null) NormPoint(x, y) else null
        }

    /** Renders a page bitmap for the UI. Returns null if the source isn't ready or render fails. */
    suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap? =
        runCatching { source?.renderPage(pageIndex, widthPx, heightPx) }.getOrNull()

    fun aspectRatio(pageIndex: Int): Float = source?.pageAspectRatio(pageIndex) ?: 1.4142f

    fun persistPosition() {
        val s = _state.value
        val id = s.documentId ?: return
        viewModelScope.launch {
            repository.saveReadingPosition(
                ReadingPosition(id, s.currentPage, s.zoom, offsetX = 0f, offsetY = 0f),
            )
        }
    }

    override fun onCleared() {
        persistPosition()
        source?.close()
        source = null
    }
}
