package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoinIsLpTokenTest {

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        isNativeToken: Boolean = false,
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 0,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    @Test
    fun `ThorChain RUJI is not an LP token`() {
        val c = coin(Chain.ThorChain, "RUJI", "x/ruji")
        assertFalse(c.isLpToken)
    }

    @Test
    fun `ThorChain RKUJI is not an LP token`() {
        val c = coin(Chain.ThorChain, "RKUJI", "thor.rkuji")
        assertFalse(c.isLpToken)
    }

    @Test
    fun `ThorChain sTCY is an LP token`() {
        val c = coin(Chain.ThorChain, "sTCY", "x/staking-tcy")
        assertTrue(c.isLpToken)
    }

    @Test
    fun `ThorChain sRUJI staking denom is an LP token`() {
        val c = coin(Chain.ThorChain, "sRUJI", "x/staking-ruji")
        assertTrue(c.isLpToken)
    }

    @Test
    fun `ThorChain yRUNE nami-index denom is an LP token`() {
        val c =
            coin(
                Chain.ThorChain,
                "yRUNE",
                "x/nami-index-nav-thor1mlphkryw5g54yfkrp6xpqzlpv4f8wh6hyw27yyg4z2els8a9gxpqhfhekt-rcpt",
            )
        assertTrue(c.isLpToken)
    }

    @Test
    fun `ThorChain yTCY nami-index denom is an LP token`() {
        val c =
            coin(
                Chain.ThorChain,
                "yTCY",
                "x/nami-index-nav-thor1h0hr0rm3dawkedh44hlrmgvya6plsryehcr46yda2vj0wfwgq5xqrs86px-rcpt",
            )
        assertTrue(c.isLpToken)
    }

    @Test
    fun `ThorChain native RUNE is not an LP token`() {
        val c = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)
        assertFalse(c.isLpToken)
    }

    @Test
    fun `ThorChain TCY is not an LP token`() {
        val c = coin(Chain.ThorChain, "TCY", "tcy")
        assertFalse(c.isLpToken)
    }
}
