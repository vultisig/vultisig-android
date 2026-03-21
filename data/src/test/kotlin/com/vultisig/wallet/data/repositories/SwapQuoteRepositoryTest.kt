package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwapQuoteRepositoryTest {

    private val repository =
        SwapQuoteRepositoryImpl(
            thorChainApi = mockk(),
            mayaChainApi = mockk(),
            oneInchApi = mockk(),
            liFiChainApi = mockk(),
            jupiterApi = mockk(),
            kyberApi = mockk(),
        )

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String = "",
        isNativeToken: Boolean = contractAddress.isEmpty(),
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    @Test
    fun `SOL to USDC resolves to JUPITER not THORCHAIN`() {
        val sol = coin(Chain.Solana, "SOL")
        val usdc =
            coin(
                Chain.Solana,
                "USDC",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            )

        val provider = repository.resolveProvider(sol, usdc)

        assertNotNull(provider)
        assertEquals(SwapProvider.JUPITER, provider)
    }

    @Test
    fun `USDC to SOL resolves to JUPITER not THORCHAIN`() {
        val sol = coin(Chain.Solana, "SOL")
        val usdc =
            coin(
                Chain.Solana,
                "USDC",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            )

        val provider = repository.resolveProvider(usdc, sol)

        assertNotNull(provider)
        assertEquals(SwapProvider.JUPITER, provider)
    }

    @Test
    fun `SOL to SOL cross-chain resolves to THORCHAIN`() {
        val sol = coin(Chain.Solana, "SOL")
        val btc = coin(Chain.Bitcoin, "BTC")

        val provider = repository.resolveProvider(sol, btc)

        assertNotNull(provider)
        assertEquals(SwapProvider.THORCHAIN, provider)
    }

    @Test
    fun `Solana USDC to ETH cross-chain resolves to LIFI`() {
        val usdc =
            coin(
                Chain.Solana,
                "USDC",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            )
        val eth = coin(Chain.Ethereum, "ETH")

        val provider = repository.resolveProvider(usdc, eth)

        assertNotNull(provider)
        assertEquals(SwapProvider.LIFI, provider)
    }

    @Test
    fun `Solana SPL token gets JUPITER and LIFI but not THORCHAIN`() {
        val usdt =
            coin(
                Chain.Solana,
                "USDT",
                contractAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
            )
        val sol = coin(Chain.Solana, "SOL")

        val provider = repository.resolveProvider(usdt, sol)

        assertNotNull(provider)
        assertTrue(provider == SwapProvider.JUPITER || provider == SwapProvider.LIFI)
    }

    @Test
    fun `unsupported chain returns null`() {
        val sui1 = coin(Chain.Sui, "SUI")
        val sui2 = coin(Chain.Sui, "USDC", contractAddress = "0xabc")

        val provider = repository.resolveProvider(sui1, sui2)

        assertNull(provider)
    }
}
