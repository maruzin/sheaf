package com.sheaf.feature.reader.forms

import com.sheaf.core.domain.model.FormField

/** Reads/writes AcroForm fields of a PDF (via a text-capable engine). */
interface PdfFormReader {
    suspend fun readFields(uri: String): List<FormField>
    /** Fill [values] (fieldName -> value) and write a flattened/updated copy; returns output file path or null. */
    suspend fun fillAndSave(uri: String, values: Map<String, String>): String?
}
