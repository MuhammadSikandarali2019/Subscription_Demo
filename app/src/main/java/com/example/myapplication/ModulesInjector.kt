package com.example.myapplication


import com.example.myapplication.subscription.RepositoryPremium
import com.example.myapplication.subscription.ViewModelPremium
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val allModules = module {


    single { RepositoryPremium(get()) }


}
val viewModelModule = module {


    viewModel {

        ViewModelPremium(get(), get())
    }


}