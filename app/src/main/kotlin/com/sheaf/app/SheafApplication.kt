package com.sheaf.app

import android.app.Application
import com.sheaf.core.data.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SheafApplication : Application() {

    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        // Restore any existing Pro entitlement in the background.
        billingManager.start()
    }
}
