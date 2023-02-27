package ua.com.radiokot.lnaddr2invoice.logic

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.com.radiokot.lnaddr2invoice.base.extension.toSingle
import ua.com.radiokot.lnaddr2invoice.base.util.SAT
import java.math.BigDecimal
import java.math.RoundingMode

class GetBolt11InvoiceUseCase(
    val amountSat: BigDecimal,
    val callbackUrl: String,
    val httpClient: OkHttpClient,
    val jsonObjectMapper: ObjectMapper,
) {
    fun perform(): Single<String> {
        return getInvoice()
    }

    private fun getInvoice(): Single<String> = {
        val request = Request.Builder()
            .get()
            .url(
                callbackUrl.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter(
                        "amount",
                        amountSat
                            .multiply(SAT)
                            .setScale(0, RoundingMode.DOWN)
                            .stripTrailingZeros()
                            .toPlainString()
                    )
                    .build()
            )
            .build()

        val response = httpClient
            .newCall(request)
            .execute()
        check(response.isSuccessful)

        val body = response.body
        checkNotNull(body) {
            "The invoice response has no body"
        }

        val json = jsonObjectMapper.readTree(body.byteStream())

        val invoice = json["pr"]
            ?.asText("")
            ?.takeIf(String::isNotEmpty)
        checkNotNull(invoice) {
            "There is no invoice in the response"
        }

        invoice.toString()
    }.toSingle().subscribeOn(Schedulers.io())
}