package com.sheaf.feature.reader.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renderFactory: PdfRenderSourceFactory,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<LibraryUiState> =
        combine(
            repository.observeRecents(),
            repository.observeBookmarked(),
            _busy,
            _error,
        ) { recents, bookmarked, busy, error ->
            LibraryUiState(recents = recents, bookmarked = bookmarked, isBusy = busy, error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Emits the document id to open once import completes; screen consumes then clears. */
    private val _openDocument = MutableStateFlow<Long?>(null)
    val openDocument: StateFlow<Long?> = _openDocument.asStateFlow()
    fun consumeOpen() { _openDocument.value = null }

    /** Imports a picked PDF into the index (dedup by uri) and requests opening it. */
    fun onDocumentPicked(uri: String, displayName: String, sizeBytes: Long) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val existing = repository.findByUri(uri)
                val id = existing?.id ?: run {
                    val pageCount = renderFactory.open(uri).use { it.pageCount }
                    repository.upsert(
                        Document(
                            id = 0,
                            uri = uri,
                            displayName = displayName.ifBlank { "Document" },
                            sizeBytes = sizeBytes,
                            pageCount = pageCount,
                            lastOpenedAt = System.currentTimeMillis(),
                            addedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                _openDocument.value = id
            }.onFailure { t -> _error.value = t.message ?: "Couldn't open that file" }
            _busy.value = false
        }
    }

    fun toggleBookmark(document: Document) {
        viewModelScope.launch { repository.setBookmarked(document.id, !document.isBookmarked) }
    }
}

// Small helper so we can use the render source in a use{} block.
private inline fun <T> com.sheaf.feature.reader.render.PdfRenderSource.use(block: (com.sheaf.feature.reader.render.PdfRenderSource) -> T): T {
    try { return block(this) } finally { close() }
}
