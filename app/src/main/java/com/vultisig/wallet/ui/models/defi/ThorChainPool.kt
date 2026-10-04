package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain

internal data class ThorChainPoolAsset(
    val chain: Chain?,
    val ticker: String,
    val contractAddress: String,
)

/**
 * Parses a THORChain pool identifier of the form `CHAIN.TICKER` or `CHAIN.TICKER-CONTRACT` (e.g.
 * `BTC.BTC`, `ETH.USDC-0xA0b86991C6218B36c1d19D4a2e9Eb0cE3606eB48`) into its parts.
 */
internal fun parseThorChainPool(pool: String): ThorChainPoolAsset {
    val prefix = pool.substringBefore(".", missingDelimiterValue = "")
    val rest = pool.substringAfter(".", missingDelimiterValue = pool)
    val ticker = rest.substringBefore("-")
    val contractAddress = rest.substringAfter("-", missingDelimiterValue = "")
    return ThorChainPoolAsset(
        chain = thorPoolChainPrefixToChain(prefix),
        ticker = ticker,
        contractAddress = contractAddress,
    )
}

internal fun thorPoolChainPrefixToChain(prefix: String): Chain? =
    when (prefix.uppercase()) {
        "BTC" -> Chain.Bitcoin
        "BCH" -> Chain.BitcoinCash
        "LTC" -> Chain.Litecoin
        "DOGE" -> Chain.Dogecoin
        "ETH" -> Chain.Ethereum
        "AVAX" -> Chain.Avalanche
        "BSC" -> Chain.BscChain
        "BASE" -> Chain.Base
        "GAIA" -> Chain.GaiaChain
        "THOR" -> Chain.ThorChain
        "SOL" -> Chain.Solana
        "TRON" -> Chain.Tron
        "XRP" -> Chain.Ripple
        else -> null
    }

// Every prefix `/mayachain/pools` currently returns as Available. A prefix missing here resolves to
// no chain, which leaves the pool's asset leg priced at zero and halves the card and header totals.
internal fun mayaPoolChainPrefixToChain(prefix: String): Chain? =
    when (prefix.uppercase()) {
        "BTC" -> Chain.Bitcoin
        "ETH" -> Chain.Ethereum
        "DASH" -> Chain.Dash
        "MAYA" -> Chain.MayaChain
        "BASE" -> Chain.Base
        "ARB" -> Chain.Arbitrum
        "AVAX" -> Chain.Avalanche
        "BSC" -> Chain.BscChain
        "THOR" -> Chain.ThorChain
        "ADA" -> Chain.Cardano
        "ZEC" -> Chain.Zcash
        else -> null
    }

/**
 * The MayaChain pool each chain's native coin deposits into, for the chains whose asset side of a
 * Maya LP add the app can sign: a plain native transfer to the Maya inbound vault carrying the
 * memo. ERC-20 pools need the router and are not covered.
 */
internal val MAYA_NATIVE_LP_POOLS: Map<Chain, String> =
    mapOf(
        Chain.Bitcoin to "BTC.BTC",
        Chain.Ethereum to "ETH.ETH",
        Chain.Arbitrum to "ARB.ETH",
        Chain.Dash to "DASH.DASH",
        Chain.Zcash to "ZEC.ZEC",
    )

/**
 * The chain whose vault address a MayaChain LP memo names as its paired side when [chain] signs the
 * add into [pool], or null when the add is single-sided.
 *
 * Only pools in [MAYA_NATIVE_LP_POOLS] pair: those are the pools whose other half the app can
 * deposit, so a paired CACAO add there can be completed. A paired CACAO add anywhere else would sit
 * pending on MayaChain until an asset deposit the app cannot make, so it stays `+:POOL`.
 */
internal fun mayaLpPairedChain(chain: Chain, pool: String): Chain? {
    val assetChain =
        MAYA_NATIVE_LP_POOLS.entries.firstOrNull { it.value.equals(pool, ignoreCase = true) }?.key
            ?: return null
    return when (chain) {
        Chain.MayaChain -> assetChain
        assetChain -> Chain.MayaChain
        else -> null
    }
}
