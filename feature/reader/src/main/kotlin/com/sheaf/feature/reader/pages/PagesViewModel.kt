package com.sheaf.feature.reader.pages

import com.sheaf.core.data.log.AppLog
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.repository.AnnotationRepository
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.feature.reader.render.PdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PagesViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renderFactory: PdfRenderSourceFactory,
    private val editor: PdfPageEditor,
    private val annotations: AnnotationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PagesUiState())
    val state: StateFlow<PagesUiState> = _state.asStateFlow()

    private var source: PdfRenderSource? = null

    fun open(documentId: Long) {
        if (_state.value.documentId == documentId && !_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, documentId = documentId, error = null) }
            runCatching {
                val doc = repository.document(documentId) ?: error("Document not found")
                val opened = renderFactory.open(doc.uri)
                source = opened
                _state.update {
                    it.copy(
                        isLoading = false,
                        uri = doc.uri,
                        displayName = doc.displayName,
                        items = (0 until opened.pageCount).map { i -> PageItem(i, 0) },
                    )
                }
            }.onFailure { t ->
                if (t !is SecurityException) AppLog.e("Pages", "Opening Manage pages failed", t)
                val message = if (t is SecurityException) {
                    "This PDF is password-protected. Remove the password before managing its pages."
                } else {
                    "Couldn't open this document"
                }
                _state.update { it.copy(isLoading = false, error = message) }
            }
        }
    }

    suspend fun renderThumb(sourceIndex: Int, widthPx: Int, heightPx: Int): Bitmap? =
        runCatching { source?.renderPage(sourceIndex, widthPx, heightPx) }.getOrNull()

    fun aspectRatio(sourceIndex: Int): Float = source?.pageAspectRatio(sourceIndex) ?: 1.4142f

    fun rotate(position: Int) = _state.update { it.copy(items = PageReducer.rotate(it.items, position)) }

    fun delete(position: Int) = _state.update { it.copy(items = PageReducer.delete(it.items, position)) }

    fun move(position: Int, delta: Int) =
        _state.update { it.copy(items = PageReducer.move(it.items, position, delta)) }

    fun setSelecting(on: Boolean) = _state.update { it.copy(selecting = on, selected = emptySet()) }

    fun toggleSelected(sourceIndex: Int) = _state.update {
        it.copy(selected = if (sourceIndex in it.selected) it.selected - sourceIndex else it.selected + sourceIndex)
    }

    fun selectAll() = _state.update {
        it.copy(selected = if (it.selected.size == it.items.size) emptySet() else it.items.map { p -> p.sourceIndex }.toSet())
    }

    /** Saves the arrangement as an edited copy ("Save copy"). */
    fun save() {
        val s = _state.value
        if (s.items.isEmpty()) return
        writeCopy(s.items, "edited")
    }

    /**
     * Saves only the ticked pages, in their current order and rotation, as a new PDF in the library
     * ("Lease (pages 2-4).pdf"); markup on those pages comes along.
     */
    fun extract() {
        val s = _state.value
        val picked = s.items.filter { it.sourceIndex in s.selected }
        if (picked.isEmpty()) return
        val positions = s.items.mapIndexedNotNull { i, item -> if (item.sourceIndex in s.selected) i + 1 else null }
        writeCopy(picked, PageRanges.label(positions))
    }

    private fun writeCopy(items: List<PageItem>, suffix: String) {
        val s = _state.value
        if (s.uri.isBlank() || items.isEmpty() || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val plan = items.map { PageOp(it.sourceIndex, it.rotation) }
            val path = runCatching { editor.save(s.uri, plan) }.getOrNull()
            if (path == null) {
                _state.update { it.copy(saving = false, error = "Couldn't save the edited PDF") }
                return@launch
            }
            val fileUri = Uri.fromFile(File(path)).toString()
            val newId = runCatching {
                repository.upsert(
                    Document(
                        id = 0,
                        uri = fileUri,
                        displayName = s.displayName.substringBeforeLast(".").ifBlank { "Document" } + " ($suffix).pdf",
                        sizeBytes = File(path).length(),
                        pageCount = items.size,
                        lastOpenedAt = System.currentTimeMillis(),
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }.getOrNull()
            // Bring the user's ink, highlights and notes along to the edited copy.
            val sourceId = s.documentId
            if (newId != null && sourceId != null) {
                runCatching {
                    val existing = annotations.observeForDocument(sourceId).first()
                    AnnotationRemap.remap(existing, items, newId, ::aspectRatio).forEach { annotations.upsert(it) }
                }
            }
            _state.update {
                it.copy(
                    saving = false,
                    savedDocumentId = newId,
                    selecting = false,
                    selected = emptySet(),
                    error = if (newId == null) "Couldn't add the new PDF to your library" else it.error,
                )
            }
        }
    }

    override fun onCleared() {
        source?.close()
        source = null
    }
}
