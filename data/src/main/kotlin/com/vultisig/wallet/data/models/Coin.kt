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

/** True if the coin represents a liquidity-pool position rather than a plain token. */
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

/** Returns true if this coin's chain allows zero-gas transactions. */
fun Coin.allowZeroGas(): Boolean {
    return this.chain == Chain.Polkadot || this.chain == Chain.Bittensor || this.chain == Chain.Tron
}

/** Returns the ticker without the LP "X/" prefix, uppercased. */
fun Coin.getNotNativeTicker(): String {
    return this.ticker.uppercase().removePrefix("X/")
}

/** Returns true if this coin is a THORChain secured-asset token (on the THORChain side). */
fun Coin.isSecuredAsset(): Boolean {
    if (chain != Chain.ThorChain) return false
    if (isNativeToken) return false
    if (contractAddress.startsWith("x/", ignoreCase = true)) return false
    val parts = contractAddress.split("-", limit = 2)
    return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
}

/** Returns true if this coin can be deposited into THORChain as a SECURE+ asset. */
fun Coin.isSecuredAssetEligible(): Boolean {
    val eligibleTickers = listOf("BTC", "ETH", "BCH", "LTC", "DOGE", "AVAX", "BNB")
    return eligibleTickers.contains(ticker.uppercase()) &&
        (isNativeToken || contractAddress == "${ticker.lowercase()}-${ticker.lowercase()}")
}

/** Returns the chain portion of a secured-asset contract address, uppercased. */
fun Coin.securedAssetChain(): String {
    return contractAddress.substringBefore("-").uppercase()
}

/** Returns the symbol portion of a secured-asset contract address, uppercased. */
fun Coin.securedAssetSymbol(): String {
    return contractAddress.substringAfter("-").uppercase()
}

/** Returns the THORSwap-formatted asset name (e.g. "ETH.USDC-0xA0b..." or "THOR.RUNE"). */
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
        } else if (chain == Chain.ThorChain) {
            if (contractAddress.contains(Regex("""^\w+-\w+$"""))) contractAddress
            else "${chain.swapAssetName()}.${ticker}"
        } else {
            "${chain.swapAssetName()}.${ticker}-${contractAddress}"
        }
    }

/**
 * Normalizes [swapAssetName] for same-asset identity comparisons. EVM contract addresses are
 * lowercased to handle EIP-55 checksum-casing differences from QR payloads. Non-EVM chains (e.g.
 * Cosmos ibc/, Kujira factory/) use case-sensitive canonical forms as returned by the THORChain API
 * and are not altered.
 */
fun Coin.swapAssetComparisonName(): String {
    val name = swapAssetName()
    return if (chain.standard == TokenStandard.EVM) name.lowercase() else name
}
