package com.sheaf.app

import androidx.core.net.toUri
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
import com.sheaf.feature.reader.library.countPages
import java.io.File
import java.security.MessageDigest
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
                    val src = uriString.toUri()
                    val name = queryDisplayName(src)
                    val dir = File(context.filesDir, "opened").apply { mkdirs() }
                    val out = File(dir, "open_${System.currentTimeMillis()}.pdf")
                    val copied = context.contentResolver.openInputStream(src)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                    if (!copied || out.length() == 0L) {
                        out.delete()
                        return@runCatching null
                    }
                    // Opening the same file again reuses its library entry instead of stacking
                    // copies, but only when an earlier Sheaf-owned copy is byte-for-byte identical
                    // (name + size alone could match a different document).
                    existingIdenticalCopy(name, out)?.let { existingId ->
                        out.delete()
                        return@runCatching existingId
                    }
                    val fileUri = Uri.fromFile(out).toString()
                    repository.upsert(
                        Document(
                            id = 0,
                            uri = fileUri,
                            displayName = name,
                            sizeBytes = out.length(),
                            // 0 for password-protected files; the reader fills it in after unlock.
                            pageCount = countPages(renderFactory, fileUri),
                            lastOpenedAt = System.currentTimeMillis(),
                            addedAt = System.currentTimeMillis(),
                        ),
                    )
                }.getOrNull()
            }
            _pendingOpenDoc.value = id
        }
    }

    private suspend fun existingIdenticalCopy(name: String, copy: File): Long? {
        val candidate = repository.findByNameAndSize(name, copy.length()) ?: return null
        val candidateUri = candidate.uri.toUri()
        if (candidateUri.scheme != "file") return null
        val candidateFile = candidateUri.path?.let { File(it) } ?: return null
        val ownDir = File(context.filesDir, "opened").canonicalPath + File.separator
        if (!candidateFile.exists() || !candidateFile.canonicalPath.startsWith(ownDir)) return null
        return if (sha256(candidateFile).contentEquals(sha256(copy))) candidate.id else null
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun queryDisplayName(uri: Uri): String {
        // file:// links have no content provider to ask; use the file's own name.
        if (uri.scheme == "file") return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Document.pdf"
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
