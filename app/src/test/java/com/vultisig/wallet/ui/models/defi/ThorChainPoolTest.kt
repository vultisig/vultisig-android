package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class ThorChainPoolTest {

    @Test
    fun `parses native asset pool with no contract`() {
        val parsed = parseThorChainPool("BTC.BTC")
        assertEquals(Chain.Bitcoin, parsed.chain)
        assertEquals("BTC", parsed.ticker)
        assertEquals("", parsed.contractAddress)
    }

    @Test
    fun `parses ERC20 pool with contract address`() {
        val parsed = parseThorChainPool("ETH.USDC-0xA0b86991C6218B36c1d19D4a2e9Eb0cE3606eB48")
        assertEquals(Chain.Ethereum, parsed.chain)
        assertEquals("USDC", parsed.ticker)
        assertEquals("0xA0b86991C6218B36c1d19D4a2e9Eb0cE3606eB48", parsed.contractAddress)
    }

    @Test
    fun `parses BSC and AVAX prefixes to their chains`() {
        assertEquals(Chain.BscChain, parseThorChainPool("BSC.BNB").chain)
        assertEquals(Chain.Avalanche, parseThorChainPool("AVAX.AVAX").chain)
        assertEquals(Chain.Base, parseThorChainPool("BASE.ETH").chain)
        assertEquals(Chain.GaiaChain, parseThorChainPool("GAIA.ATOM").chain)
        assertEquals(Chain.Dogecoin, parseThorChainPool("DOGE.DOGE").chain)
        assertEquals(Chain.Litecoin, parseThorChainPool("LTC.LTC").chain)
        assertEquals(Chain.BitcoinCash, parseThorChainPool("BCH.BCH").chain)
        assertEquals(Chain.ThorChain, parseThorChainPool("THOR.RUNE").chain)
    }

    @Test
    fun `returns null chain for unknown prefix`() {
        val parsed = parseThorChainPool("UNKNOWN.TKN")
        assertNull(parsed.chain)
        assertEquals("TKN", parsed.ticker)
    }

    @Test
    fun `falls back to original input when delimiter is missing`() {
        // Defensive: thornode always returns CHAIN.ASSET, but if a malformed value arrives
        // we don't want to throw — we degrade gracefully.
        val parsed = parseThorChainPool("MALFORMED")
        assertNull(parsed.chain)
        assertEquals("MALFORMED", parsed.ticker)
        assertEquals("", parsed.contractAddress)
    }

    @Test
    fun `prefix matching is case-insensitive`() {
        assertEquals(Chain.Bitcoin, parseThorChainPool("btc.BTC").chain)
        assertEquals(Chain.Ethereum, parseThorChainPool("Eth.ETH").chain)
    }
}
