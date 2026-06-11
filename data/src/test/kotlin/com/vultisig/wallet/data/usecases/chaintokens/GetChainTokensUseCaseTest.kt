@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.usecases.chaintokens

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
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
    private val vault: Vault = mockk()

    private val useCase =
        GetChainTokensUseCaseImpl(
            tokenRepository = tokenRepository,
            splTokenRepository = mockk(relaxed = true),
            oneInchApi = mockk(relaxed = true),
            oneInchToCoins = mockk(relaxed = true),
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

    private fun coin(chain: Chain, ticker: String, contractAddress: String) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "addr",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )
}
