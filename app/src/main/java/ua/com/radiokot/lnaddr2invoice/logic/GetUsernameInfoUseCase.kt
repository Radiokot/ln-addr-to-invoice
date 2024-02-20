package ua.com.radiokot.lnaddr2invoice.logic

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.com.radiokot.lnaddr2invoice.base.extension.checkNotNull
import ua.com.radiokot.lnaddr2invoice.base.extension.toSingle
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase.AddressIsAnInvoiceException
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo

/**
 * Gets [UsernameInfo] for the given [address]
 *
 * @see [AddressIsAnInvoiceException]
 * @see <a href="https://github.com/andrerfneves/lightning-address/blob/master/DIY.md">Lightning Address username info response</a>
 */
class GetUsernameInfoUseCase(
    val address: String,
    val httpClient: OkHttpClient,
    val jsonObjectMapper: ObjectMapper,
) {
    private lateinit var username: String
    private lateinit var host: String

    fun perform(): Single<UsernameInfo> {
        return getUsernameAndHost()
            .onErrorResumeNext { error ->
                if (error is IllegalArgumentException && address.startsWith(INVOICE_PREFIX_MAINNET))
                    Single.error(AddressIsAnInvoiceException())
                else
                    Single.error(error)
            }
            .doOnSuccess { pair ->
                username = pair.first
                host = pair.second
            }
            .flatMap { getUsernameInfo() }
    }

    private fun getUsernameAndHost(): Single<Pair<String, String>> = {
        require(address.contains('@')) {
            "The address is not an internet identifier"
        }
        require(address.length <= 320) {
            "The address exceeds the maximum internet identifier length"
        }

        val username = address.substringBefore('@', "")
        require(username.isNotEmpty()) {
            "The address has no username"
        }

        val host = address.substringAfter('@', "")
        require(host.isNotEmpty()) {
            "The address has no host"
        }

        username to host
    }.toSingle()

    private fun getUsernameInfo(): Single<UsernameInfo> = {
        val request = Request.Builder()
            .get()
            // HTTPS is expected to be used, since this is sensitive data,
            // where MITM impacts actual money.
            .url("https://$host/.well-known/lnurlp/$username")
            .build()

        val response = httpClient
            .newCall(request)
            .execute()

        check(response.isSuccessful)

        val body = response.body
            .checkNotNull {
                "The username info response has no body"
            }

        UsernameInfo.fromResponse(
            response = jsonObjectMapper.readTree(body.byteStream()),
            jsonObjectMapper = jsonObjectMapper,
        )
    }.toSingle().subscribeOn(Schedulers.io())

    class AddressIsAnInvoiceException: Exception()

    private companion object {
        private const val INVOICE_PREFIX_MAINNET = "lnbc1"
    }
}
