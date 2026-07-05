package com.sheaf.feature.reader.compress

/** Reduces PDF size by downsampling and re-encoding embedded images. */
interface PdfCompressor {
    /** Returns the output file path of a smaller copy, or null on failure. */
    suspend fun compress(uri: String): String?
}
