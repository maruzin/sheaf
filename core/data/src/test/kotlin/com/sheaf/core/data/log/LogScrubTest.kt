package com.sheaf.core.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The privacy policy promises the diagnostic log never holds document names. */
class LogScrubTest {
    @Test
    fun `file paths and content uris are removed`() {
        val msg = "java.io.FileNotFoundException: /data/user/0/com.sheaf/cache/share/ab12/Lease Agreement.pdf: " +
            "open failed: ENOSPC; source content://com.android.externalstorage.documents/document/primary%3ADownload%2FLease.pdf"
        val out = AppLog.scrub(msg)
        assertFalse(out, out.contains("Lease"))
        assertFalse(out, out.contains("Agreement"))
        assertEquals("java.io.FileNotFoundException: <path>: open failed: ENOSPC; source <uri>", out)
    }

    @Test
    fun `ordinary messages are untouched`() {
        assertEquals("Export to docx: 1 file(s)", AppLog.scrub("Export to docx: 1 file(s)"))
    }
}
