package com.sheaf.feature.reader.pages

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class PdfBoxPageEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfPageEditor {

    override suspend fun save(uri: String, plan: List<PageOp>): String? = withContext(io) {
        if (plan.isEmpty()) return@withContext null
        ensureInit()
        runCatching {
            val dir = File(context.filesDir, "edited").apply { mkdirs() }
            val out = File(dir, "pages_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { src ->
                    val srcPages = src.pages.toList()
                    PDDocument().use { dst ->
                        plan.forEach { op ->
                            val page = srcPages.getOrNull(op.sourceIndex) ?: return@forEach
                            val imported = dst.importPage(page)
                            val base = ((page.rotation % 360) + 360) % 360
                            imported.rotation = ((base + op.rotation) % 360 + 360) % 360
                        }
                        dst.save(out)
                    }
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
