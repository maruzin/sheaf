package com.sheaf.feature.reader.security

import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class PdfBoxSecurity @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfSecurity {

    override suspend fun encrypt(uri: String, password: String): String? = withContext(io) {
        if (password.isBlank()) return@withContext null
        ensureInit()
        val out = File(context.cacheDir, "protected_${System.currentTimeMillis()}.pdf")
        val saved = runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, PdfBoxMemory.setting()).use { doc ->
                    val permissions = AccessPermission()
                    val policy = StandardProtectionPolicy(password, password, permissions)
                    policy.encryptionKeyLength = 128
                    policy.permissions = permissions
                    doc.protect(policy)
                    doc.save(out)
                }
                true
            } ?: false
        }.getOrDefault(false)
        if (saved && out.length() > 0) out.absolutePath else null.also { out.delete() }
    }

    override suspend fun decrypt(uri: String, password: String): String? = withContext(io) {
        ensureInit()
        // cacheDir: never backed up, and purged by purgeUnlockedCopies() when the reader closes.
        val out = File(unlockedDir().apply { mkdirs() }, "unlocked_${System.currentTimeMillis()}.pdf")
        val saved = runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, password, PdfBoxMemory.setting()).use { doc ->
                    doc.setAllSecurityToBeRemoved(true)
                    doc.save(out)
                }
                true
            } ?: false
        }.getOrDefault(false)
        if (saved && out.length() > 0) {
            out.absolutePath
        } else {
            out.delete() // wrong password or unreadable source: leave nothing behind
            null
        }
    }

    override suspend fun purgeUnlockedCopies() {
        withContext(io) { unlockedDir().listFiles()?.forEach { it.delete() } }
    }

    private fun unlockedDir() = File(context.cacheDir, "unlocked")

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
