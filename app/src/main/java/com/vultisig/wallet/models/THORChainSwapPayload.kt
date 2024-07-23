package com.vultisig.wallet.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.common.toJson
import wallet.core.jni.proto.THORChainSwap.Asset
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.BigInteger

internal data class THORChainSwapPayload(
    @SerializedName("fromAddress")
    val fromAddress: String,
    @SerializedName("fromCoin")
    val fromCoin: Coin,
    @SerializedName("toCoin")
    val toCoin: Coin,
    @SerializedName("vaultAddress")
    val vaultAddress: String,
    @SerializedName("routerAddress")
    val routerAddress: String?,
    @SerializedName("fromAmount")
    val fromAmount: BigInteger,
    @SerializedName("toAmountDecimal")
    val toAmountDecimal: BigDecimal,
    @SerializedName("toAmountLimit")
    val toAmountLimit: String,
    @SerializedName("steamingInterval")
    val steamingInterval: String,
    @SerializedName("streamingQuantity")
    val streamingQuantity: String,
    @SerializedName("expirationTime")
    val expirationTime: ULong,
    @SerializedName("isAffiliate")
    val isAffiliate: Boolean,
) {
    val toAddress: String
        get() = toCoin.address

    val fromAsset: Asset
        get() = swapAsset(fromCoin, true)
    val toAsset: Asset
        get() = swapAsset(toCoin, false)

    private fun swapAsset(coin: Coin, source: Boolean): Asset {
        val asset = Asset.newBuilder()
            .setSymbol(coin.ticker)
        when (coin.chain) {
            Chain.thorChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.THOR)
            Chain.mayaChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.THOR)
            Chain.ethereum -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.ETH)
            Chain.avalanche -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.AVAX)
            Chain.bscChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.BSC)
            Chain.bitcoin -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.BTC)
            Chain.bitcoinCash -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.BCH)
            Chain.litecoin -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.LTC)
            Chain.dogecoin -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.DOGE)
            Chain.gaiaChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.ATOM)
            else -> throw Exception("Unsupported chain")
        }
        if (!coin.isNativeToken) {
            asset.setTokenId(if (source) coin.contractAddress else "${coin.ticker}-${coin.contractAddress}")
        }
        return asset.build()
    }
}

internal class THORChainSwapPayloadSerializer : JsonSerializer<THORChainSwapPayload> {
    override fun serialize(
        src: THORChainSwapPayload?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        val jsonObject = src?.let {
            val jsonObject = JsonObject()
            jsonObject.addProperty("fromAddress", it.fromAddress)
            jsonObject.add("fromCoin", context?.serialize(it.fromCoin))
            jsonObject.add("toCoin", context?.serialize(it.toCoin))
            jsonObject.addProperty("vaultAddress", it.vaultAddress)
            jsonObject.addProperty("routerAddress", it.routerAddress)
            jsonObject.add("fromAmount", it.fromAmount.toJson())
            jsonObject.addProperty("toAmountDecimal", it.toAmountDecimal)
            jsonObject.addProperty("toAmountLimit", it.toAmountLimit)
            jsonObject.addProperty("streamingInterval", it.steamingInterval)
            jsonObject.addProperty("streamingQuantity", it.streamingQuantity)
            jsonObject.addProperty("expirationTime", it.expirationTime.toLong())
            jsonObject.addProperty("isAffiliate", it.isAffiliate)
            return jsonObject
        }
        return jsonObject ?: JsonObject()
    }
}

internal class THORChainSwapPayloadDeserializer : JsonDeserializer<THORChainSwapPayload> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): THORChainSwapPayload {
        val jsonObject = json.asJsonObject
        val fromAddress = jsonObject.get("fromAddress")?.asString ?: ""
        val fromCoin = context.deserialize<Coin>(jsonObject.get("fromCoin"), Coin::class.java)
        val toCoin = context.deserialize<Coin>(jsonObject.get("toCoin"), Coin::class.java)
        val vaultAddress = jsonObject.get("vaultAddress")?.asString ?: ""
        val routerAddress = jsonObject.get("routerAddress")?.asString ?: ""
        val fromAmount =
            jsonObject.get("fromAmount")?.asJsonArray?.get(1)?.asBigInteger ?: BigInteger.ZERO
        val toAmountDecimal = jsonObject.get("toAmountDecimal")?.asBigDecimal ?: BigDecimal.ZERO
        val toAmountLimit = jsonObject.get("toAmountLimit")?.asString ?: ""
        val steamingInterval = jsonObject.get("steamingInterval")?.asString ?: ""
        val streamingQuantity = jsonObject.get("streamingQuantity")?.asString ?: ""
        val expirationTime = jsonObject.get("expirationTime")?.asLong?.toULong() ?: 0u
        return THORChainSwapPayload(
            fromAddress,
            fromCoin,
            toCoin,
            vaultAddress,
            routerAddress,
            fromAmount,
            toAmountDecimal,
            toAmountLimit,
            steamingInterval,
            streamingQuantity,
            expirationTime,
            jsonObject.get("isAffiliate")?.asBoolean ?: false
        )
    }

}
