package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.isSecuredAsset

/**
 * Number of trailing contract-address characters a THORChain memo uses to disambiguate a token from
 * others sharing its ticker (`ETH.USDC-06EB48`).
 *
 * Deliberately NOT the full contract address: memo bytes are scarce (UTXO sources cap at 80 bytes),
 * and THORChain resolves the abbreviated form against its pool list.
 */
private const val CONTRACT_SUFFIX_LENGTH = 6

/**
 * THORChain memo-asset chain prefix (`BTC`, `ETH`, `THOR`, …) for every chain routable through
 * THORChain.
 *
 * Limit swaps (`=<`) share THORChain's regular-swap chain universe — `=` vs `=<` only selects
 * execution behavior (price/queue/TTL), not a different set of chains.
 *
 * This is a dedicated table rather than a reuse of [com.vultisig.wallet.data.models.swapAssetName]:
 * that helper maps [Chain.Noble] to `USDC` (Noble's fee ticker), whereas THORChain routes Noble
 * under the `NOBLE` prefix. The memo *is* the order, so the prefix must be THORChain's, not the fee
 * ticker's. Chains absent from this table (MayaChain, Cardano, Optimism, Polygon, …) are not
 * THORChain-routable and must fail before a memo is built.
 */
val thorchainMemoAssetChainPrefix: Map<Chain, String> =
    mapOf(
        Chain.Avalanche to "AVAX",
        Chain.Base to "BASE",
        Chain.BitcoinCash to "BCH",
        Chain.BscChain to "BSC",
        Chain.Bitcoin to "BTC",
        Chain.Dash to "DASH",
        Chain.Dogecoin to "DOGE",
        Chain.Ethereum to "ETH",
        Chain.GaiaChain to "GAIA",
        Chain.Kujira to "KUJI",
        Chain.Litecoin to "LTC",
        Chain.ThorChain to "THOR",
        Chain.Tron to "TRON",
        Chain.Ripple to "XRP",
        Chain.Arbitrum to "ARB",
        Chain.Zcash to "ZEC",
        Chain.Solana to "SOL",
        Chain.Noble to "NOBLE",
    )

/**
 * Reverse of [thorchainMemoAssetChainPrefix]: THORChain asset prefix → [Chain].
 *
 * Derived by inversion rather than hand-maintained so the two directions cannot drift. Drift here
 * is a fund-safety bug: a prefix one direction accepts and the other rejects either blocks a valid
 * order or builds one that routes somewhere unintended.
 */
val thorchainAssetPrefixToChain: Map<String, Chain> =
    thorchainMemoAssetChainPrefix.entries.associate { (chain, prefix) -> prefix to chain }

/**
 * Whether a chain can be encoded as a THORChain memo asset — i.e. whether it is routable through
 * THORChain at all. Use this to filter coin pickers so a user cannot select a chain that would only
 * fail later, at memo-build time.
 */
fun isThorchainRoutable(chain: Chain): Boolean = thorchainMemoAssetChainPrefix.containsKey(chain)

/** Every chain the memo builder can encode, ignoring live network state. */
val staticLimitSwapSupportedChains: List<Chain> = thorchainMemoAssetChainPrefix.keys.toList()

/**
 * Build the THORChain memo-asset string for a coin — the `source_asset` / `target_asset` a
 * limit-swap memo is built from.
 * - native → `CHAIN.TICKER` (`BTC.BTC`, `THOR.RUNE`; Cosmos native is always `GAIA.ATOM`)
 * - THORChain tokens → `THOR.TICKER` (`THOR.TCY`)
 * - THORChain secured assets → the raw denom (`eth-usdc-0x…`)
 * - any other token → `CHAIN.TICKER-<last 6 of contract, uppercased>` (`ETH.USDC-06EB48`)
 *
 * Throws for chains THORChain cannot route, empty tickers, and contract segments too short to
 * abbreviate — a malformed asset segment must fail here rather than at broadcast time, once funds
 * are committed.
 */
fun Coin.thorchainMemoAsset(): String {
    val prefix =
        requireNotNull(thorchainMemoAssetChainPrefix[chain]) {
            "thorchainMemoAsset: $chain is not routable through THORChain"
        }

    val normalizedTicker = ticker.trim()
    require(normalizedTicker.isNotEmpty()) {
        "thorchainMemoAsset: ticker must be a non-empty string for $chain"
    }

    if (isNativeToken) {
        // Cosmos (GaiaChain) always routes as GAIA.ATOM regardless of the native coin's ticker,
        // matching the market-swap asset notation.
        return if (chain == Chain.GaiaChain) "$prefix.ATOM" else "$prefix.$normalizedTicker"
    }

    if (chain == Chain.ThorChain) {
        // Secured denoms identify the asset by their trailing address; leave them un-abbreviated.
        return if (isSecuredAsset()) contractAddress else "$prefix.$normalizedTicker"
    }

    val contract = contractAddress.trim()
    require(contract.length >= CONTRACT_SUFFIX_LENGTH) {
        "thorchainMemoAsset: contract segment \"$contract\" is shorter than " +
            "$CONTRACT_SUFFIX_LENGTH characters"
    }
    return "$prefix.$normalizedTicker-${contract.takeLast(CONTRACT_SUFFIX_LENGTH).uppercase()}"
}
