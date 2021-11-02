package com.example.myapplication.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.myapplication.subscription.localdb.SkuDetailsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class ViewModelPremium(application: Application, var repositoryPremium: RepositoryPremium) :
    AndroidViewModel(application) {

    private val viewModelScope = CoroutineScope(Job() + Dispatchers.Main)

    var subSkuDetailsModelListLiveData: LiveData<List<SkuDetailsModel>>

    init {
        repositoryPremium.startDataSourceConnections()
        subSkuDetailsModelListLiveData = repositoryPremium.subSkuDetailsModelListLiveData
    }

    fun getBySkuId(skuId: String): SkuDetailsModel? {

        if (subSkuDetailsModelListLiveData.value != null)
            for (item in subSkuDetailsModelListLiveData.value!!) {
                if (item.sku == skuId) {
                    return item
                }
            }
        return null
    }


    override fun onCleared() {
        super.onCleared()
        repositoryPremium.endDataSourceConnections()
        viewModelScope.coroutineContext.cancel()
    }

    fun makePurchase(activity: Activity, skuDetailsModel: SkuDetailsModel) {
        repositoryPremium.launchBillingFlow(activity, skuDetailsModel)
    }

}