package ua.com.radiokot.lnaddr2invoice.integration.logic

import org.junit.Assert
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.test.KoinTest
import org.koin.test.get
import ua.com.radiokot.lnaddr2invoice.di.commonModules
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import java.math.BigInteger

class GetUsernameInfoUseCaseTest: KoinTest {
    @Test
    fun perform() {
        startKoin {
            modules(
                commonModules
            )
        }

        val usernameInfo = GetUsernameInfoUseCase(
            address = "liquidtarget16@walletofsatoshi.com",
            httpClient = get(),
            jsonObjectMapper = get(),
        )
            .perform()
            .blockingGet()

        println(usernameInfo)
        Assert.assertEquals(
            0,
            usernameInfo.minSendableSat.compareTo(BigInteger.ONE),
        )
    }
}
