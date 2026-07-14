@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.CosmosBankCoinFinder
import com.vultisig.wallet.data.usecases.EvmCoinFinder
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

    @Test
    fun `getTokensWithBalance surfaces bRUNE even when it is not in enabledDenoms`() = runTest {
        // A fresh holder only ever seeded native RUNE, so enabledDenoms won't contain bRUNE. The
        // curated liquid-bonding denom must bypass the gate and auto-surface anyway.
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(
                CosmosBalance(denom = Coins.ThorChain.bRUNE.contractAddress, amount = "100"),
                CosmosBalance(denom = "btc/btc", amount = "50"),
            )
        coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null

        val coins =
            newRepository(thorApi)
                .getTokensWithBalance(Chain.ThorChain, ADDRESS, enabledDenoms = setOf("rune"))

        val bRune = coins.single { it.contractAddress == Coins.ThorChain.bRUNE.contractAddress }
        assertEquals(Coins.ThorChain.bRUNE.ticker, bRune.ticker)
        // A non-curated denom stays gated out.
        assertTrue(coins.none { it.contractAddress == "btc/btc" })
    }

    @Test
    fun `getTokensWithBalance canonicalizes bRUNE decimal to the curated value`() = runTest {
        // The denom-metadata ladder reports a non-8 exponent; the curated override must reset
        // decimal alongside ticker/contractAddress so the discovered coin can't disagree with the
        // curated definition.
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(CosmosBalance(denom = Coins.ThorChain.bRUNE.contractAddress, amount = "100"))
        coEvery { thorApi.getDenomMetaFromLCD(Coins.ThorChain.bRUNE.contractAddress) } returns
            DenomMetadata(
                base = Coins.ThorChain.bRUNE.contractAddress,
                symbol = "x/brune",
                display = null,
                denomUnits =
                    listOf(
                        com.vultisig.wallet.data.api.models.DenomUnit(
                            denom = "x/brune",
                            exponent = 6,
                        )
                    ),
            )

        val coins = newRepository(thorApi).getTokensWithBalance(Chain.ThorChain, ADDRESS)

        val bRune = coins.single()
        assertEquals(Coins.ThorChain.bRUNE.decimal, bRune.decimal)
    }

    @Test
    fun `getTokensWithBalance for Terra delegates to the Cosmos bank coin finder`() = runTest {
        val cosmosBankCoinFinder: CosmosBankCoinFinder = mockk()
        val expected = listOf(Coins.Terra.ASTRO_IBC)
        coEvery { cosmosBankCoinFinder.find(Chain.Terra, TERRA_ADDRESS) } returns expected

        val coins =
            newRepository(cosmosBankCoinFinder = cosmosBankCoinFinder)
                .getTokensWithBalance(Chain.Terra, TERRA_ADDRESS)

        assertEquals(expected, coins)
    }

    @Test
    fun `getTokensWithBalance for TerraClassic delegates to the Cosmos bank coin finder`() =
        runTest {
            val cosmosBankCoinFinder: CosmosBankCoinFinder = mockk()
            val expected = listOf(Coins.TerraClassic.USTC)
            coEvery { cosmosBankCoinFinder.find(Chain.TerraClassic, TERRA_CLASSIC_ADDRESS) } returns
                expected

            val coins =
                newRepository(cosmosBankCoinFinder = cosmosBankCoinFinder)
                    .getTokensWithBalance(Chain.TerraClassic, TERRA_CLASSIC_ADDRESS)

            assertEquals(expected, coins)
        }

    @Test
    fun `getTokensWithBalance leaves other Cosmos chains untouched and returns empty`() = runTest {
        // Defensive: the dispatch must scope the new finder to Terra / TerraClassic only — the
        // ticket explicitly limits this discovery to those two chains.
        val cosmosBankCoinFinder: CosmosBankCoinFinder = mockk()

        val coins =
            newRepository(cosmosBankCoinFinder = cosmosBankCoinFinder)
                .getTokensWithBalance(Chain.GaiaChain, COSMOS_ADDRESS)

        assertTrue(coins.isEmpty())
        coVerify(exactly = 0) { cosmosBankCoinFinder.find(any(), any()) }
    }

    private fun newRepository(
        thorApi: ThorChainApi = mockk(relaxed = true),
        evmCoinFinder: EvmCoinFinder = mockk(relaxed = true),
        cosmosBankCoinFinder: CosmosBankCoinFinder = mockk(relaxed = true),
    ): TokenRepositoryImpl =
        TokenRepositoryImpl(
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            thorApi = thorApi,
            chainAccountAddressRepository = mockk<ChainAccountAddressRepository>(relaxed = true),
            evmCoinFinder = evmCoinFinder,
            cosmosBankCoinFinder = cosmosBankCoinFinder,
        )

    private companion object {
        const val ADDRESS = "thor1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v"
        const val TERRA_ADDRESS = "terra1abc"
        const val TERRA_CLASSIC_ADDRESS = "terra1classic"
        const val COSMOS_ADDRESS = "cosmos1abc"
    }
}
