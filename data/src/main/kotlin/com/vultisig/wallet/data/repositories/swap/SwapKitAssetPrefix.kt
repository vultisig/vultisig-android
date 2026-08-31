package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain

/**
 * SwapKit's spelling of a chain in an asset identifier — the `CHAIN` half of
 * `CHAIN.TICKER[-CONTRACT]` — or null for a network its catalogue has no name for here.
 *
 * This map, not a [SwapProviderTable] row, is the one entry a newly supported network needs before
 * SwapKit can be offered on it: with no prefix there is no `sellAsset`/`buyAsset` to send, so every
 * quote for the chain fails whatever `/providers` says about it. [SwapKitCapability.canReceiveOn]
 * gates on it for that reason — offering a provider that cannot form a request is worse than not
 * offering it, because the pair then looks routable until the quote comes back. iOS gets the same
 * guarantee for free by resolving identifiers from the live `/tokens` catalogue
 * (`SwapKitAssetCatalog`) rather than a static map.
 */
internal object SwapKitAssetPrefix {

    fun of(chain: Chain): String? =
        when (chain) {
            Chain.Ethereum -> "ETH"
            Chain.BscChain -> "BSC"
            Chain.Avalanche -> "AVAX"
            Chain.Arbitrum -> "ARB"
            Chain.Optimism -> "OP"
            Chain.Base -> "BASE"
            Chain.Polygon -> "POL"
            Chain.Solana -> "SOL"
            Chain.Bitcoin -> "BTC"
            Chain.Litecoin -> "LTC"
            Chain.Dogecoin -> "DOGE"
            Chain.BitcoinCash -> "BCH"
            Chain.Dash -> "DASH"
            Chain.Zcash -> "ZEC"
            Chain.Tron -> "TRON"
            Chain.Sui -> "SUI"
            Chain.Cardano -> "ADA"
            Chain.Ton -> "TON"
            Chain.Ripple -> "XRP"
            // Confirmed against `GET /tokens`: chain 4663 lists as `HOOD.ETH` / `HOOD.TSLA-0x…`,
            // chain 999 as `HYPEREVM.HYPE` / `HYPEREVM.USDC-0x…`. The catalogue's separate `HYPE.*`
            // bucket is HyperCore (`USDC:0x…` addresses), a different venue — never this chain.
            Chain.Robinhood -> "HOOD"
            Chain.Hyperliquid -> "HYPEREVM"
            // ZkSync, Mantle, Blast and Cronos land here: EVM networks the wallet holds that
            // SwapKit's `/providers` does not enable and whose catalogue spelling is therefore
            // unconfirmed. Guessing one would mint a garbage identifier, so they stay unoffered
            // until an entry can be read off `GET /tokens`.
            else -> null
        }
}
