package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.subscription.PremiumSkus
import com.example.myapplication.subscription.ViewModelPremium
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    private val monthlySkuId = PremiumSkus.monthlySubscriptionId
    private val yearlySkuId = PremiumSkus.yearlySubscriptionId
    private val premiumViewModel: ViewModelPremium by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.subBtnMonth).setOnClickListener {
            premiumViewModel.getBySkuId(monthlySkuId)?.let {
                premiumViewModel.makePurchase(this@MainActivity, it)
            }

        }

        findViewById<Button>(R.id.subBtnYear).setOnClickListener {
            premiumViewModel.getBySkuId(yearlySkuId)?.let {
                premiumViewModel.makePurchase(this@MainActivity, it)
            }

        }

        premiumViewModel.subSkuDetailsModelListLiveData.observe(this@MainActivity,
            { skuList ->
                skuList.forEachIndexed { _, augmentedSkuDetails ->
                    if (augmentedSkuDetails.sku == PremiumSkus.monthlySubscriptionId) {


                        findViewById<TextView>(R.id.montlyPrice).text =
                            "MONTHLY PRICE  : ${augmentedSkuDetails.price}"


                    }

                    if (augmentedSkuDetails.sku == PremiumSkus.yearlySubscriptionId) {

                        findViewById<TextView>(R.id.yearlyPrice).text =
                            "YEARLY PRICE : ${augmentedSkuDetails.price}"
                    }


                }
            })
    }
}