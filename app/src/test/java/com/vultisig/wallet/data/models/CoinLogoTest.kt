package com.vultisig.wallet.data.models

import com.vultisig.wallet.R
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class CoinLogoTest {

    private fun coin(logo: String, chain: Chain = Chain.Arbitrum): Coin =
        Coin(
            chain = chain,
            ticker = "TEST",
            logo = logo,
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = false,
        )

    @Test
    fun `tokenLogoRes returns the predefined drawable when the coin logo is in the mapping`() {
        // "usdc" is mapped to R.drawable.usdc in getCoinLogo
        val res = coin(logo = "usdc").tokenLogoRes()
        assertEquals(R.drawable.usdc, res)
    }

    @Test
    fun `tokenLogoRes mapping is case-insensitive`() {
        // getCoinLogo lowercases the input — uppercase "USDC" should still resolve to
        // R.drawable.usdc
        val res = coin(logo = "USDC").tokenLogoRes()
        assertEquals(R.drawable.usdc, res)
    }

    @Test
    fun `tokenLogoRes falls back to chain logo when coin logo is unknown`() {
        // A URL-style logo (typical for arbitrary ERC20s) is not in the mapping
        val res =
            coin(logo = "https://example.com/some-token.png", chain = Chain.Arbitrum).tokenLogoRes()
        assertEquals(Chain.Arbitrum.logo, res)
    }

    @Test
    fun `tokenLogoRes falls back to chain logo when coin logo is empty`() {
        val res = coin(logo = "", chain = Chain.Ethereum).tokenLogoRes()
        assertEquals(Chain.Ethereum.logo, res)
    }

    @Test
    fun `tokenLogoRes always returns a non-zero drawable id`() {
        // Sanity check: every supported chain must produce a usable resource id, even when the
        // coin's logo string is junk.
        Chain.entries.forEach { chain ->
            val res = coin(logo = "definitely-not-a-real-logo", chain = chain).tokenLogoRes()
            assertTrue(res != 0, "Expected non-zero drawable id for chain $chain, got 0")
        }
    }

    @Test
    fun `tokenLogoRes for known token does not equal chain logo when they differ`() {
        // USDC on Ethereum: token logo (usdc) should differ from chain logo (ethereum)
        val res = coin(logo = "usdc", chain = Chain.Ethereum).tokenLogoRes()
        assertEquals(R.drawable.usdc, res)
        assertNotEquals(Chain.Ethereum.logo, res)
    }

    @Test
    fun `tokenLogoRes for native chain token resolves via getCoinLogo not fallback`() {
        // ETH's logo string is "eth" which getCoinLogo maps to R.drawable.ethereum — same drawable
        // as Chain.Ethereum.logo, but the path through getCoinLogo is what's exercised here.
        val res = coin(logo = "eth", chain = Chain.Ethereum).tokenLogoRes()
        assertEquals(R.drawable.ethereum, res)
    }
}
