package com.sheaf.app

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/** Builds small real PDFs for the end-to-end tests. */
object TestPdfs {
    const val SECRET_WORD = "zebracorn"
    const val PASSWORD = "s3cret"

    fun plain(context: Context, name: String, pages: Int = 3): File =
        write(context, name, pages, password = null)

    fun protected(context: Context, name: String): File = write(context, name, pages = 1, password = PASSWORD)

    private fun write(context: Context, name: String, pages: Int, password: String?): File {
        PDFBoxResourceLoader.init(context)
        val dir = File(context.cacheDir, "e2e").apply { mkdirs() }
        val out = File(dir, name)
        PDDocument().use { doc ->
            for (i in 1..pages) {
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 18f)
                    cs.newLineAtOffset(72f, 700f)
                    val word = if (i == pages) " The secret word is $SECRET_WORD." else ""
                    cs.showText("Sheaf test page $i.$word")
                    cs.endText()
                }
            }
            if (password != null) {
                val perms = AccessPermission()
                doc.protect(StandardProtectionPolicy(password, password, perms).apply { encryptionKeyLength = 128 })
            }
            doc.save(out)
        }
        return out
    }
}
