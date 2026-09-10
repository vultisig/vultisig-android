package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * [Coin.matchesSearch] is the one filter every asset search goes through, so what it matches on is
 * pinned here rather than in each ViewModel that calls it.
 */
internal class CoinMatchesSearchTest {

    @Test
    fun `a name matches where the ticker would not`() {
        val eth = Coins.Base.ETH

        assertTrue(eth.matchesSearch("ethereum"))
        assertTrue(eth.matchesSearch("Ethereum"))
        assertTrue(eth.matchesSearch("eth"))
        assertFalse(eth.matchesSearch("bitcoin"))
    }

    @Test
    fun `a multi-word name matches on any substring`() {
        val usdc = Coins.Arbitrum.USDC

        assertTrue(usdc.matchesSearch("usd coin"))
        assertTrue(usdc.matchesSearch("coin"))
        assertFalse(usdc.matchesSearch("usdcoin"))
    }

    @Test
    fun `a contract address matches whatever case it is pasted in`() {
        val usdc = Coins.Ethereum.USDC

        assertTrue(usdc.matchesSearch(usdc.contractAddress.lowercase()))
        assertTrue(usdc.matchesSearch(usdc.contractAddress.uppercase()))
        assertTrue(usdc.matchesSearch(usdc.contractAddress.drop(2).take(12)))
    }

    @Test
    fun `blank and whitespace queries match everything`() {
        val nameless = Coins.Bitcoin.BTC.copy(name = "")

        assertTrue(nameless.matchesSearch(""))
        assertTrue(nameless.matchesSearch("   "))
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertTrue(Coins.Bitcoin.BTC.matchesSearch("  bitcoin "))
    }

    @Test
    fun `a nameless coin still matches on its ticker`() {
        val custom =
            Coin(
                chain = Chain.Ethereum,
                ticker = "PENGU",
                logo = "",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x1234",
                isNativeToken = false,
            )

        assertEquals("", custom.name)
        assertTrue(custom.matchesSearch("pengu"))
        assertFalse(custom.matchesSearch("pudgy"))
    }
}
