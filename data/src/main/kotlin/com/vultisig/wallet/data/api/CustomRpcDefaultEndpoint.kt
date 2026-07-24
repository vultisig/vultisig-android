package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain

/**
 * Single source of truth for the hardcoded default RPC endpoint of every chain that accepts a
 * custom override (#4787 / #4997).
 *
 * The same defaults serve two callers, so they must never drift:
 * - the resolution funnel ([EvmApiFactory] / [CosmosApiFactory]) falls back to them when the user
 *   has no override set, and
 * - the Custom RPC editor's read-only "DEFAULT ENDPOINT" card shows them so the user can see what
 *   they are overriding (and what *Reset to Default* restores).
 *
 * Mirrors the iOS `CustomRPCDefaultEndpoint`. Keep this list in lockstep with
 * [com.vultisig.wallet.data.models.CustomRpcSupportedChains]: a chain shown in the picker is
 * exactly a chain the funnel honors and for which a default exists here.
 */
object CustomRpcDefaultEndpoint {

    /** EVM defaults resolved through [EvmApiFactory]. */
    private val evm: Map<Chain, String> =
        mapOf(
            Chain.Ethereum to "https://api.vultisig.com/eth/",
            Chain.BscChain to "https://api.vultisig.com/bnb/",
            Chain.Avalanche to "https://api.vultisig.com/avax/",
            Chain.Polygon to "https://api.vultisig.com/polygon/",
            Chain.Optimism to "https://api.vultisig.com/opt/",
            Chain.CronosChain to "https://cronos-evm-rpc.publicnode.com",
            Chain.Blast to "https://api.vultisig.com/blast/",
            Chain.Base to "https://api.vultisig.com/base/",
            Chain.Arbitrum to "https://api.vultisig.com/arb/",
            Chain.ZkSync to "https://api.vultisig.com/zksync/",
            Chain.Mantle to "https://api.vultisig.com/mantle/",
            Chain.Sei to "https://evm-rpc.sei-apis.com/",
            Chain.Robinhood to "https://rpc.mainnet.chain.robinhood.com/",
            Chain.Hyperliquid to "https://api.vultisig.com/hyperevm/",
        )

    /**
     * Cosmos defaults resolved through [CosmosApiFactory]. `Qbtc` is included so the funnel keeps
     * its default, even though it is excluded from the custom-RPC picker (Vultisig proxy, no
     * real-node equivalent).
     */
    private val cosmos: Map<Chain, String> =
        mapOf(
            Chain.GaiaChain to "https://cosmos-rest.publicnode.com",
            Chain.Kujira to "https://kujira-api.polkachu.com",
            Chain.Dydx to "https://dydx-rest.publicnode.com",
            Chain.Osmosis to "https://osmosis-rest.publicnode.com",
            Chain.Terra to "https://terra-lcd.publicnode.com",
            Chain.TerraClassic to "https://terra-classic-lcd.publicnode.com",
            Chain.Noble to "https://noble-api.polkachu.com",
            Chain.Akash to "https://akash-rest.publicnode.com",
            Chain.Qbtc to "https://api.vultisig.com/qbtc-rpc",
        )

    /** Default EVM RPC URL for [chain]. Throws for a chain [EvmApiFactory] does not support. */
    fun evmUrl(chain: Chain): String =
        evm[chain] ?: throw IllegalArgumentException("Unsupported chain $chain")

    /**
     * Default Cosmos REST URL for [chain]. Throws for a chain [CosmosApiFactory] does not support.
     */
    fun cosmosUrl(chain: Chain): String =
        cosmos[chain] ?: throw IllegalArgumentException("Unsupported chain $chain")

    /**
     * Default endpoint for [chain] regardless of family, or `null` if the chain has no configurable
     * default. Used by the Custom RPC editor to render the read-only default card.
     */
    fun string(chain: Chain): String? = evm[chain] ?: cosmos[chain]
}
