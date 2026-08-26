package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain

/**
 * Chain slug Blockaid's API is keyed by, or `null` for a chain Blockaid does not index. Port of
 * iOS' `BlockaidChainIdentifier`.
 *
 * The nullable return is the point: callers that must reputation-check before acting — notably
 * [com.vultisig.wallet.data.repositories.swap.SwapKitCapability.canQuoteFrom] — need to ask "is
 * this chain scannable?" without an exception, while [BlockaidRpcClient] keeps throwing on the
 * chains it is never handed. Kept in lockstep with [BlockaidScannerService.supportedChains]: a
 * chain named here that the scanner does not run is a gate that lies about coverage.
 */
internal object BlockaidChainIdentifier {
    fun name(chain: Chain): String? =
        when (chain) {
            Chain.Arbitrum -> "arbitrum"
            Chain.Avalanche -> "avalanche"
            Chain.Base -> "base"
            Chain.Blast -> "blast"
            Chain.BscChain -> "bsc"
            Chain.Bitcoin -> "bitcoin"
            Chain.Ethereum -> "ethereum"
            Chain.Hyperliquid -> "hyperevm"
            Chain.Optimism -> "optimism"
            Chain.Polygon -> "polygon"
            Chain.Sui -> "sui"
            Chain.Solana -> "solana"
            else -> null
        }
}
