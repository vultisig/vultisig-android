package com.vultisig.wallet.data.models

import com.vultisig.wallet.R
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // Multi-alias entries are easy to break by typo — any alias that silently falls through to the
    // chain logo would be a regression. Pin each known alias to its drawable so renames get caught.
    @Test
    fun `tokenLogoRes resolves solana aliases`() {
        assertEquals(R.drawable.solana, coin(logo = "sol").tokenLogoRes())
        assertEquals(R.drawable.solana, coin(logo = "solana").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves polygon aliases`() {
        assertEquals(R.drawable.polygon, coin(logo = "pol").tokenLogoRes())
        assertEquals(R.drawable.polygon, coin(logo = "matic").tokenLogoRes())
        assertEquals(R.drawable.polygon, coin(logo = "polygon").tokenLogoRes())
        assertEquals(R.drawable.polygon, coin(logo = "eth_polygon").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves zksync aliases`() {
        assertEquals(R.drawable.zksync, coin(logo = "zksync").tokenLogoRes())
        assertEquals(R.drawable.zksync, coin(logo = "zsync-era").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves wif aliases`() {
        assertEquals(R.drawable.wif, coin(logo = "wif").tokenLogoRes())
        assertEquals(R.drawable.wif, coin(logo = "dogwifhat-wif-logo").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves raydium aliases`() {
        assertEquals(R.drawable.ray, coin(logo = "ray").tokenLogoRes())
        assertEquals(R.drawable.ray, coin(logo = "raydium-ray-seeklogo-2").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves astro aliases`() {
        assertEquals(R.drawable.astro, coin(logo = "astro").tokenLogoRes())
        assertEquals(R.drawable.astro, coin(logo = "terra-astroport").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves levana aliases`() {
        assertEquals(R.drawable.lvn, coin(logo = "lvn").tokenLogoRes())
        assertEquals(R.drawable.lvn, coin(logo = "levana").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves fuzn logo`() {
        assertEquals(R.drawable.fuzion, coin(logo = "fuzn").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves vulti aliases`() {
        assertEquals(R.drawable.vulti, coin(logo = "vult").tokenLogoRes())
        assertEquals(R.drawable.vulti, coin(logo = "vulti").tokenLogoRes())
    }

    @Test
    fun `tokenLogoRes resolves hyperliquid aliases`() {
        assertEquals(R.drawable.hyperliquid, coin(logo = "hype").tokenLogoRes())
        assertEquals(R.drawable.hyperliquid, coin(logo = "whype").tokenLogoRes())
    }

    // The five surfaces that name a swap provider all render through getProviderLogo, so one it
    // cannot resolve shows a bare name on every one of them. The `when` over SwapProvider turns a
    // newly added provider into a compile error; these tests pin the string -> enum step in front
    // of it, which no compiler can check.
    @Test
    fun `getProviderLogo resolves every provider's display label`() {
        SwapProvider.entries.forEach { provider ->
            val label = provider.getSwapProviderId()
            assertNotNull(
                getProviderLogo(label),
                "Expected a logo for $provider, whose UI label is \"$label\"",
            )
        }
    }

    @Test
    fun `getProviderLogo resolves display labels to their drawable`() {
        assertEquals(R.drawable.rune, getProviderLogo("THORChain"))
        assertEquals(R.drawable.maya, getProviderLogo("MayaChain"))
        assertEquals(R.drawable.jup, getProviderLogo("Jupiter"))
        assertEquals(R.drawable.oneinch, getProviderLogo("1Inch"))
        assertEquals(R.drawable.kyberswap, getProviderLogo("KyberSwap"))
        assertEquals(R.drawable.lifi, getProviderLogo("LI.FI"))
        assertEquals(R.drawable.swapkit, getProviderLogo("SwapKit"))
    }

    @Test
    fun `getProviderLogo is case-insensitive`() {
        assertEquals(R.drawable.rune, getProviderLogo("thorchain"))
        assertEquals(R.drawable.rune, getProviderLogo("THORCHAIN"))
    }

    @Test
    fun `getProviderLogo resolves wire aliases the history layer stores`() {
        assertEquals(R.drawable.maya, getProviderLogo("maya"))
        assertEquals(R.drawable.maya, getProviderLogo("mayachain"))
        assertEquals(R.drawable.kyberswap, getProviderLogo("kyber"))
        assertEquals(R.drawable.kyberswap, getProviderLogo("kyberswap"))
    }

    // formatSwapKitProviderLabel appends the route as `SwapKit (CHAINFLIP)`. Stripping the hint is
    // a
    // general rule now rather than a startsWith("swapkit") special case, so it must hold for a
    // provider that has never carried a hint too.
    @Test
    fun `getProviderLogo strips the route hint before matching`() {
        assertEquals(R.drawable.swapkit, getProviderLogo("SwapKit (CHAINFLIP)"))
        assertEquals(R.drawable.swapkit, getProviderLogo("SwapKit (NEAR)"))
        assertEquals(R.drawable.swapkit, getProviderLogo("SwapKit (GARDEN)"))
        assertEquals(R.drawable.rune, getProviderLogo("THORChain (SOMEROUTE)"))
    }

    @Test
    fun `getProviderLogo returns null for an unknown provider`() {
        assertNull(getProviderLogo("NotAProvider"))
        assertNull(getProviderLogo(""))
    }
}
