package ua.com.radiokot.lnaddr2invoice.integration.logic

import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.test.KoinTest
import org.koin.test.get
import ua.com.radiokot.lnaddr2invoice.di.commonModules
import ua.com.radiokot.lnaddr2invoice.logic.GetBolt11InvoiceUseCase
import java.math.BigInteger

class GetBolt11InvoiceUseCaseTest : KoinTest {
    @Test
    fun perform() {
        startKoin {
            modules(
                commonModules
            )
        }

        val invoice = GetBolt11InvoiceUseCase(
            amountSat = BigInteger.valueOf(5),
            callbackUrl = "https://livingroomofsatoshi.com/api/v1/lnurl/payreq/2adffa0b-67c1-4b50-a5be-4994b488efb1",
            jsonObjectMapper = get(),
            httpClient = get(),
        )
            .perform()
            .blockingGet()

        println(invoice)
    }
}
