package com.sheaf.feature.reader.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renderFactory: PdfRenderSourceFactory,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val thumbs = ConcurrentHashMap<String, Bitmap>()

    /** Renders (and caches) a small first-page preview for a library row. */
    suspend fun thumbnail(uriString: String, widthPx: Int): Bitmap? {
        thumbs[uriString]?.let { return it }
        return withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "thumbs/${uriString.hashCode()}_$widthPx.png")
            if (cacheFile.exists()) {
                BitmapFactory.decodeFile(cacheFile.path)?.let { cached ->
                    thumbs[uriString] = cached
                    return@withContext cached
                }
            }
            runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0) return@use null
                        renderer.openPage(0).use { page ->
                            val ar = if (page.width > 0) page.height.toFloat() / page.width else 1.414f
                            val h = (widthPx * ar).roundToInt().coerceIn(1, 3000)
                            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }.getOrNull()?.also { bmp ->
                thumbs[uriString] = bmp
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }

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
