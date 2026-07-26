package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitSwapSupportedChainsTest {

    @Test
    fun `always includes THORChain and every live non-halted inbound`() {
        val chains =
            getLimitSwapSupportedChains(listOf(inbound("ETH"), inbound("BTC"), inbound("GAIA")))
        assertTrue(chains.contains(Chain.ThorChain))
        assertTrue(chains.contains(Chain.Ethereum))
        assertTrue(chains.contains(Chain.Bitcoin))
        assertTrue(chains.contains(Chain.GaiaChain))
    }

    @Test
    fun `excludes halted or trading-paused inbounds`() {
        val chains =
            getLimitSwapSupportedChains(
                listOf(
                    inbound("ETH"),
                    inbound("BTC", halted = true),
                    inbound("LTC", chainTradingPaused = true),
                    inbound("DOGE", globalTradingPaused = true),
                )
            )
        assertTrue(chains.contains(Chain.Ethereum))
        assertFalse(chains.contains(Chain.Bitcoin))
        assertFalse(chains.contains(Chain.Litecoin))
        assertFalse(chains.contains(Chain.Dogecoin))
    }

    @Test
    fun `returns only THORChain when a successful response has no usable external inbound`() {
        // Static fallback is the repository's concern (fetch failure); a live all-halted response
        // must not re-offer the halted chains.
        assertEquals(listOf(Chain.ThorChain), getLimitSwapSupportedChains(emptyList()))
        assertEquals(
            listOf(Chain.ThorChain),
            getLimitSwapSupportedChains(listOf(inbound("ETH", halted = true))),
        )
    }

    @Test
    fun `ignores inbound chains that are not THORChain-routable`() {
        val chains = getLimitSwapSupportedChains(listOf(inbound("ETH"), inbound("MAYA")))
        assertTrue(chains.contains(Chain.Ethereum))
        assertFalse(chains.contains(Chain.MayaChain))
    }

    private fun inbound(
        chain: String,
        halted: Boolean = false,
        globalTradingPaused: Boolean = false,
        chainTradingPaused: Boolean = false,
    ) =
        THORChainInboundAddress(
            chain = chain,
            address = "addr",
            halted = halted,
            globalTradingPaused = globalTradingPaused,
            chainTradingPaused = chainTradingPaused,
        )
}
