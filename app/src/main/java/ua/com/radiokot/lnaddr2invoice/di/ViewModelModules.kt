package ua.com.radiokot.lnaddr2invoice.di

import android.content.ClipboardManager
import android.content.Context
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.base.extension.getNumericProperty
import ua.com.radiokot.lnaddr2invoice.view.MainViewModel

val viewModelModules: List<Module> = listOf(
    module {
        viewModel {
            MainViewModel(
                authorTipAddress = getProperty("authorTipAddress"),
                createdInvoicesCounter = get(),
                tipStateStorage = get(),
                tipEveryNthInvoice = getNumericProperty("tipAfterNthInvoice", Int.MAX_VALUE),
                clipboardManager = androidApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
            )
        }
    }
)
