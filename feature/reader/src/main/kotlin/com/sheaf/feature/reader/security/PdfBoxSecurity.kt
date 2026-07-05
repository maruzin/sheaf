package com.sheaf.feature.reader.security

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
        runCatching {
            val out = File(context.cacheDir, "protected_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { doc ->
                    val permissions = AccessPermission()
                    val policy = StandardProtectionPolicy(password, password, permissions)
                    policy.encryptionKeyLength = 128
                    policy.permissions = permissions
                    doc.protect(policy)
                    doc.save(out)
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    override suspend fun decrypt(uri: String, password: String): String? = withContext(io) {
        ensureInit()
        runCatching {
            val dir = File(context.filesDir, "unlocked").apply { mkdirs() }
            val out = File(dir, "unlocked_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input, password).use { doc ->
                    doc.setAllSecurityToBeRemoved(true)
                    doc.save(out)
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
