package com.sheaf.feature.reader.forms

import android.content.Context
import android.net.Uri
import com.sheaf.core.domain.model.FormField
import com.sheaf.core.domain.model.FormFieldType
import com.sheaf.core.domain.model.NormRect
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class PdfBoxFormReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfFormReader {

    override suspend fun readFields(uri: String): List<FormField> = withContext(io) {
        ensureInit()
        val out = ArrayList<FormField>()
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { doc ->
                    val acro = doc.documentCatalog?.acroForm ?: return@use
                    val pages = doc.pages.toList()
                    for (field in acro.fieldTree) {
                        if (field.widgets.isEmpty()) continue
                        val widget = field.widgets.first()
                        val rect = widget.rectangle ?: continue
                        val pageIndex = pages.indexOfFirst { pageHasWidget(it, widget.cosObject) }
                        if (pageIndex < 0) continue
                        val page = pages[pageIndex]
                        val box = page.mediaBox
                        val pw = box.width.takeIf { it > 0 } ?: continue
                        val ph = box.height.takeIf { it > 0 } ?: continue
                        val nx = (rect.lowerLeftX - box.lowerLeftX) / pw
                        val ny = 1f - (rect.upperRightY - box.lowerLeftY) / ph
                        val nw = rect.width / pw
                        val nh = rect.height / ph
                        val fqn = field.fullyQualifiedName ?: continue
                        out += FormField(
                            name = fqn,
                            pageIndex = pageIndex,
                            type = typeOf(field),
                            rect = NormRect(nx, ny, nw, nh),
                            value = field.valueAsString.orEmpty(),
                            options = (field as? PDChoice)?.options.orEmpty(),
                        )
                    }
                }
            }
        }
        out
    }

    override suspend fun fillAndSave(uri: String, values: Map<String, String>): String? = withContext(io) {
        ensureInit()
        runCatching {
            val out = File(context.cacheDir, "filled_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                PDDocument.load(input).use { doc ->
                    val acro = doc.documentCatalog?.acroForm ?: return@withContext null
                    values.forEach { (name, value) ->
                        when (val field = acro.getField(name)) {
                            is PDTextField -> runCatching { field.setValue(value) }
                            is PDChoice -> runCatching { field.setValue(value) }
                            is PDCheckBox -> runCatching {
                                if (value.isBlank() || value.equals("Off", ignoreCase = true)) field.unCheck()
                                else field.check()
                            }
                            else -> Unit
                        }
                    }
                    doc.save(out)
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    private fun pageHasWidget(page: PDPage, widgetCos: Any): Boolean =
        runCatching { page.annotations.any { it.cosObject == widgetCos } }.getOrDefault(false)

    private fun typeOf(field: PDField): FormFieldType = when (field) {
        is PDTextField -> FormFieldType.Text
        is PDCheckBox -> FormFieldType.Checkbox
        is PDChoice -> FormFieldType.Choice
        else -> FormFieldType.Unsupported
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        val initialized = AtomicBoolean(false)
    }
}
