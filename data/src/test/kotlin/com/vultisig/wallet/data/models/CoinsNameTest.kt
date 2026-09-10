package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every curated coin carries a name so the pickers can be searched by it. Nothing else fails when
 * an entry is added without one — search just silently falls back to the ticker for it — so the
 * catalogue is checked whole here.
 */
internal class CoinsNameTest {

    @Test
    fun `every curated coin has a name`() {
        val nameless = Coins.allResolvable.filter { it.name.isBlank() }.map { it.id }

        assertTrue(nameless.isEmpty(), "curated coins without a name: $nameless")
    }

    @Test
    fun `the native asset of an L2 is named after the asset, not the chain`() {
        listOf(Coins.Base.ETH, Coins.Arbitrum.ETH, Coins.Optimism.ETH, Coins.ZkSync.ETH).forEach {
            assertEquals("Ethereum", it.name, it.id)
        }
    }

    @Test
    fun `withCuratedName fills a discovered coin's name from the catalogue`() {
        val discovered = Coins.ThorChain.TCY.copy(name = "", address = "thor1abc")

        val named = Coins.withCuratedName(discovered)

        assertEquals(Coins.ThorChain.TCY.name, named.name)
        assertEquals("thor1abc", named.address)
    }

    @Test
    fun `withCuratedName keeps a name the source already supplied`() {
        val discovered = Coins.Ethereum.USDC.copy(name = "USD Coin (Bridged)")

        assertEquals("USD Coin (Bridged)", Coins.withCuratedName(discovered).name)
    }

    @Test
    fun `withCuratedName leaves a coin the catalogue does not carry unchanged`() {
        val unknown =
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

        assertEquals(unknown, Coins.withCuratedName(unknown))
    }

    @Test
    fun `withCuratedName does not name a lookalike sharing a curated ticker`() {
        val lookalike = Coins.Ethereum.USDC.copy(name = "", contractAddress = "0xdeadbeef")

        assertEquals("", Coins.withCuratedName(lookalike).name)
    }
}
