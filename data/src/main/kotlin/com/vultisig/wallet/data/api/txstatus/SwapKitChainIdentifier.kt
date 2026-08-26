package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.evmChainId

/**
 * Translates a Vultisig [Chain] to the chainId string SwapKit's `/track` endpoint expects — EVM
 * chains use the decimal EIP-155 chainId, non-EVM chains use SwapKit's slug. Returns `null` for
 * chains outside SwapKit's route catalogue: the caller's signal to skip `/track` gating and fall
 * back to the source-chain status check rather than poll an unknown chainId forever. Ported from
 * iOS' `SwapKitChainIdentifier`.
 *
 * Kept separate from the asset-prefix mapping in
 * [com.vultisig.wallet.data.repositories.swap.SwapKitQuoteSource] (`ETH`/`ARB`/`BTC`): `/track`
 * needs the numeric/slug chainId, which only overlaps for a couple of chains.
 */
internal object SwapKitChainIdentifier {
    fun chainId(chain: Chain): String? =
        // EVM networks are their own decimal chain id, read off the enum rather than a hand-written
        // list, so a chain the wallet already holds never needs an entry here just to be tracked —
        // Robinhood (4663) and HyperEVM (999) arrived that way. Mirrors iOS'
        // `SwapKitChainIDMapper.swapKitChainId`.
        chain.evmChainId()
            ?: when (chain) {
                Chain.Tron -> "728126428"
                Chain.Cardano -> "cardano"
                Chain.Ton -> "ton"
                Chain.Solana -> "solana"
                Chain.Bitcoin -> "bitcoin"
                Chain.BitcoinCash -> "bitcoincash"
                Chain.Litecoin -> "litecoin"
                Chain.Ripple -> "ripple"
                Chain.GaiaChain -> "cosmoshub-4"
                Chain.Dash -> "dash"
                Chain.Zcash -> "zcash"
                Chain.Sui -> "sui"
                Chain.Dogecoin -> "dogecoin"
                Chain.Kujira -> "kaiyo-1"
                // THORChain/Maya are filtered out of SwapKit routes (Vultisig pays those affiliates
                // directly), so a SwapKit swap is never sourced from them — but kept for iOS
                // parity.
                Chain.MayaChain -> "mayachain-mainnet-v1"
                Chain.ThorChain -> "thorchain-1"
                else -> null
            }
}
