package com.sheaf.feature.reader

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
            is ReaderEvent.Open -> open(event.documentId, event.uri)
            is ReaderEvent.PageChanged -> _state.update { it.copy(currentPage = event.page) }
            is ReaderEvent.ZoomChanged -> _state.update { it.copy(zoom = event.zoom) }
            is ReaderEvent.SetTheme -> _state.update { it.copy(theme = event.theme) }
            ReaderEvent.ToggleReflow -> _state.update { it.copy(reflow = !it.reflow) }
            ReaderEvent.ToggleOutline -> _state.update { it.copy(outlineVisible = !it.outlineVisible) }
            is ReaderEvent.JumpTo -> _state.update { it.copy(currentPage = event.page, outlineVisible = false) }
        }
    }

    private fun open(documentId: Long, uri: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, documentId = documentId, error = null) }
            runCatching {
                val opened = renderFactory.open(uri)
                source = opened
                val restored = repository.readingPosition(documentId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        pageCount = opened.pageCount,
                        currentPage = restored?.pageIndex ?: 0,
                        zoom = restored?.zoom ?: 1f,
                        outline = opened.outline(),
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(isLoading = false, error = t.message ?: "Failed to open document") }
            }
        }
    }

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
        source?.close()
        source = null
    }
}
