package com.sheaf.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheaf.core.domain.model.Document
import com.sheaf.core.domain.repository.DocumentRepository
import com.sheaf.core.domain.repository.SettingsRepository
import com.sheaf.feature.reader.render.PdfRenderSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AppState(
    val loading: Boolean = true,
    val dynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val repository: DocumentRepository,
    private val renderFactory: PdfRenderSourceFactory,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<AppState> =
        combine(settings.dynamicColor, settings.onboardingComplete) { dyn, done ->
            AppState(loading = false, dynamicColor = dyn, onboardingDone = done)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState(loading = true))

    /** Emits the document id to open after an external "Open with" / VIEW intent is imported. */
    private val _pendingOpenDoc = MutableStateFlow<Long?>(null)
    val pendingOpenDoc: StateFlow<Long?> = _pendingOpenDoc.asStateFlow()
    fun consumePendingOpen() { _pendingOpenDoc.value = null }

    /** Copies an externally-opened PDF into app storage, indexes it, and requests opening it. */
    fun openIncoming(uriString: String) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    val src = Uri.parse(uriString)
                    val name = queryDisplayName(src)
                    val dir = File(context.filesDir, "opened").apply { mkdirs() }
                    val out = File(dir, "open_${System.currentTimeMillis()}.pdf")
                    val copied = context.contentResolver.openInputStream(src)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                    if (!copied) return@runCatching null
                    val fileUri = Uri.fromFile(out).toString()
                    val pageCount = renderFactory.open(fileUri).let { s ->
                        try {
                            s.pageCount
                        } finally {
                            s.close()
                        }
                    }
                    repository.upsert(
                        Document(
                            id = 0,
                            uri = fileUri,
                            displayName = name,
                            sizeBytes = out.length(),
                            pageCount = pageCount,
                            lastOpenedAt = System.currentTimeMillis(),
                            addedAt = System.currentTimeMillis(),
                        ),
                    )
                }.getOrNull()
            }
            _pendingOpenDoc.value = id
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "Document.pdf"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
            }
        }
        return name
    }
}
