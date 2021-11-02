package com.example.myapplication.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.LiveData
import com.android.billingclient.api.*
import com.example.myapplication.subscription.PremiumSkus.NON_CONSUMABLE_SKUS
import com.example.myapplication.subscription.PremiumSkus.SUBS_SKUS
import com.example.myapplication.subscription.localdb.SkuDetailsModel
import com.example.myapplication.subscription.localdb.BillingDbInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class RepositoryPremium(private val application: Application) :
    PurchasesUpdatedListener, BillingClientStateListener {

    lateinit var storeBillingClient: BillingClient
    private lateinit var billingDbInstance: BillingDbInstance

    fun startDataSourceConnections() {
        Timber.d("startDataSourceConnections")
        instantiateAndConnectToPlayBillingService()

        billingDbInstance = BillingDbInstance.getInstance(application)
    }

    fun endDataSourceConnections() {
        if (storeBillingClient.isReady)
            storeBillingClient.endConnection()
        // normally you don't worry about closing a DB connection unless you have more than
        // one DB open. so no need to call 'localCacheBillingClient.close()'
        Timber.d("endDataSourceConnections")
    }

    private fun instantiateAndConnectToPlayBillingService() {
        storeBillingClient = BillingClient.newBuilder(application.applicationContext)
            .enablePendingPurchases() // required or app will crash
            .setListener(this).build()
        connectToPlayBillingService()
    }

    private fun connectToPlayBillingService(): Boolean {
        Timber.d("connectToPlayBillingService")
        if (!storeBillingClient.isReady) {
            storeBillingClient.startConnection(this)
            return true
        }
        return false
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Timber.d("onBillingSetupFinished successfully")
                querySkuDetailsAsync(BillingClient.SkuType.SUBS, SUBS_SKUS)
                // querySkuDetailsAsync(BillingClient.SkuType.INAPP, INAPP_SKUS)
                queryPurchasesAsync()
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                //Some apps may choose to make decisions based on this knowledge.
                Timber.d(billingResult.debugMessage)
            }
            else -> {
                //do nothing. Someone else will connect it through retry policy.
                //May choose to send to server though
                Timber.d(billingResult.debugMessage)
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        Timber.d("onBillingServiceDisconnected")
        connectToPlayBillingService()
    }

    fun queryPurchasesAsync() {
        Timber.d("queryPurchasesAsync called")
        val purchasesResult = HashSet<Purchase>()
        /*var result = storeBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
        Log.d(LOG_TAG, "queryPurchasesAsync INAPP results: ${result?.purchasesList?.size}")
        result?.purchasesList?.apply { purchasesResult.addAll(this) }*/
        if (isSubscriptionSupported()) {
            val result = storeBillingClient.queryPurchases(BillingClient.SkuType.SUBS)
            result.purchasesList?.apply { purchasesResult.addAll(this) }
            Timber.d("queryPurchasesAsync SUBS results: ${result.purchasesList?.size}")
        }
        processPurchases(purchasesResult)
    }

    private fun processPurchases(purchasesResult: Set<Purchase>) =
        CoroutineScope(Job() + Dispatchers.IO).launch {
            Timber.d("processPurchases called")
            val validPurchases = HashSet<Purchase>(purchasesResult.size)
            Timber.d("processPurchases newBatch content $purchasesResult")
            purchasesResult.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
//                    validPurchases.add(purchase)
                    if (isSignatureValid(purchase)) {
                        validPurchases.add(purchase)
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    Timber.d("Received a pending purchase of SKU: ${purchase.sku}")
                }
            }
            val (nonConsumables, _) = validPurchases.partition {
                NON_CONSUMABLE_SKUS.contains(it.sku)
            }

            acknowledgeNonConsumablePurchasesAsync(nonConsumables)
        }

    private fun acknowledgeNonConsumablePurchasesAsync(nonConsumables: List<Purchase>) {

        if (!nonConsumables.isNullOrEmpty()) {
            nonConsumables.forEach { purchase ->
                val params =
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                        .build()
                storeBillingClient.acknowledgePurchase(params) { billingResult ->
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            disburseNonConsumableEntitlement(purchase)
                        }
                        else -> {
                            Timber.d("acknowledgeNonConsumablePurchasesAsync response is ${billingResult.debugMessage}")
                        }
                    }
                }
            }
        } else {

            val monthly = billingDbInstance.skuDetailsDao().getById(SUBS_SKUS[0])
            monthly?.let {
                if (!it.canPurchase) {
                    billingDbInstance.skuDetailsDao().insertOrUpdate(SUBS_SKUS[0], true)
                }
            }

            val annual = billingDbInstance.skuDetailsDao().getById(SUBS_SKUS[1])
            annual?.let {
                if (!it.canPurchase){
                    billingDbInstance.skuDetailsDao().insertOrUpdate(SUBS_SKUS[1], true)
                }
            }




        }
    }

    private fun disburseNonConsumableEntitlement(purchase: Purchase) =
        CoroutineScope(Job() + Dispatchers.IO).launch {
            when (purchase.sku) {

                SUBS_SKUS[0] -> {
                    val monthly = billingDbInstance.skuDetailsDao().getById(SUBS_SKUS[0])
                    monthly?.let {
                        if (it.canPurchase) {
                            billingDbInstance.skuDetailsDao().insertOrUpdate(purchase.sku, false)
                        }
                    }
                }
                SUBS_SKUS[1] -> {
                    val annual = billingDbInstance.skuDetailsDao().getById(SUBS_SKUS[1])
                    annual?.let {
                        if (it.canPurchase) {
                            billingDbInstance.skuDetailsDao().insertOrUpdate(purchase.sku, false)
                        }
                    }
                }


            }
        }

    private fun isSignatureValid(purchase: Purchase): Boolean {
        return Security.verifyPurchaseKey(
            Security.BASE_64_ENCODED_PUBLIC_KEY,
            purchase.originalJson,
            purchase.signature
        )
    }

    private fun isSubscriptionSupported(): Boolean {
        val billingResult =
            storeBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        var succeeded = false
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> connectToPlayBillingService()
            BillingClient.BillingResponseCode.OK -> succeeded = true
            else -> {
                Timber.w(
                    "isSubscriptionSupported() error: ${billingResult.debugMessage}"
                )
            }
        }
        return succeeded
    }

    private val REGEX = "^P((\\d)*Y)?((\\d)*W)?((\\d)*D)?"
    private fun parseDuration(duration: String?): Int {
        var days = 0
        val pattern: Pattern = Pattern.compile(REGEX)
        val matcher: Matcher = pattern.matcher(duration!!)
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                days += 365 * Integer.valueOf(matcher.group(2))
            }
            if (matcher.group(3) != null) {
                days += 7 * Integer.valueOf(matcher.group(4))
            }
            if (matcher.group(5) != null) {
                days += Integer.valueOf(matcher.group(6))
            }
        }
        return days
    }

    private fun querySkuDetailsAsync(
        @BillingClient.SkuType skuType: String,
        skuList: List<String>
    ) {
        val params = SkuDetailsParams.newBuilder().setSkusList(skuList).setType(skuType).build()
        storeBillingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (skuDetailsList.orEmpty().isNotEmpty()) {
                        skuDetailsList?.forEach {
                            CoroutineScope(Job() + Dispatchers.IO).launch {
                                Timber.d(
                                    "Title ${it.title} Trail Period: ${it.freeTrialPeriod}   Price ${it.price} Duration ${
                                        parseDuration(
                                            it.freeTrialPeriod
                                        )
                                    } "
                                )
                                billingDbInstance.skuDetailsDao().insertOrUpdate(it)
                            }
                        }
                    }
                }
                else -> {
                    Timber.e(billingResult.debugMessage)
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, skuDetailsModel: SkuDetailsModel) =
        launchBillingFlow(activity, SkuDetails(skuDetailsModel.originalJson!!))

    private fun launchBillingFlow(activity: Activity, skuDetails: SkuDetails) {
        val purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
        storeBillingClient.launchBillingFlow(activity, purchaseParams)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                // will handle server verification, consumables, and updating the local cache
                purchases?.apply { processPurchases(this.toSet()) }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // item already owned? call queryPurchasesAsync to verify and process all such items
                Timber.d(billingResult.debugMessage)
                queryPurchasesAsync()
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                connectToPlayBillingService()
            }
            else -> {
                Timber.i(billingResult.debugMessage)
            }
        }
    }

//    companion object {
//
//        @Volatile
//        private var INSTANCE: RepositoryPremium? = null
//
//        fun getInstance(application: Application): RepositoryPremium =
//            INSTANCE ?: synchronized(this) {
//                INSTANCE
//                    ?: RepositoryPremium(application)
//                        .also { INSTANCE = it }
//            }
//    }

    val subSkuDetailsModelListLiveData: LiveData<List<SkuDetailsModel>> by lazy {
        if (!::billingDbInstance.isInitialized) {
            billingDbInstance = BillingDbInstance.getInstance(application)
        }
        billingDbInstance.skuDetailsDao().getSubscriptionSkuDetails()
    }
}

