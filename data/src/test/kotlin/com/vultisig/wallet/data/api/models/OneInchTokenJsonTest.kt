package com.vultisig.wallet.data.api.models

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the CoinGecko-allowlist primitive used by EVM token auto-discovery (#4555). The set of
 * accepted providers must stay in lockstep with the SDK resolver.
 */
internal class OneInchTokenJsonTest {

    @Test
    fun `isCoinGeckoVerified is true when providers contain CoinGecko`() {
        // USDC-shape token: 1inch + CoinGecko + Uniswap.
        val token = usdcToken(providers = listOf("1inch", "CoinGecko", "Uniswap Labs Default"))

        assertTrue(token.isCoinGeckoVerified)
    }

    @Test
    fun `isCoinGeckoVerified is false when providers omit CoinGecko`() {
        // Real-world airdrop dust: 1inch knows the token, CoinGecko has not curated it.
        val token = anonToken(providers = listOf("1inch"))

        assertFalse(token.isCoinGeckoVerified)
    }

    @Test
    fun `isCoinGeckoVerified is false when providers is null`() {
        val token = anonToken(providers = null)

        assertFalse(token.isCoinGeckoVerified)
    }

    @Test
    fun `isCoinGeckoVerified is case sensitive on the provider name`() {
        // The SDK / iOS implementations use a strict-equality `includes` check; any other casing
        // (coingecko, COINGECKO, Coingecko) must not pass the allowlist.
        val token = anonToken(providers = listOf("coingecko"))

        assertFalse(token.isCoinGeckoVerified)
    }

    private fun usdcToken(providers: List<String>?): OneInchTokenJson =
        OneInchTokenJson(
            address = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            symbol = "USDC",
            name = "USD Coin",
            decimals = 6,
            logoURI = "https://tokens.1inch.io/0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48.png",
            providers = providers,
        )

    private fun anonToken(providers: List<String>?): OneInchTokenJson =
        OneInchTokenJson(
            address = "0x00000000002514bf58ae82408e1e217f16a1dfa0",
            symbol = "ANON",
            name = "Anon",
            decimals = 18,
            logoURI = null,
            providers = providers,
        )
}
