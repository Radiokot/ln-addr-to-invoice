package ua.com.radiokot.lnaddr2invoice

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import java.math.BigDecimal

class UsernameInfoTest {
    @Test
    fun fromResponse() {
        val vectors = listOf(
            "{\"callback\":\"https://livingroomofsatoshi.com/api/v1/lnurl/payreq/2adffa0b-67c1-4b50-a5be-4994b488efb1\",\"maxSendable\":100000000000,\"minSendable\":1000,\"metadata\":\"[[\\\"text/plain\\\",\\\"Pay to Wallet of Satoshi user: liquidtarget16\\\"],[\\\"text/identifier\\\",\\\"liquidtarget16@walletofsatoshi.com\\\"]]\",\"commentAllowed\":32,\"tag\":\"payRequest\",\"allowsNostr\":true,\"nostrPubkey\":\"be1d89794bf92de5dd64c1e60f6a2c70c140abac9932418fee30c5c637fe9479\"}"
                    to UsernameInfo(
                callbackUrl = "https://livingroomofsatoshi.com/api/v1/lnurl/payreq/2adffa0b-67c1-4b50-a5be-4994b488efb1",
                minSendableSat = BigDecimal.ONE,
                maxSendableSat = BigDecimal(100000000),
                description = "Pay to Wallet of Satoshi user: liquidtarget16",
            ),
            "{\"status\":\"OK\",\"tag\":\"payRequest\",\"commentAllowed\":255,\"callback\":\"https://getalby.com/lnurlp/radiokot/callback\",\"metadata\":\"[[\\\"text/identifier\\\",\\\"radiokot@getalby.com\\\"],[\\\"text/plain\\\",\\\"Sats for radiokot\\\"]]\",\"minSendable\":1000,\"maxSendable\":11000000000,\"payerData\":{\"name\":{\"mandatory\":false},\"email\":{\"mandatory\":false}}}"
                    to UsernameInfo(
                callbackUrl = "https://getalby.com/lnurlp/radiokot/callback",
                minSendableSat = BigDecimal.ONE,
                maxSendableSat = BigDecimal(11000000),
                description = "Sats for radiokot",
            )
        )
        val mapper = jacksonObjectMapper()

        vectors.forEach { (json, expected) ->
            val parsed = UsernameInfo.fromResponse(mapper.readTree(json), mapper)

            Assert.assertEquals(
                expected.callbackUrl,
                parsed.callbackUrl
            )
            Assert.assertEquals(
                "Min sendable amount must match",
                expected.minSendableSat.compareTo(parsed.minSendableSat),
                0
            )
            Assert.assertEquals(
                "Max sendable amount must match",
                expected.maxSendableSat.compareTo(parsed.maxSendableSat),
                0
            )
            Assert.assertEquals(
                expected.description,
                parsed.description
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun fromResponseNoCallback() {
        val mapper = jacksonObjectMapper()
        UsernameInfo.fromResponse(
            mapper.readTree(
                "{\"maxSendable\":100000000000,\"minSendable\":1000,\"metadata\":\"[[\\\"text/plain\\\",\\\"Pay to Wallet of Satoshi user: liquidtarget16\\\"],[\\\"text/identifier\\\",\\\"liquidtarget16@walletofsatoshi.com\\\"]]\",\"commentAllowed\":32,\"tag\":\"payRequest\",\"allowsNostr\":true,\"nostrPubkey\":\"be1d89794bf92de5dd64c1e60f6a2c70c140abac9932418fee30c5c637fe9479\"}"
            ), mapper
        )
    }

    @Test(expected = IllegalStateException::class)
    fun fromResponseNoAmounts() {
        val mapper = jacksonObjectMapper()
        UsernameInfo.fromResponse(
            mapper.readTree(
                "{\"callback\":\"https://livingroomofsatoshi.com/api/v1/lnurl/payreq/2adffa0b-67c1-4b50-a5be-4994b488efb1\",\"metadata\":\"[[\\\"text/plain\\\",\\\"Pay to Wallet of Satoshi user: liquidtarget16\\\"],[\\\"text/identifier\\\",\\\"liquidtarget16@walletofsatoshi.com\\\"]]\",\"commentAllowed\":32,\"tag\":\"payRequest\",\"allowsNostr\":true,\"nostrPubkey\":\"be1d89794bf92de5dd64c1e60f6a2c70c140abac9932418fee30c5c637fe9479\"}"
            ), mapper
        )
    }

    @Test(expected = IllegalStateException::class)
    fun fromResponseNoDescription() {
        val mapper = jacksonObjectMapper()
        UsernameInfo.fromResponse(
            mapper.readTree(
                "{\"callback\":\"https://livingroomofsatoshi.com/api/v1/lnurl/payreq/2adffa0b-67c1-4b50-a5be-4994b488efb1\",\"maxSendable\":100000000000,\"minSendable\":1000,\"metadata\":\"[[\\\"text/identifier\\\",\\\"liquidtarget16@walletofsatoshi.com\\\"]]\",\"commentAllowed\":32,\"tag\":\"payRequest\",\"allowsNostr\":true,\"nostrPubkey\":\"be1d89794bf92de5dd64c1e60f6a2c70c140abac9932418fee30c5c637fe9479\"}"
            ), mapper
        )
    }
}