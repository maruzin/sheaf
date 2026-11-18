package com.sheaf.app

import android.app.Application
import com.sheaf.core.data.billing.BillingManager
import com.sheaf.core.data.log.AppLog
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.HiltAndroidApp
import com.sheaf.feature.reader.security.PdfSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application() {

    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var security: PdfSecurity

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // First: the on-device diagnostic log and crash handler (Settings -> Share diagnostic log).
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        AppLog.init(this, info?.versionName ?: "?", info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L)
        // Restore any existing Pro entitlement in the background.
        billingManager.start()
        // Remove plaintext copies of protected PDFs left behind if the process was killed mid-read.
        appScope.launch { runCatching { security.purgeUnlockedCopies() } }
        appScope.launch { runCatching { purgeShareCopies() } }
    }

    /**
     * Share-only outputs (filled forms, protected/compressed copies, flattened "Share with markup"
     * files) live in cacheDir. For a protected source these are decrypted plaintext, so don't leave
     * them around: anything older than an hour has long been handed to the share sheet.
     */
    private fun purgeShareCopies() {
        val cutoff = System.currentTimeMillis() - SHARE_COPY_TTL_MS
        val dirs = listOf(File(cacheDir, "share"), File(cacheDir, "compressed"), File(cacheDir, "signed"))
        dirs.forEach { dir ->
            dir.listFiles { f -> f.isFile && f.name.endsWith(".pdf") && f.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        const val SHARE_COPY_TTL_MS = 60L * 60 * 1000
    }
}
