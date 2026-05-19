package com.vultisig.wallet.data.repositories

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard

/**
 * Static lookup of well-known EVM contract addresses → human-readable labels.
 *
 * Consulted by the dApp verify and join-keysign screens so a recipient that resolves to a known DEX
 * router renders as e.g. `Uniswap V3 Router` instead of a raw `0x68b3…fC45`. Coverage is limited to
 * contracts whose addresses are stable across deployments and widely used by Vultisig users; new
 * entries should only be added once an address has been re-verified against the protocol's
 * canonical documentation.
 *
 * Addresses are stored in their canonical (`0x` + lowercase hex) form so lookups never depend on
 * the casing of the wire address. Permit2 (`0x000000000022D473030F116dDEE9F6B43aC78BA3`) ships at
 * the same address on every chain it supports, so it lives in a separate chain-agnostic table that
 * the public [lookup] consults as a fallback before reporting "no match".
 */
object KnownEvmContracts {

    /** Address → label, keyed by chain. Addresses are stored as `0x` + lowercase 40-char hex. */
    private val perChain: Map<Chain, Map<String, String>> =
        mapOf(
            Chain.Ethereum to
                mapOf(
                    // Uniswap V2 (`UniswapV2Router02`)
                    "0x7a250d5630b4cf539739df2c5dacb4c659f2488d" to "Uniswap V2 Router",
                    // Uniswap V3
                    "0xe592427a0aece92de3edee1f18e0157c05861564" to "Uniswap V3 Router",
                    "0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45" to "Uniswap V3 Router 2",
                    // Uniswap Universal Router — V1 (deadline variant) and V2 (V1.2, default)
                    "0xef1c6e67703c7bd7107eed8303fbe6ec2554bf6b" to "Uniswap Universal Router V1",
                    "0x66a9893cc07d91d95644aedd05d03f95e1dba8af" to "Uniswap Universal Router V2",
                    // 1inch Aggregation Router (v5 + v6 — both at canonical deployments)
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                    // 0x Exchange Proxy
                    "0xdef1c0ded9bec7f1a1670819833240f027b25eff" to "0x Exchange Proxy",
                    // Aave V3 Pool
                    "0x87870bca3f3fd6335c3f4ce8392d69350b4fa4e2" to "Aave V3 Pool",
                ),
            Chain.BscChain to
                mapOf(
                    "0x10ed43c718714eb63d5aa57b78b54704e256024e" to "PancakeSwap V2 Router",
                    "0x13f4ea83d0bd40e75c8222255bc855a974568dd4" to "PancakeSwap V3 Router",
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                ),
            Chain.Polygon to
                mapOf(
                    "0xe592427a0aece92de3edee1f18e0157c05861564" to "Uniswap V3 Router",
                    "0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45" to "Uniswap V3 Router 2",
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                    "0x794a61358d6845594f94dc1db02a252b5b4814ad" to "Aave V3 Pool",
                ),
            Chain.Arbitrum to
                mapOf(
                    "0xe592427a0aece92de3edee1f18e0157c05861564" to "Uniswap V3 Router",
                    "0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45" to "Uniswap V3 Router 2",
                    "0x5e325eda8064b456f4781070c0738d849c824258" to "Uniswap Universal Router",
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                    "0x794a61358d6845594f94dc1db02a252b5b4814ad" to "Aave V3 Pool",
                ),
            Chain.Optimism to
                mapOf(
                    "0xe592427a0aece92de3edee1f18e0157c05861564" to "Uniswap V3 Router",
                    "0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45" to "Uniswap V3 Router 2",
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                    "0x794a61358d6845594f94dc1db02a252b5b4814ad" to "Aave V3 Pool",
                ),
            Chain.Base to
                mapOf(
                    "0x2626664c2603336e57b271c5c0b26f421741e481" to "Uniswap V3 Router 2",
                    "0x6ff5693b99212da76ad316178a184ab56d299b43" to "Uniswap Universal Router",
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                    "0xa238dd80c259a72e81d7e4664a9801593f98d1c5" to "Aave V3 Pool",
                ),
            Chain.Avalanche to
                mapOf(
                    "0x1111111254eeb25477b68fb85ed929f73a960582" to "1inch Router V5",
                    "0x111111125421ca6dc452d289314280a0f8842a65" to "1inch Router V6",
                    "0x794a61358d6845594f94dc1db02a252b5b4814ad" to "Aave V3 Pool",
                ),
        )

    /**
     * Contracts that share an identical deployment address across every chain they support.
     * Consulted only after the chain-specific table returns no match so a chain-specific override
     * (rare in practice) always wins.
     */
    private val chainAgnostic: Map<String, String> =
        mapOf(
            // Permit2 — Uniswap's signature-based approval router
            "0x000000000022d473030f116ddee9f6b43ac78ba3" to "Uniswap Permit2",
            // CowSwap settlement contract
            "0x9008d19f58aabd9ed0d60971565aa8510560ab41" to "CoW Protocol",
        )

    /**
     * Returns the human-readable label for [address] on [chain], or null when the contract is not
     * in the registry. [address] is matched case-insensitively after normalising to `0x`-prefixed
     * lowercase hex; whitespace and casing variants from the wire are tolerated. Non-EVM chains
     * always return null — the registry only meaningfully maps Ethereum-style addresses, and
     * silently matching a chain-agnostic entry like Permit2 on Solana would mislead the user.
     */
    fun lookup(chain: Chain, address: String): String? {
        if (chain.standard != TokenStandard.EVM) return null
        val key = normalizeAddress(address) ?: return null
        return perChain[chain]?.get(key) ?: chainAgnostic[key]
    }

    private fun normalizeAddress(address: String): String? {
        val trimmed = address.trim().takeIf { it.isNotEmpty() } ?: return null
        val withPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) trimmed else "0x$trimmed"
        return withPrefix.lowercase()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val tableForTesting: Map<Chain, Map<String, String>>
        get() = perChain

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val chainAgnosticForTesting: Map<String, String>
        get() = chainAgnostic
}
