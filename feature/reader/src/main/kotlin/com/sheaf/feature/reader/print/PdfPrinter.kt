package com.sheaf.feature.reader.print

import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream

/** Sends an existing PDF to the Android print framework by streaming its bytes to the spooler. */
fun printPdf(context: Context, uri: String, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = PdfFilePrintAdapter(context, uri.toUri(), jobName.ifBlank { "Document" })
    runCatching {
        printManager.print(jobName.ifBlank { "Sheaf Document" }, adapter, PrintAttributes.Builder().build())
    }
}

private val printExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

private class PdfFilePrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val name: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("$name.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        if (destination == null) {
            callback?.onWriteFailed("No output")
            return
        }
        // onWrite runs on the main thread: copying a large PDF here caused ANRs. Copy on a worker
        // thread and report back through the callback (allowed from any thread).
        printExecutor.execute {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancellationSignal?.isCanceled == true) return@runCatching false
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    true
                } ?: error("Could not open document")
            }.onSuccess { finished ->
                if (finished) callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) else callback?.onWriteCancelled()
            }.onFailure {
                callback?.onWriteFailed(it.message)
            }
        }
    }
}
