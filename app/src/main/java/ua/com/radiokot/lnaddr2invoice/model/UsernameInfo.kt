package ua.com.radiokot.lnaddr2invoice.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ua.com.radiokot.lnaddr2invoice.base.extension.checkNotNull
import ua.com.radiokot.lnaddr2invoice.base.util.SAT
import java.math.BigDecimal
import java.math.MathContext

data class UsernameInfo(
    val callbackUrl: String,
    val maxSendableSat: BigDecimal,
    val minSendableSat: BigDecimal,
    val description: String,
) {
    companion object {
        /**
         * @return a parsed [UsernameInfo] from the JSON response
         *
         * @see <a href="https://github.com/andrerfneves/lightning-address/blob/master/DIY.md">Lightning Address username response</a>
         */
        fun fromResponse(
            response: JsonNode,
            jsonObjectMapper: ObjectMapper,
        ): UsernameInfo {
            check(response.isObject) {
                "The response is not an object"
            }

            val callbackUrl: String = response["callback"]
                ?.asText("")
                ?.takeIf(String::isNotBlank)
                .checkNotNull {
                    "There is no callback URL in the response"
                }

            val maxSendableSat: BigDecimal = response["maxSendable"]
                ?.asLong(0)
                ?.toBigDecimal()
                ?.divide(SAT, MathContext.DECIMAL32)
                ?.stripTrailingZeros()
                .checkNotNull {
                    "There is no max sendable amount in the response"
                }

            val minSendableSat: BigDecimal = response["minSendable"]
                ?.asLong(0)
                ?.toBigDecimal()
                ?.divide(SAT, MathContext.DECIMAL32)
                ?.stripTrailingZeros()
                .checkNotNull {
                    "There is no min sendable amount in the response"
                }

            val metadata: JsonNode = response["metadata"]
                ?.asText("")
                ?.takeIf(String::isNotBlank)
                .checkNotNull {
                    "There is no metadata string in the response"
                }
                .let(jsonObjectMapper::readTree)
                .also {
                    check(it.isArray) {
                        "The metadata is not an array"
                    }
                }

            val description: String = metadata
                .firstOrNull { it[0].asText() == "text/plain" }
                ?.get(1)
                ?.asText()
                ?.takeIf(String::isNotEmpty)
                .checkNotNull {
                    "There is no text/plain description in the metadata"
                }

            return UsernameInfo(
                callbackUrl = callbackUrl,
                maxSendableSat = maxSendableSat,
                minSendableSat = minSendableSat,
                description = description,
            )
        }
    }
}