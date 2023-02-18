package ua.com.radiokot.lnaddr2invoice.di

import android.content.Context
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.R
import java.text.DecimalFormat
import java.util.*

enum class InjectedAmountFormat {
    SAT,
    DEFAULT,
    ;
}

val formatModules: List<Module> = listOf(

    // Amount.
    module {
        factory(named(InjectedAmountFormat.SAT)) {
            DecimalFormat.getInstance(Locale.getDefault()).apply {
                (this as DecimalFormat).positiveSuffix = " " + get<Context>().getString(
                    R.string.sat
                )
            }
        }

        factory(named(InjectedAmountFormat.DEFAULT)) {
            DecimalFormat.getInstance(Locale.getDefault())
        }
    }
)