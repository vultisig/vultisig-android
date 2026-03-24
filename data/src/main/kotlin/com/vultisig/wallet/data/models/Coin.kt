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

fun Coin.isSecuredAsset(): Boolean {
    if (chain != Chain.ThorChain) return false
    if (isNativeToken) return false
    if (contractAddress.startsWith("x/")) return false
    return contractAddress.contains("-")
}

fun Coin.securedAssetChain(): String {
    val chain = contractAddress.substringBefore("-")
    return chain.ifEmpty { "THOR" }.uppercase()
}

/**
 * Returns the symbol from the secured asset denom (e.g., "eth-usdc-0xa0b..." →
 * "USDC-0xA0B86991...")
 */
fun Coin.securedAssetSymbol(): String {
    val symbol = contractAddress.substringAfter("-")
    return symbol.ifEmpty { ticker }.uppercase()
}
