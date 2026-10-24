package com.sheaf.feature.reader.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting

/**
 * PdfBox memory policy: keep up to 48 MB of a parsed document in RAM and spill the rest to a temp
 * file in the app cache. Loading large PDFs fully into the heap is the main OOM risk in this app.
 */
internal object PdfBoxMemory {
    private const val MAX_MAIN_MEMORY_BYTES = 48L * 1024 * 1024

    fun setting(): MemoryUsageSetting = MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES)
}
