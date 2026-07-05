package com.sheaf.feature.reader.ocr

/** Adds an invisible OCR text layer to an (image-only) PDF so it becomes searchable. */
interface PdfOcr {
    /** Returns the output file path of a searchable copy, or null on failure. */
    suspend fun makeSearchable(uri: String): String?
}
