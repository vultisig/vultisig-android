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
        assertEquals(Chain.Solana, parseThorChainPool("SOL.SOL").chain)
        assertEquals(Chain.Tron, parseThorChainPool("TRON.TRX").chain)
        assertEquals(
            Chain.Tron,
            parseThorChainPool("TRON.USDT-TR7NHQJEKQXGTCI8Q8ZY4PL8OTSZGJLJ6T").chain,
        )
        assertEquals(Chain.Ripple, parseThorChainPool("XRP.XRP").chain)
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

    @Test
    fun `CACAO side of a native Maya pool pairs with the pool's asset chain`() {
        assertEquals(Chain.Bitcoin, mayaLpPairedChain(Chain.MayaChain, "BTC.BTC"))
        assertEquals(Chain.Ethereum, mayaLpPairedChain(Chain.MayaChain, "ETH.ETH"))
        assertEquals(Chain.Arbitrum, mayaLpPairedChain(Chain.MayaChain, "ARB.ETH"))
        assertEquals(Chain.Dash, mayaLpPairedChain(Chain.MayaChain, "DASH.DASH"))
        assertEquals(Chain.Zcash, mayaLpPairedChain(Chain.MayaChain, "ZEC.ZEC"))
    }

    @Test
    fun `asset side of a native Maya pool pairs with MayaChain`() {
        assertEquals(Chain.MayaChain, mayaLpPairedChain(Chain.Arbitrum, "ARB.ETH"))
        assertEquals(Chain.MayaChain, mayaLpPairedChain(Chain.Zcash, "zec.zec"))
    }

    @Test
    fun `Maya pools whose asset side the app cannot deposit stay single-sided`() {
        assertNull(
            mayaLpPairedChain(
                Chain.MayaChain,
                "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
            )
        )
        assertNull(mayaLpPairedChain(Chain.MayaChain, "ADA.ADA"))
        assertNull(mayaLpPairedChain(Chain.MayaChain, "THOR.RUNE"))
        assertNull(mayaLpPairedChain(Chain.MayaChain, "MAYA.MAYA"))
    }

    @Test
    fun `a chain on neither side of the Maya pool gets no pairing`() {
        assertNull(mayaLpPairedChain(Chain.Ethereum, "ARB.ETH"))
        assertNull(mayaLpPairedChain(Chain.ThorChain, "BTC.BTC"))
    }
}
