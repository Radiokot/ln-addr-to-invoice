package ua.com.radiokot.lnaddr2invoice.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.logic.GetBolt11InvoiceUseCase
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import java.math.BigInteger

val useCaseModules: List<Module> = listOf(
    module {
        factory { (address: String) ->
            GetUsernameInfoUseCase(
                address = address,
                httpClient = get(),
                jsonObjectMapper = get()
            )
        }

        factory { (amountSat: BigInteger, callbackUrl: String) ->
            GetBolt11InvoiceUseCase(
                amountSat = amountSat,
                callbackUrl = callbackUrl,
                httpClient = get(),
                jsonObjectMapper = get()
            )
        }
    }
)
