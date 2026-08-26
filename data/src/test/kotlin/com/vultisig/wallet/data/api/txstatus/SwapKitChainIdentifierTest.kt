package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.evmChainId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the `/track` chain id. The interesting case is that EVM ids are derived, not listed: a
 * settlement poll for a newly supported network must work without another edit here.
 */
internal class SwapKitChainIdentifierTest {

    @Test
    fun `every EVM chain reports its own decimal chain id`() {
        Chain.entries
            .filter { it.standard == TokenStandard.EVM }
            .forEach { chain ->
                assertEquals(
                    chain.evmChainId(),
                    SwapKitChainIdentifier.chainId(chain),
                    "chainId(${chain.raw})",
                )
            }
    }

    @Test
    fun `Robinhood and HyperEVM track under 4663 and 999`() {
        // Both come from the enum, not a hand-written row — the reason neither needed one.
        assertEquals("4663", SwapKitChainIdentifier.chainId(Chain.Robinhood))
        assertEquals("999", SwapKitChainIdentifier.chainId(Chain.Hyperliquid))
    }

    @Test
    fun `non-EVM chains keep their SwapKit slugs`() {
        assertEquals("solana", SwapKitChainIdentifier.chainId(Chain.Solana))
        assertEquals("bitcoin", SwapKitChainIdentifier.chainId(Chain.Bitcoin))
        assertEquals("bitcoincash", SwapKitChainIdentifier.chainId(Chain.BitcoinCash))
        assertEquals("728126428", SwapKitChainIdentifier.chainId(Chain.Tron))
        assertEquals("cardano", SwapKitChainIdentifier.chainId(Chain.Cardano))
        assertEquals("ripple", SwapKitChainIdentifier.chainId(Chain.Ripple))
        assertEquals("ton", SwapKitChainIdentifier.chainId(Chain.Ton))
        assertEquals("sui", SwapKitChainIdentifier.chainId(Chain.Sui))
    }

    @Test
    fun `a chain outside the route catalogue reports null so tracking is skipped`() {
        assertNull(SwapKitChainIdentifier.chainId(Chain.Polkadot))
        assertNull(SwapKitChainIdentifier.chainId(Chain.Osmosis))
    }
}
