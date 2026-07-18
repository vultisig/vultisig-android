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
            Chain.ThorChain -> {
                // Compare on the lowercased denom so a persisted coin with non-canonical casing
                // (e.g. a pre-canonicalization "x/BRUNE") is still recognized as an LP position and
                // excluded from the swap pickers, matching the lowercase pricing path.
                val addr = contractAddress.lowercase()
                addr.startsWith("x/staking-") ||
                    addr.startsWith("x/nami-index-") ||
                    addr == "x/brune"
            }
            Chain.MayaChain ->
                contractAddress.startsWith("x/bow-") ||
                    contractAddress.startsWith("x/ghost-vault/") ||
                    contractAddress.startsWith("x/staking-") ||
                    contractAddress.startsWith("x/nami-index-")
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

/**
 * A dedup/list-key identity, distinct from the persisted [Coin.id]. Secured assets on different
 * underlying chains can share a ticker (e.g. `ETH.USDC` and `AVAX.USDC` both have ticker `USDC`),
 * which would otherwise collide on `id` (always `<ticker>-thorchain`) and silently drop one from
 * catalogs/lists keyed on it.
 */
val Coin.catalogKey: String
    get() = if (isSecuredAsset()) "$id-$contractAddress" else id

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

/**
 * Returns the THORSwap-formatted asset name (e.g. "ETH.USDC-0xA0b..." or "THOR.RUNE") used as the
 * wire value for swap quote/tx requests. THORChain secured assets are the exception: Thornode's
 * quote endpoint expects the raw denom verbatim (e.g. "eth-usdc-0xa0b8...", matching
 * [contractAddress] and iOS's `Coin.swapAsset`), not a dot-normalized form — see
 * [swapAssetComparisonName] for the dot form still needed for same-asset comparisons.
 */
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
            if (isSecuredAsset()) {
                contractAddress
            } else {
                "${chain.swapAssetName()}.${ticker}"
            }
        } else {
            "${chain.swapAssetName()}.${ticker}-${contractAddress}"
        }
    }

private val EVM_TAIL_REGEX = Regex("""^(.+)-(0x[0-9a-fA-F]+)$""")

/**
 * Builds the Thornode-accepted secured-asset name (e.g. `BTC.BTC`, `ETH.USDC-0xa0b8...`) from a raw
 * `contractAddress` of the form `<chain>-<symbol>[-<0xHex>]`. The EVM hex tail is preserved
 * verbatim so EIP-55 checksum casing survives the round-trip; chain and symbol parts are uppercased
 * to match the Thornode native-swap-asset convention.
 */
private fun Coin.thorChainSecuredAssetSwapName(): String {
    val chainPart = securedAssetChain()
    val rest = contractAddress.substringAfter("-")
    val evmTail = EVM_TAIL_REGEX.find(rest)
    val tail =
        if (evmTail != null) {
            "${evmTail.groupValues[1].uppercase()}-${evmTail.groupValues[2]}"
        } else {
            rest.uppercase()
        }
    return "$chainPart.$tail"
}

/**
 * Normalizes this coin's asset name for same-asset identity comparisons (e.g. guarding against
 * swapping a native L1 asset into its own THORChain-secured form). Uses the dot-normalized Thornode
 * form ([thorChainSecuredAssetSwapName]) for THORChain secured assets rather than [swapAssetName]'s
 * raw wire value, so a secured coin's identity matches its native-chain counterpart's (e.g.
 * "eth.usdc-0xa0b8..." on both sides). EVM contract addresses are lowercased to handle EIP-55
 * checksum-casing differences from QR payloads; THORChain secured assets are also lowercased so the
 * THORChain side (`THORCHAIN` standard) matches its native EVM counterpart. Other non-EVM chains
 * (e.g. Cosmos ibc/, Kujira factory/) use case-sensitive canonical forms as returned by the
 * THORChain API and are not altered.
 */
fun Coin.swapAssetComparisonName(): String {
    val name =
        if (chain == Chain.ThorChain && isSecuredAsset()) thorChainSecuredAssetSwapName()
        else swapAssetName()
    val needsLowercase =
        chain.standard == TokenStandard.EVM || (chain == Chain.ThorChain && isSecuredAsset())
    return if (needsLowercase) name.lowercase() else name
}
