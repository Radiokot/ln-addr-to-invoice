package ua.com.radiokot.lnaddr2invoice.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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

            val callbackUrl = response["callback"]
                ?.asText("")
                ?.takeIf(String::isNotBlank)
            checkNotNull(callbackUrl) {
                "There is no callback URL in the response"
            }

            val sat = BigDecimal(1000)
            val maxSendableSat = response["maxSendable"]
                ?.asLong(0)
                ?.toBigDecimal()
                ?.divide(sat, MathContext.DECIMAL32)
                ?.stripTrailingZeros()
            checkNotNull(maxSendableSat) {
                "There is no max sendable amount in the response"
            }
            val minSendableSat = response["minSendable"]
                ?.asLong(0)
                ?.toBigDecimal()
                ?.divide(sat, MathContext.DECIMAL32)
                ?.stripTrailingZeros()
            checkNotNull(minSendableSat) {
                "There is no min sendable amount in the response"
            }

            val metadataString = response["metadata"]
                ?.asText("")
                ?.takeIf(String::isNotBlank)
            checkNotNull(metadataString) {
                "There is no metadata in the response"
            }

            val metadata = jsonObjectMapper.readTree(metadataString)
            check(metadata.isArray) {
                "The metadata is not an array"
            }

            val description = metadata
                .firstOrNull { it[0].asText() == "text/plain" }
                ?.get(1)
                ?.asText()
                ?.takeIf(String::isNotEmpty)
            checkNotNull(description) {
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