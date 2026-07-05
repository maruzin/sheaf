package com.sheaf.feature.reader.pages

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.feature.reader.render.PdfRenderSource
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PagesViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renderFactory: PdfRenderSourceFactory,
    private val editor: PdfPageEditor,
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
                _state.update { it.copy(isLoading = false, error = t.message ?: "Failed to open") }
            }
        }
    }

    suspend fun renderThumb(sourceIndex: Int, widthPx: Int, heightPx: Int): Bitmap? =
        runCatching { source?.renderPage(sourceIndex, widthPx, heightPx) }.getOrNull()

    fun aspectRatio(sourceIndex: Int): Float = source?.pageAspectRatio(sourceIndex) ?: 1.4142f

    fun rotate(position: Int) = _state.update { s ->
        s.copy(items = s.items.mapIndexed { i, it -> if (i == position) it.copy(rotation = (it.rotation + 90) % 360) else it })
    }

    fun delete(position: Int) = _state.update { s ->
        if (s.items.size <= 1) s else s.copy(items = s.items.filterIndexed { i, _ -> i != position })
    }

    fun move(position: Int, delta: Int) = _state.update { s ->
        val target = position + delta
        if (target < 0 || target >= s.items.size) return@update s
        val list = s.items.toMutableList()
        val tmp = list[position]; list[position] = list[target]; list[target] = tmp
        s.copy(items = list)
    }

    fun save() {
        val s = _state.value
        if (s.uri.isBlank() || s.items.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val plan = s.items.map { PageOp(it.sourceIndex, it.rotation) }
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
                        displayName = s.displayName.substringBeforeLast(".").ifBlank { "Document" } + " (edited).pdf",
                        sizeBytes = File(path).length(),
                        pageCount = s.items.size,
                        lastOpenedAt = System.currentTimeMillis(),
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }.getOrNull()
            _state.update { it.copy(saving = false, savedDocumentId = newId) }
        }
    }

    override fun onCleared() {
        source?.close()
        source = null
    }
}
