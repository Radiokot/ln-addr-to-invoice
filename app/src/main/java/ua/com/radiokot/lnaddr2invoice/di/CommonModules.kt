package ua.com.radiokot.lnaddr2invoice.di

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.Module
import org.koin.dsl.module
import ua.com.radiokot.lnaddr2invoice.base.util.http.HttpExceptionInterceptor
import ua.com.radiokot.lnaddr2invoice.base.view.ToastManager
import java.util.concurrent.TimeUnit

val commonModules: List<Module> = listOf(
    // JSON
    module {
        single<ObjectMapper> {
            jacksonObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    },

    // HTTP
    module {
        fun getLoggingInterceptor(): HttpLoggingInterceptor {
            val logger = KotlinLogging.logger("HTTP")
            return HttpLoggingInterceptor(logger::info).apply {
                level =
                    if (logger.isDebugEnabled)
                        HttpLoggingInterceptor.Level.BODY
                    else
                        HttpLoggingInterceptor.Level.BASIC
            }
        }

        fun getDefaultBuilder(): OkHttpClient.Builder {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpExceptionInterceptor())
        }

        single {
            getDefaultBuilder()
                .addInterceptor(getLoggingInterceptor())
                .build()
        }
    },

    // Toast
    module {
        factory {
            ToastManager(
                context = get()
            )
        }
    },
)