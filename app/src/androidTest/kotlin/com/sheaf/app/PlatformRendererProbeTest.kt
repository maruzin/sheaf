package com.sheaf.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sheaf.feature.reader.render.AndroidPdfRenderSource
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Android 8.0 / 8.1 bug guard: after PdfRenderer failed on a protected PDF, every later PdfRenderer in
 * the process threw "password required" (and the broken renderer crashed the app when finalised).
 * Sheaf must refuse the protected file without poisoning the renderer, and keep opening plain files.
 */
@RunWith(AndroidJUnit4::class)
class PlatformRendererProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pdf(name: String, password: String?): File {
        PDFBoxResourceLoader.init(context)
        val f = File(context.cacheDir, name)
        PDDocument().use { d ->
            d.addPage(PDPage(PDRectangle.A4))
            if (password != null) d.protect(StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 128 })
            d.save(f)
        }
        return f
    }

    @Test
    fun aProtectedFileNeverBreaksOpeningPlainFilesAfterIt() = runBlocking {
        val factory = AndroidPdfRenderSource.Factory(context, Dispatchers.IO)
        val locked = pdf("guard-locked.pdf", "pw123")
        val plain = pdf("guard-plain.pdf", null)
        repeat(3) {
            val refused = runCatching { factory.open(Uri.fromFile(locked).toString()) }.exceptionOrNull()
            assertTrue("protected file is refused as protected, got $refused", refused is SecurityException)
            val src = factory.open(Uri.fromFile(plain).toString())
            assertEquals(1, src.pageCount)
            src.close()
        }
        // Give the finalizers a chance to run: a poisoned renderer would crash the process here.
        repeat(3) { System.gc(); System.runFinalization(); Thread.sleep(200) }
        val again = factory.open(Uri.fromFile(plain).toString())
        assertEquals(1, again.pageCount)
        again.close()
    }
}
