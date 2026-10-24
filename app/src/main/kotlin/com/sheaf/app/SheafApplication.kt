package com.sheaf.app

import android.app.Application
import com.sheaf.core.data.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import com.sheaf.feature.reader.security.PdfSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application() {

    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var security: PdfSecurity

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Restore any existing Pro entitlement in the background.
        billingManager.start()
        // Remove plaintext copies of protected PDFs left behind if the process was killed mid-read.
        appScope.launch { runCatching { security.purgeUnlockedCopies() } }
    }
}
