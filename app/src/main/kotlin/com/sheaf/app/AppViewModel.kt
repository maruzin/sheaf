package com.sheaf.app

import com.sheaf.core.data.log.AppLog
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
    private val imagePdf: com.sheaf.feature.reader.export.ImagePdfBuilder,
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

    /** One-shot message when an "Open with" file couldn't be imported. */
    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()
    fun consumeOpenError() { _openError.value = null }

    /** Copies an externally-opened PDF into app storage, indexes it, and requests opening it. */
    fun openIncoming(uriString: String) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    val src = uriString.toUri()
                    // Another app could point a file:// link at Sheaf's own private storage
                    // (settings, database, decrypted copies) to get it copied into the library.
                    // Open file:// links through the canonical path that was checked, so a symlink
                    // swapped in afterwards can't redirect the read.
                    val canonical = if (src.scheme == "file") {
                        canonicalOutsideAppData(src) ?: return@runCatching null
                    } else {
                        null
                    }
                    var name = queryDisplayName(src)
                    val readable = if (canonical != null) Uri.fromFile(canonical) else src
                    val out: File
                    val copied: Boolean
                    if (isImage(readable)) {
                        // A shared photo or picture becomes a one-page PDF, ready to sign and share.
                        out = imagePdf.imagesToPdf(listOf(readable)) ?: return@runCatching null
                        name = name.substringBeforeLast('.') + ".pdf"
                        copied = true
                    } else {
                        val dir = File(context.filesDir, "opened").apply { mkdirs() }
                        out = File(dir, "open_${System.currentTimeMillis()}.pdf")
                        val stream = if (canonical != null) canonical.inputStream() else context.contentResolver.openInputStream(src)
                        copied = stream?.use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                            true
                        } ?: false
                    }
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
                }.onFailure { AppLog.e("OpenWith", "Importing an \"Open with\" file failed", it) }.getOrNull()
            }
            if (id == null) {
                AppLog.w("OpenWith", "An \"Open with\" file could not be imported (unreadable, empty or refused)")
                _openError.value = "Couldn't open that file. It may be damaged or not a PDF."
            }
            _pendingOpenDoc.value = id
        }
    }

    private fun isImage(uri: Uri): Boolean {
        val type = if (uri.scheme == "file") {
            android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty())
        } else {
            runCatching { context.contentResolver.getType(uri) }.getOrNull()
        }
        return type?.startsWith("image/") == true
    }

    /** The file's canonical location, or null if it is inside Sheaf's private data (or unresolvable). */
    private fun canonicalOutsideAppData(uri: Uri): File? {
        val path = uri.path ?: return null
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val dataDir = context.dataDir.canonicalPath + File.separator
        return target.takeUnless { it.path.startsWith(dataDir) }
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
