package com.sheaf.feature.reader.render

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 8.0 / 8.1 platform bug (fixed in Android 9): once [android.graphics.pdf.PdfRenderer] fails
 * on a password-protected file, every later PdfRenderer in the process throws "password required",
 * even for plain files, and the half-built renderer crashes the app with a native fault when it is
 * finalised. Every PdfRenderer in Sheaf (reader, page manager, library thumbnails) must call [check]
 * first, so a protected or broken file never reaches PdfRenderer on those versions.
 */
object PdfRendererGuard {
    private val pdfBoxReady = AtomicBoolean(false)

    /** Throws SecurityException (protected) or IOException (unreadable) on Android 8; no-op on 9+. */
    fun check(context: Context, uri: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return
        if (pdfBoxReady.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("Could not read the file")
        try {
            input.use { PDDocument.load(it, PdfBoxMemory.setting()).close() }
        } catch (e: InvalidPasswordException) {
            throw SecurityException("password required", e)
        } catch (e: IOException) {
            throw IOException("Not a readable PDF", e)
        }
    }
}
