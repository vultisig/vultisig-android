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
        else -> null
    }
