package com.voltix.wallet.models

import wallet.core.jni.proto.THORChainSwap.Asset
import java.math.BigInteger

data class THORChainSwapPayload(
    val fromAddress: String,
    val fromCoin: Coin,
    val toCoin: Coin,
    val vaultAddress: String,
    val routerAddress: String,
    val fromAmount: BigInteger,
    val toAmount: BigInteger,
    val toAmountLimit: String,
    val steamingInterval: String,
    val streamingQuantity: String,
    val expirationTime: ULong,
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
            asset.setTokenId(if (source) coin.contractAddress else "${coin.address}-${coin.contractAddress}")
        }
        return asset.build()
    }
}