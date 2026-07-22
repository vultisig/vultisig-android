@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.usecases.chaintokens

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ThorChainSecuredAssetRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class GetChainTokensUseCaseTest {

    private val tokenRepository: TokenRepository = mockk()
    private val securedAssetRepository: ThorChainSecuredAssetRepository = mockk()
    private val vault: Vault = mockk()

    private val useCase =
        GetChainTokensUseCaseImpl(
            tokenRepository = tokenRepository,
            splTokenRepository = mockk(relaxed = true),
            oneInchApi = mockk(relaxed = true),
            oneInchToCoins = mockk(relaxed = true),
            securedAssetRepository = securedAssetRepository,
        )

    @Test
    fun `collapses a ticker sourced with two contract addresses on one chain to one row`() =
        runTest {
            // Regression for #4869: the LazyColumn keys on Coin.id (ticker-chainId), so two coins
            // with the same ticker but different contracts on one chain must not both survive —
            // otherwise the list crashes with a duplicate-key IllegalArgumentException on scroll.
            val chain = Chain.Bitcoin
            val builtIn = coin(chain, ticker = "cbETH", contractAddress = "0xA")
            val provider = coin(chain, ticker = "cbETH", contractAddress = "0xB")
            every { tokenRepository.builtInTokens } returns flowOf(listOf(builtIn, provider))
            coEvery { tokenRepository.getRefreshTokens(any(), any()) } returns emptyList()

            val emitted = useCase(chain, vault).toList().last()

            val key = "cbETH-${chain.id}"
            assertEquals(1, emitted.count { it.id == key })
            // No two emitted coins ever share a LazyColumn key.
            assertEquals(emitted.size, emitted.distinctBy { it.id }.size)
            // The canonical built-in entry (ordered first) wins over the provider duplicate.
            assertEquals("0xA", emitted.first { it.id == key }.contractAddress)
        }

    @Test
    fun `keeps distinct tickers on the same chain`() = runTest {
        val chain = Chain.Bitcoin
        val a = coin(chain, ticker = "AAA", contractAddress = "0x1")
        val b = coin(chain, ticker = "BBB", contractAddress = "0x2")
        every { tokenRepository.builtInTokens } returns flowOf(listOf(a, b))
        coEvery { tokenRepository.getRefreshTokens(any(), any()) } returns emptyList()

        val emitted = useCase(chain, vault).toList().last()

        assertEquals(setOf("AAA-${chain.id}", "BBB-${chain.id}"), emitted.map { it.id }.toSet())
    }

    @Test
    fun `merges the secured-asset catalog into the ThorChain token list`() = runTest {
        val rune = coin(Chain.ThorChain, ticker = "RUNE", contractAddress = "")
        val btcSecured = coin(Chain.ThorChain, ticker = "BTC", contractAddress = "btc-btc")
        every { tokenRepository.builtInTokens } returns flowOf(listOf(rune))
        coEvery { tokenRepository.getRefreshTokens(any(), any()) } returns emptyList()
        coEvery { securedAssetRepository.getSecuredAssetCoins() } returns listOf(btcSecured)

        val emitted = useCase(Chain.ThorChain, vault).toList().last()

        assertEquals(setOf(rune, btcSecured), emitted.toSet())
    }

    @Test
    fun `two secured assets sharing a ticker on different underlying chains both survive`() =
        runTest {
            // Johnny's P1: ETH.USDC and AVAX.USDC both have ticker USDC and chain=ThorChain, so
            // they'd collide on the plain Coin.id-based dedup and silently drop one.
            val ethUsdc =
                coin(
                    Chain.ThorChain,
                    ticker = "USDC",
                    contractAddress = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                )
            val avaxUsdc =
                coin(
                    Chain.ThorChain,
                    ticker = "USDC",
                    contractAddress = "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e",
                )
            every { tokenRepository.builtInTokens } returns flowOf(emptyList())
            coEvery { tokenRepository.getRefreshTokens(any(), any()) } returns emptyList()
            coEvery { securedAssetRepository.getSecuredAssetCoins() } returns
                listOf(ethUsdc, avaxUsdc)

            val emitted = useCase(Chain.ThorChain, vault).toList().last()

            assertEquals(setOf(ethUsdc, avaxUsdc), emitted.toSet())
        }

    @Test
    fun `an already-held secured asset wins over its zero-balance catalog duplicate`() = runTest {
        val heldBtcSecured =
            coin(Chain.ThorChain, ticker = "BTC", contractAddress = "btc-btc", decimal = 8)
        val catalogBtcSecured = coin(Chain.ThorChain, ticker = "BTC", contractAddress = "btc-btc")
        every { tokenRepository.builtInTokens } returns flowOf(emptyList())
        coEvery { tokenRepository.getRefreshTokens(any(), any()) } returns listOf(heldBtcSecured)
        coEvery { securedAssetRepository.getSecuredAssetCoins() } returns listOf(catalogBtcSecured)

        val emitted = useCase(Chain.ThorChain, vault).toList().last()

        assertEquals(listOf(heldBtcSecured), emitted)
    }

    private fun coin(chain: Chain, ticker: String, contractAddress: String, decimal: Int = 18) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "addr",
            decimal = decimal,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )
}
