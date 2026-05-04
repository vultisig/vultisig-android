@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.OneInchToCoinsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TokenRepositoryImplTest {

    @Test
    fun `getTokensWithBalance for ThorChain skips x_staking-ruji denom`() = runTest {
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(
                CosmosBalance(denom = "x/staking-ruji", amount = "100"),
                CosmosBalance(denom = "x/ruji", amount = "200"),
            )
        coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null

        val coins = newRepository(thorApi).getTokensWithBalance(Chain.ThorChain, ADDRESS)

        assertTrue(coins.none { it.contractAddress == "x/staking-ruji" })
    }

    @Test
    fun `getTokensWithBalance for ThorChain keeps non-staking denoms`() = runTest {
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(
                CosmosBalance(denom = "x/staking-ruji", amount = "100"),
                CosmosBalance(denom = "x/ruji", amount = "200"),
                CosmosBalance(denom = "tcy", amount = "300"),
            )
        coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null

        val coins = newRepository(thorApi).getTokensWithBalance(Chain.ThorChain, ADDRESS)

        assertEquals(setOf("x/ruji", "tcy"), coins.map { it.contractAddress }.toSet())
    }

    @Test
    fun `getTokensWithBalance for ThorChain skips x_staking-ruji even with metadata`() = runTest {
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(CosmosBalance(denom = "x/staking-ruji", amount = "100"))
        coEvery { thorApi.getDenomMetaFromLCD("x/staking-ruji") } returns
            DenomMetadata(
                base = "x/staking-ruji",
                symbol = "sRUJI",
                display = null,
                denomUnits = null,
            )

        val coins = newRepository(thorApi).getTokensWithBalance(Chain.ThorChain, ADDRESS)

        assertTrue(coins.isEmpty())
    }

    @Test
    fun `getTokensWithBalance for ThorChain with empty enabledDenoms returns all non-excluded denoms`() =
        runTest {
            val thorApi: ThorChainApi = mockk(relaxed = true)
            coEvery { thorApi.getBalance(ADDRESS) } returns
                listOf(
                    CosmosBalance(denom = "x/ruji", amount = "100"),
                    CosmosBalance(denom = "tcy", amount = "200"),
                )
            coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null

            val coins =
                newRepository(thorApi).getTokensWithBalance(Chain.ThorChain, ADDRESS, emptySet())

            assertEquals(setOf("x/ruji", "tcy"), coins.map { it.contractAddress }.toSet())
        }

    @Test
    fun `getTokensWithBalance for ThorChain with non-empty enabledDenoms filters to only matching denoms`() =
        runTest {
            val thorApi: ThorChainApi = mockk(relaxed = true)
            coEvery { thorApi.getBalance(ADDRESS) } returns
                listOf(
                    CosmosBalance(denom = "x/ruji", amount = "100"),
                    CosmosBalance(denom = "tcy", amount = "200"),
                    CosmosBalance(denom = "btc/btc", amount = "50"),
                )
            coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null

            val coins =
                newRepository(thorApi)
                    .getTokensWithBalance(Chain.ThorChain, ADDRESS, setOf("x/ruji", "tcy"))

            assertEquals(setOf("x/ruji", "tcy"), coins.map { it.contractAddress }.toSet())
            coVerify(exactly = 0) { thorApi.getDenomMetaFromLCD("btc/btc") }
        }

    @Test
    fun `getTokensWithBalance for ThorChain enabledDenoms does not override defi-only filter`() =
        runTest {
            val thorApi: ThorChainApi = mockk(relaxed = true)
            coEvery { thorApi.getBalance(ADDRESS) } returns
                listOf(
                    CosmosBalance(denom = "x/staking-ruji", amount = "100"),
                    CosmosBalance(denom = "x/ruji", amount = "200"),
                )
            coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null

            val coins =
                newRepository(thorApi)
                    .getTokensWithBalance(
                        Chain.ThorChain,
                        ADDRESS,
                        setOf("x/staking-ruji", "x/ruji"),
                    )

            assertTrue(coins.none { it.contractAddress == "x/staking-ruji" })
            assertEquals(listOf("x/ruji"), coins.map { it.contractAddress })
        }

    private fun newRepository(thorApi: ThorChainApi): TokenRepositoryImpl =
        TokenRepositoryImpl(
            oneInchApi = mockk<OneInchApi>(relaxed = true),
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            thorApi = thorApi,
            coinGeckoApi = mockk<CoinGeckoApi>(relaxed = true),
            currencyRepository = mockk<AppCurrencyRepository>(relaxed = true),
            chainAccountAddressRepository = mockk<ChainAccountAddressRepository>(relaxed = true),
            oneInchToCoins = mockk<OneInchToCoinsUseCase>(relaxed = true),
        )

    private companion object {
        const val ADDRESS = "thor1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v"
    }
}
