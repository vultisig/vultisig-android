package com.vultisig.wallet.data.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.common.toJson
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.models.Coin
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.BigInteger

internal data class OneInchSwapPayloadJson(
    @SerializedName("fromCoin")
    val fromCoin: Coin,
    @SerializedName("toCoin")
    val toCoin: Coin,
    @SerializedName("fromAmount")
    val fromAmount: BigInteger,
    @SerializedName("toAmountDecimal")
    val toAmountDecimal: BigDecimal,
    @SerializedName("quote")
    val quote: OneInchSwapQuoteJson,
)

internal class OneInchSwapPayloadJsonSerializer : JsonSerializer<OneInchSwapPayloadJson> {
    override fun serialize(
        src: OneInchSwapPayloadJson?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement = src?.let {
        val jsonObject = JsonObject()
        jsonObject.add("fromCoin", context?.serialize(it.fromCoin))
        jsonObject.add("toCoin", context?.serialize(it.toCoin))
        jsonObject.add("fromAmount", it.fromAmount.toJson())
        jsonObject.addProperty("toAmountDecimal", it.toAmountDecimal)
        return jsonObject
    } ?: JsonObject()
}

internal class OneInchSwapPayloadJsonDeserializer : JsonDeserializer<OneInchSwapPayloadJson> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): OneInchSwapPayloadJson {
        val jsonObject = json.asJsonObject
        val fromCoin = context.deserialize<Coin>(jsonObject.get("fromCoin"), Coin::class.java)
        val toCoin = context.deserialize<Coin>(jsonObject.get("toCoin"), Coin::class.java)
        val fromAmount =
            jsonObject.get("fromAmount")?.asJsonArray?.get(1)?.asBigInteger ?: BigInteger.ZERO
        val toAmountDecimal = jsonObject.get("toAmountDecimal")?.asBigDecimal ?: BigDecimal.ZERO
        val quote = context.deserialize<OneInchSwapQuoteJson>(
            jsonObject.get("quote"),
            OneInchSwapQuoteJson::class.java
        )

        return OneInchSwapPayloadJson(
            fromCoin,
            toCoin,
            fromAmount,
            toAmountDecimal,
            quote
        )
    }

}