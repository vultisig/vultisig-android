package com.vultisig.wallet.data.models

/**
 * A THORChain pool asset: the wire string thornode identifies it by (`BTC.BTC`,
 * `ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48`) together with the wallet [Coin] it
 * resolves to.
 *
 * Both halves are needed: [asset] is what a THORName memo carries verbatim — thornode matches it
 * against its own pool table, so it must survive the round-trip unaltered — while [coin] is what
 * the UI renders and what the vault enables so payouts in it become visible.
 */
data class ThorChainPoolCoin(val asset: String, val coin: Coin) {

    companion object {
        /** THORChain accounts in 8 decimals; used when a pool reports no precision of its own. */
        private const val DEFAULT_DECIMALS = 8

        /**
         * Resolves a pool asset id into a [ThorChainPoolCoin], or null when the app has no chain
         * for it (thornode lists pools this wallet cannot hold, e.g. a chain it doesn't support
         * yet).
         *
         * [decimals] comes from the pool entry, which only reports it when the asset's precision
         * differs from THORChain's own; it is used solely for assets missing from [Coins].
         */
        fun from(asset: String, decimals: Int? = null): ThorChainPoolCoin? {
            val chainCode = asset.substringBefore('.', missingDelimiterValue = "")
            if (chainCode.isEmpty()) return null

            val tail = asset.substringAfter('.', missingDelimiterValue = "")
            val ticker: String
            val contractAddress: String
            when {
                tail.isEmpty() -> {
                    ticker = chainCode
                    contractAddress = ""
                }
                tail.contains('-') -> {
                    ticker = tail.substringBefore('-')
                    contractAddress = tail.substringAfter('-')
                }
                else -> {
                    ticker = tail
                    // THORChain's own non-native assets are denominated by their lowercased
                    // ticker (`THOR.TCY` is held as `tcy`), every other chain identifies a token
                    // by contract address, which a dash-less pool id does not carry.
                    contractAddress =
                        if (chainCode.equals(THORCHAIN_CODE, ignoreCase = true)) tail.lowercase()
                        else ""
                }
            }
            if (ticker.isEmpty()) return null

            val chain =
                Chain.entries.firstOrNull {
                    it.swapAssetName().equals(chainCode, ignoreCase = true)
                } ?: return null

            val known = Coins.coins[chain].orEmpty()
            // A pool id that spells out a contract address names one exact token, so a registry
            // coin sharing only the ticker is a different token: enabling it would put the wrong
            // contract and precision in the vault. The THORChain denom above is a guess made from
            // the ticker rather than something the pool id carries, so it keeps the fallback.
            val isTickerFallbackSound =
                contractAddress.isEmpty() || chainCode.equals(THORCHAIN_CODE, ignoreCase = true)
            val coin =
                known.firstOrNull {
                    contractAddress.isNotEmpty() &&
                        it.contractAddress.equals(contractAddress, ignoreCase = true)
                }
                    ?: known.firstOrNull {
                        isTickerFallbackSound && it.ticker.equals(ticker, ignoreCase = true)
                    }
                    ?: Coin(
                        chain = chain,
                        ticker = ticker.uppercase(),
                        logo = ticker.lowercase(),
                        address = "",
                        decimal = decimals ?: DEFAULT_DECIMALS,
                        hexPublicKey = "",
                        priceProviderID = "",
                        contractAddress = contractAddress,
                        isNativeToken = contractAddress.isEmpty(),
                    )

            return ThorChainPoolCoin(asset = asset, coin = coin)
        }

        private const val THORCHAIN_CODE = "THOR"
    }
}
