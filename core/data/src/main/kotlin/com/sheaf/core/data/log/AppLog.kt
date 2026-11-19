package com.sheaf.core.data.log

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * On-device diagnostic log: errors, warnings and crashes go to logcat and to a small rotating file
 * in the app's private storage. Nothing leaves the device unless the user taps "Share diagnostic log"
 * in Settings. Play Console's Android vitals records crashes/ANRs separately and automatically.
 *
 * Privacy: callers log what failed and the exception, never document text, passwords or file names.
 */
object AppLog {
    private const val TAG = "Sheaf"
    private const val MAX_BYTES = 256L * 1024 // per file; one rotated backup, so <= 512 KB total
    const val LOG_FILE = "sheaf.log"
    const val CRASH_FILE = "last-crash.txt"

    @Volatile private var dir: File? = null
    @Volatile private var header: String = ""
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "sheaf-log").apply { isDaemon = true } }

    /** Call once from Application.onCreate(). Installs the crash handler. */
    fun init(context: Context, versionName: String, versionCode: Long) {
        val logDir = File(context.filesDir, "logs").apply { mkdirs() }
        dir = logDir
        header = "Sheaf $versionName ($versionCode) | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | " +
            "${Build.MANUFACTURER} ${Build.MODEL}"
        installCrashHandler(logDir)
        i("App", "Started: $header")
    }

    fun i(area: String, message: String) = write('I', area, message, null)
    fun w(area: String, message: String, error: Throwable? = null) = write('W', area, message, error)
    fun e(area: String, message: String, error: Throwable? = null) = write('E', area, message, error)

    private fun write(level: Char, area: String, message: String, error: Throwable?) {
        when (level) {
            'E' -> Log.e(TAG, "[$area] $message", error)
            'W' -> Log.w(TAG, "[$area] $message", error)
            else -> Log.i(TAG, "[$area] $message")
        }
        val target = dir ?: return
        val line = format(System.currentTimeMillis(), level, area, message, error)
        runCatching { writer.execute { LogFiles.append(target, line, MAX_BYTES) } }
    }

    /** Writes the crash report synchronously (the process is about to die), then defers to Android. */
    private fun installCrashHandler(logDir: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val now = System.currentTimeMillis()
                val report = "CRASH ${timestamp(now)} on thread '${thread.name}'\n$header\n${scrub(stackTrace(error))}"
                File(logDir, CRASH_FILE).writeText(report)
                LogFiles.append(logDir, format(now, 'E', "Crash", "Uncaught on '${thread.name}'", error), MAX_BYTES)
            }
            // Keep Android's default handling so Play's Android vitals still records the crash.
            previous?.uncaughtException(thread, error)
        }
    }

    /** Everything in one text file for the user to share: header, last crash, recent log lines. */
    fun exportTo(out: File): File? = runCatching {
        val logDir = dir ?: return null
        writer.submit {}.get() // flush pending writes
        out.parentFile?.mkdirs()
        out.writeText(buildString {
            appendLine("Sheaf diagnostic log, exported ${timestamp(System.currentTimeMillis())}")
            appendLine(header)
            appendLine()
            File(logDir, CRASH_FILE).takeIf { it.exists() }?.let {
                appendLine("=== Last crash ===")
                appendLine(it.readText())
                appendLine()
            }
            appendLine("=== Recent log ===")
            LogFiles.ordered(logDir).forEach { append(it.readText()) }
        })
        out
    }.getOrNull()

    /** Time of the last recorded crash, or null. */
    fun lastCrashAt(): Long? = dir?.let { File(it, CRASH_FILE) }?.takeIf { it.exists() }?.lastModified()

    fun clear() {
        val logDir = dir ?: return
        runCatching { writer.submit { logDir.listFiles()?.forEach { it.delete() } }.get() }
    }

    internal fun format(time: Long, level: Char, area: String, message: String, error: Throwable?): String =
        buildString {
            append(timestamp(time)).append(' ').append(level).append(" [").append(area).append("] ")
            append(scrub(message).replace('\n', ' '))
            append('\n')
            if (error != null) append(scrub(stackTrace(error)))
        }

    /**
     * The log promises never to hold document names: exception messages often quote a file path or
     * a content URI ("…/share/Lease.pdf: ENOSPC"), so paths and URIs are replaced before writing.
     */
    internal fun scrub(text: String): String =
        text.replace(URI, "<uri>").replace(PATH, "<path>")

    private val URI = Regex("""\b(?:content|file)://[^\s:,;)\]'"]+""")
    // File names contain spaces, so a path runs to the next colon, quote or line end (exception
    // messages put ": reason" after the path). Over-scrubbing a few words is fine; leaking a name isn't.
    private val PATH = Regex("""(?<![\w.])/(?:data|storage|sdcard|mnt|system/media)/[^\n:;)\]'"]+""")

    private fun timestamp(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(time))

    private fun stackTrace(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
}

/** File handling, separate so rotation is unit-tested without Android. */
internal object LogFiles {
    fun append(dir: File, line: String, maxBytes: Long) {
        val current = File(dir, AppLog.LOG_FILE)
        if (current.exists() && current.length() + line.length > maxBytes) {
            val backup = File(dir, AppLog.LOG_FILE + ".1")
            backup.delete()
            current.renameTo(backup)
        }
        current.appendText(line)
    }

    /** Oldest first: the rotated backup, then the current file. */
    fun ordered(dir: File): List<File> =
        listOf(File(dir, AppLog.LOG_FILE + ".1"), File(dir, AppLog.LOG_FILE)).filter { it.exists() }
}
