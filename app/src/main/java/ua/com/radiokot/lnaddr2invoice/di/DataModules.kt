package ua.com.radiokot.lnaddr2invoice.di

import android.content.Context
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.data.CreatedInvoicesCounter
import ua.com.radiokot.lnaddr2invoice.data.SharedPrefsCreatedInvoicesCounter
import ua.com.radiokot.lnaddr2invoice.data.SharedPrefsTipStateStorage
import ua.com.radiokot.lnaddr2invoice.data.TipStateStorage

enum class InjectedSharedPreferences {
    NO_BACKUP,
    ;
}

val dataModules: List<Module> = listOf(
    // Preferences.
    module {
        single(named(InjectedSharedPreferences.NO_BACKUP)) {
            get<Context>()
                .getSharedPreferences("nobackup", Context.MODE_PRIVATE)
        }
    },

    module {
        single {
            SharedPrefsCreatedInvoicesCounter(
                sharedPreferences = get(named(InjectedSharedPreferences.NO_BACKUP)),
                key = "created_invoices_count",
            )
        } bind CreatedInvoicesCounter::class

        single {
            SharedPrefsTipStateStorage(
                sharedPreferences = get(named(InjectedSharedPreferences.NO_BACKUP)),
                key = "tip_state",
            )
        } bind TipStateStorage::class
    },
)