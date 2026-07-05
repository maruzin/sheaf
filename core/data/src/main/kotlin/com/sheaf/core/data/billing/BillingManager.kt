package com.sheaf.core.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.sheaf.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play Billing wrapper for the one-time "Sheaf Pro" unlock. Entitlement is persisted via
 * [SettingsRepository.isPro]; the actual purchase flow requires a Play Console product + signed build
 * (configured at release). Reads are safe no-ops when Play Billing is unavailable (e.g. emulator).
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var connected = false

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { purchases.forEach { handlePurchase(it) } }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** Call once at app launch to restore an existing entitlement. */
    fun start() {
        scope.launch { runCatching { restore() } }
    }

    /** Launches the purchase flow. Returns null if launched OK, else a short error message. */
    suspend fun purchasePro(activity: Activity): String? {
        ensureConnected()
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        val details = runCatching { client.queryProductDetails(params).productDetailsList?.firstOrNull() }
            .getOrNull() ?: return "Pro isn't available right now"
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null else result.debugMessage
    }

    suspend fun restore() {
        ensureConnected()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = runCatching { client.queryPurchasesAsync(params) }.getOrNull() ?: return
        result.purchasesList.forEach { handlePurchase(it) }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRO_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) {
            runCatching {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                )
            }
        }
        settings.setPro(true)
    }

    private suspend fun ensureConnected() {
        if (connected) return
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
                override fun onBillingServiceDisconnected() {
                    connected = false
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
            })
        }
    }

    private companion object {
        const val PRO_ID = "sheaf_pro"
    }
}
