package com.sheaf.core.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `log rotates to one backup and keeps the newest lines`() {
        val dir = tmp.newFolder()
        repeat(30) { LogFiles.append(dir, "line $it ".padEnd(40, '.') + "\n", maxBytes = 400) }
        val files = LogFiles.ordered(dir)
        assertEquals(listOf("sheaf.log.1", "sheaf.log"), files.map { it.name })
        assertTrue(files.all { it.length() <= 400 })
        val all = files.joinToString("") { it.readText() }
        assertTrue("newest line kept", all.contains("line 29 "))
        assertTrue("oldest lines dropped", !all.contains("line 0 "))
    }

    @Test
    fun `entries are one line plus the stack trace`() {
        val text = AppLog.format(0L, 'E', "Reader", "open failed\nsecond line", IllegalStateException("boom"))
        val first = text.lines().first()
        assertEquals("1970-01-01 00:00:00.000 UTC E [Reader] open failed second line", first)
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
    }
}
