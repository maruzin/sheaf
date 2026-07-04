package com.sheaf.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.ReadingPosition
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.feature.reader.render.PdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
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
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var source: PdfRenderSource? = null

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.Open -> open(event.documentId)
            is ReaderEvent.PageChanged -> _state.update { it.copy(currentPage = event.page) }
            is ReaderEvent.ZoomChanged -> _state.update { it.copy(zoom = event.zoom) }
            is ReaderEvent.SetTheme -> _state.update { it.copy(theme = event.theme) }
            ReaderEvent.ToggleOutline -> _state.update { it.copy(outlineVisible = !it.outlineVisible) }
            is ReaderEvent.JumpTo ->
                _state.update { it.copy(currentPage = event.page, outlineVisible = false) }
        }
    }

    private fun open(documentId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, documentId = documentId, error = null) }
            runCatching {
                val doc = repository.document(documentId) ?: error("Document not found")
                val opened = renderFactory.open(doc.uri)
                source = opened
                val restored = repository.readingPosition(documentId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        displayName = doc.displayName,
                        pageCount = opened.pageCount,
                        currentPage = restored?.pageIndex ?: 0,
                        zoom = restored?.zoom ?: 1f,
                        outline = opened.outline(),
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(isLoading = false, error = t.message ?: "Failed to open document")
                }
            }
        }
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
