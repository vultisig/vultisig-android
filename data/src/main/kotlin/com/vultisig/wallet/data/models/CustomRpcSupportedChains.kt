package com.vultisig.wallet.data.models

/**
 * Single source of truth for which chains accept an app-wide custom RPC override (#4787).
 *
 * Shared by the resolution funnel (`EvmApiFactoryImp` / `CosmosApiFactoryImp`) and the Custom RPC
 * UI so the two can never disagree — a chain shown in the picker is exactly a chain the funnel
 * honors.
 *
 * v1 scope is intentionally limited to EVM and Cosmos, the two chain groups whose networking is
 * funneled through a single factory chokepoint with a raw-node RPC URL. UTXO chains (proxy-only, no
 * coherent node insertion point) and `Qbtc` (a Vultisig proxy with no real-node equivalent) are
 * excluded, mirroring the iOS rationale. Single-instance chains (Solana, THORChain, Tron, …) are a
 * planned follow-up.
 */
object CustomRpcSupportedChains {
    /** EVM chains resolved through [com.vultisig.wallet.data.api.EvmApiFactory]. */
    val evm: List<Chain> =
        listOf(
            Chain.Ethereum,
            Chain.BscChain,
            Chain.Avalanche,
            Chain.Polygon,
            Chain.Optimism,
            Chain.CronosChain,
            Chain.Blast,
            Chain.Base,
            Chain.Arbitrum,
            Chain.ZkSync,
            Chain.Mantle,
            Chain.Sei,
            Chain.Hyperliquid,
            Chain.Robinhood,
        )

    /**
     * Cosmos chains resolved through [com.vultisig.wallet.data.api.CosmosApiFactory]. `Qbtc` is
     * deliberately excluded — it points at a Vultisig proxy, not a real node.
     */
    val cosmos: List<Chain> =
        listOf(
            Chain.GaiaChain,
            Chain.Kujira,
            Chain.Dydx,
            Chain.Osmosis,
            Chain.Terra,
            Chain.TerraClassic,
            Chain.Noble,
            Chain.Akash,
        )

    /** All chains that accept a custom RPC override, in display order. */
    val all: List<Chain> = evm + cosmos

    private val ids: Set<ChainId> = all.map { it.id }.toSet()

    fun isSupported(chain: Chain): Boolean = chain.id in ids
}
