package ua.com.radiokot.lnaddr2invoice.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.view.MainViewModel

val viewModelModules: List<Module> = listOf(
    module {
        viewModel {
            MainViewModel(
                authorTipAddress = getProperty("authorTipAddress")
            )
        }
    }
)