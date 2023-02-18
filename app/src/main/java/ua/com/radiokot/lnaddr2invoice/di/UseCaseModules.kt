package ua.com.radiokot.lnaddr2invoice.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase

val useCaseModules: List<Module> = listOf(
    module {
        factory { (address: String) ->
            GetUsernameInfoUseCase(
                address = address,
                httpClient = get(),
                jsonObjectMapper = get()
            )
        }
    }
)