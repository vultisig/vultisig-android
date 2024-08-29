package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import wallet.core.jni.proto.THORChainSwap.Asset
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
    val streamingInterval: String,
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
            Chain.Avalanche -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.AVAX)
            Chain.Bitcoin -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.BTC)
            Chain.BitcoinCash -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.BCH)
            Chain.BscChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.BSC)
            Chain.Dogecoin -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.DOGE)
            Chain.Ethereum -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.ETH)
            Chain.GaiaChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.ATOM)
            Chain.Litecoin -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.LTC)
            Chain.MayaChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.THOR)
            Chain.ThorChain -> asset.setChain(wallet.core.jni.proto.THORChainSwap.Chain.THOR)
            else -> throw Exception("Unsupported chain")
        }
        if (!coin.isNativeToken) {
            asset.setTokenId(
                if (source) coin.contractAddress else "${coin.ticker}-${
                    coin.contractAddress.takeLast(
                        6
                    ).uppercase()
                }"
            )
        }
        return asset.build()
    }
}
