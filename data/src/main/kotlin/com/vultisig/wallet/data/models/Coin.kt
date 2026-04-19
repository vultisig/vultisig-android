package com.vultisig.wallet.data.models

import java.math.BigDecimal
import wallet.core.jni.CoinType

typealias TokenId = String

data class Coin(
    val chain: Chain,
    val ticker: String,
    val logo: String,
    val address: String,
    val decimal: Int,
    val hexPublicKey: String,
    val priceProviderID: String,
    val contractAddress: String,
    val isNativeToken: Boolean,
    val usdPrice: BigDecimal? = null,
) {
    val id: TokenId
        get() = "${ticker}-${chain.id}"

    val coinType: CoinType
        get() = chain.coinType

    companion object {
        val EMPTY =
            Coin(
                chain = Chain.ThorChain,
                ticker = "",
                logo = "",
                address = "",
                decimal = 0,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "",
                isNativeToken = false,
            )
    }
}

val Coin.isLpToken: Boolean
    get() =
        when (chain) {
            Chain.ThorChain -> contractAddress.startsWith("x/")
            Chain.MayaChain ->
                contractAddress.startsWith("x/bow-") ||
                    contractAddress.startsWith("x/ghost-vault/") ||
                    contractAddress.startsWith("x/staking-") ||
                    contractAddress.startsWith("x/nami-index-") ||
                    contractAddress == "x/brune"
            else -> false
        }

fun Coin.allowZeroGas(): Boolean {
    return this.chain == Chain.Polkadot || this.chain == Chain.Bittensor || this.chain == Chain.Tron
}

fun Coin.getNotNativeTicker(): String {
    return this.ticker.uppercase().removePrefix("X/")
}

// THORChain-side: is this coin a secured asset token on THORChain itself
fun Coin.isSecuredAsset(): Boolean {
    if (chain != Chain.ThorChain) return false
    if (isNativeToken) return false
    if (contractAddress.startsWith("x/", ignoreCase = true)) return false
    val parts = contractAddress.split("-", limit = 2)
    return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
}

// Source-chain side: can this coin be deposited into THORChain as a SECURE+ asset
fun Coin.isSecuredAssetEligible(): Boolean {
    val eligibleTickers = listOf("BTC", "ETH", "BCH", "LTC", "DOGE", "AVAX", "BNB")
    return eligibleTickers.contains(ticker.uppercase()) &&
        (isNativeToken || contractAddress == "${ticker.lowercase()}-${ticker.lowercase()}")
}

fun Coin.securedAssetChain(): String {
    return contractAddress.substringBefore("-").uppercase()
}

fun Coin.securedAssetSymbol(): String {
    return contractAddress.substringAfter("-").uppercase()
}

fun Coin.swapAssetName(): String =
    if (isNativeToken) {
        if (chain == Chain.GaiaChain) {
            "${chain.swapAssetName()}.ATOM"
        } else {
            "${chain.swapAssetName()}.${ticker}"
        }
    } else {
        if (
            chain == Chain.Kujira &&
                (contractAddress.contains("factory/") || contractAddress.contains("ibc/"))
        ) {
            "${chain.swapAssetName()}.${ticker}"
        } else if (chain == Chain.ThorChain)
            if (contractAddress.contains(Regex("""\w+-\w+"""))) contractAddress
            else "${chain.swapAssetName()}.${ticker}"
        else "${chain.swapAssetName()}.${ticker}-${contractAddress}"
    }
