package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the predicates that replaced SwapKit's per-chain allowlist. The property worth defending is
 * that an EVM network is offered as soon as SwapKit's spelling of it is known — no provider-table
 * edit on top — while the source side stays narrower than the destination side, and a network whose
 * spelling is *not* known is never offered a route it could not address.
 */
internal class SwapKitCapabilityTest {

    @Test
    fun `an EVM chain can receive exactly when SwapKit's spelling of it is known`() {
        // The open list, stated as a property rather than an enumeration: a new EVM network is in
        // as soon as [SwapKitAssetPrefix] carries it. Sei is excluded on top of that — the wallet
        // holds it but does not swap it at all.
        Chain.entries
            .filter { it.standard == TokenStandard.EVM }
            .forEach { chain ->
                val expected = chain != Chain.Sei && SwapKitAssetPrefix.of(chain) != null
                assertTrue(
                    SwapKitCapability.canReceiveOn(chain) == expected,
                    "canReceiveOn(${chain.raw}) should be $expected",
                )
            }
    }

    @Test
    fun `EVM chains SwapKit has no asset identifier for are not offered at all`() {
        // These four gained SWAPKIT when the table stopped allowlisting, but no prefix is known
        // for any of them, so `assetIdentifier` could only throw. Offering them replaced a pair's
        // immediate "no route" guidance with a spinner and a late error — worst for
        // Cardano/TON/SUI/BTC destinations, where SwapKit is the only shared provider.
        listOf(Chain.ZkSync, Chain.Mantle, Chain.Blast, Chain.CronosChain).forEach { chain ->
            assertNull(SwapKitAssetPrefix.of(chain), "prefix for ${chain.raw}")
            assertFalse(SwapKitCapability.canReceiveOn(chain), "canReceiveOn(${chain.raw})")
            assertFalse(SwapKitCapability.canQuoteFrom(chain), "canQuoteFrom(${chain.raw})")
        }
    }

    @Test
    fun `Robinhood and HyperEVM need no provider-table row to be receivable`() {
        // One entry, in the map that is load-bearing anyway: without a prefix no quote can be
        // addressed, so this is the same edit that makes the chain quotable at all.
        assertTrue(SwapKitCapability.canReceiveOn(Chain.Robinhood))
        assertTrue(SwapKitCapability.canReceiveOn(Chain.Hyperliquid))
    }

    @Test
    fun `non-EVM chains without a SwapKit path cannot receive`() {
        listOf(
                Chain.ThorChain,
                Chain.MayaChain,
                Chain.GaiaChain,
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
    fun `EVM chains the scanner covers can originate a route`() {
        // Blast is deliberately absent: Blockaid does index it, but SwapKit's spelling of it is
        // unknown, so it is not receivable and therefore not sourceable either.
        listOf(
                Chain.Ethereum,
                Chain.Arbitrum,
                Chain.Avalanche,
                Chain.Base,
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
