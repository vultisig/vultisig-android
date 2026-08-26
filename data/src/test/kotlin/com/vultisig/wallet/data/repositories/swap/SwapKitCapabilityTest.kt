package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the predicates that replaced SwapKit's per-chain allowlist. The property worth defending is
 * that adding an EVM network to the wallet is enough to have SwapKit tried on it — no second edit —
 * while the source side stays narrower than the destination side.
 */
internal class SwapKitCapabilityTest {

    @Test
    fun `every EVM chain except Sei can receive`() {
        // The open list, stated as a property rather than an enumeration: a new EVM network is in
        // by construction. Sei is the single exception — the wallet holds it but does not swap it.
        Chain.entries
            .filter { it.standard == TokenStandard.EVM }
            .forEach { chain ->
                val expected = chain != Chain.Sei
                assertTrue(
                    SwapKitCapability.canReceiveOn(chain) == expected,
                    "canReceiveOn(${chain.raw}) should be $expected",
                )
            }
    }

    @Test
    fun `Robinhood and HyperEVM need no entry of their own to be receivable`() {
        assertTrue(SwapKitCapability.canReceiveOn(Chain.Robinhood))
        assertTrue(SwapKitCapability.canReceiveOn(Chain.Hyperliquid))
    }

    @Test
    fun `non-EVM chains without a SwapKit path cannot receive`() {
        listOf(
                Chain.ThorChain,
                Chain.MayaChain,
                Chain.GaiaChain,
                Chain.Kujira,
                Chain.Polkadot,
                Chain.Osmosis,
                Chain.Dydx,
                Chain.Terra,
                Chain.TerraClassic,
                Chain.Noble,
                Chain.Akash,
                Chain.Qbtc,
                Chain.Bittensor,
            )
            .forEach { assertFalse(SwapKitCapability.canReceiveOn(it), "canReceiveOn(${it.raw})") }
    }

    @Test
    fun `non-EVM chains with a SwapKit signer or deposit path can receive and quote`() {
        listOf(
                Chain.Bitcoin,
                Chain.BitcoinCash,
                Chain.Cardano,
                Chain.Dash,
                Chain.Dogecoin,
                Chain.Litecoin,
                Chain.Ripple,
                Chain.Solana,
                Chain.Sui,
                Chain.Ton,
                Chain.Tron,
                Chain.Zcash,
            )
            .forEach { chain ->
                assertTrue(SwapKitCapability.canReceiveOn(chain), "canReceiveOn(${chain.raw})")
                // Non-EVM routes are not calldata against an unknown router, so the reputation
                // gate does not apply and receive implies quote.
                assertTrue(SwapKitCapability.canQuoteFrom(chain), "canQuoteFrom(${chain.raw})")
            }
    }

    @Test
    fun `Robinhood receives but cannot originate a route`() {
        // Blockaid does not index 4663, so nothing can reputation-check calldata signed from there.
        assertTrue(SwapKitCapability.canReceiveOn(Chain.Robinhood))
        assertFalse(SwapKitCapability.canQuoteFrom(Chain.Robinhood))
    }

    @Test
    fun `an EVM chain the scanner does not cover cannot originate a route`() {
        listOf(Chain.ZkSync, Chain.Mantle, Chain.CronosChain).forEach { chain ->
            assertTrue(SwapKitCapability.canReceiveOn(chain), "canReceiveOn(${chain.raw})")
            assertFalse(SwapKitCapability.canQuoteFrom(chain), "canQuoteFrom(${chain.raw})")
        }
    }

    @Test
    fun `EVM chains the scanner covers can originate a route`() {
        listOf(
                Chain.Ethereum,
                Chain.Arbitrum,
                Chain.Avalanche,
                Chain.Base,
                Chain.Blast,
                Chain.BscChain,
                Chain.Hyperliquid,
                Chain.Optimism,
                Chain.Polygon,
            )
            .forEach { assertTrue(SwapKitCapability.canQuoteFrom(it), "canQuoteFrom(${it.raw})") }
    }

    @Test
    fun `a chain that cannot receive can never quote`() {
        Chain.entries.forEach { chain ->
            if (!SwapKitCapability.canReceiveOn(chain)) {
                assertFalse(SwapKitCapability.canQuoteFrom(chain), "canQuoteFrom(${chain.raw})")
            }
        }
    }
}
