@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.CustomTokenResponse
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.CardanoTokenFinder
import com.vultisig.wallet.data.usecases.CosmosBankCoinFinder
import com.vultisig.wallet.data.usecases.EvmCoinFinder
import com.vultisig.wallet.data.usecases.RippleTokenFinder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `getTokensWithBalance for Ripple delegates to the trust-line finder`() = runTest {
        val rippleTokenFinder: RippleTokenFinder = mockk()
        val expected =
            listOf(
                Coin.EMPTY.copy(
                    chain = Chain.Ripple,
                    ticker = "USD",
                    contractAddress = "USD.rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B",
                )
            )
        coEvery { rippleTokenFinder.find(RIPPLE_ADDRESS) } returns expected

        val coins =
            newRepository(rippleTokenFinder = rippleTokenFinder)
                .getTokensWithBalance(Chain.Ripple, RIPPLE_ADDRESS)

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

    /**
     * A THORChain balance is described by its denom and whatever the LCD says about it — never a
     * display name — so the coin the picker searches has to borrow the curated one's.
     */
    @Test
    fun `getRefreshTokens names a discovered curated denom from the catalogue`() = runTest {
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(CosmosBalance(denom = "x/ruji", amount = "200"))
        coEvery { thorApi.getDenomMetaFromLCD(any()) } returns null
        val addresses: ChainAccountAddressRepository = mockk()
        coEvery { addresses.getAddress(Chain.ThorChain, any()) } returns (ADDRESS to "pub")

        val coins =
            newRepository(thorApi, chainAccountAddressRepository = addresses)
                .getRefreshTokens(Chain.ThorChain, Vault(id = "vault-1", name = "Main"))

        val ruji = coins.single { it.contractAddress == "x/ruji" }
        assertEquals(Coins.ThorChain.RUJI.name, ruji.name)
        assertEquals(ADDRESS, ruji.address)
    }

    @Test
    fun `getRefreshTokens keeps the name the node reported over the catalogue's`() = runTest {
        val thorApi: ThorChainApi = mockk(relaxed = true)
        coEvery { thorApi.getBalance(ADDRESS) } returns
            listOf(CosmosBalance(denom = "x/ruji", amount = "200"))
        coEvery { thorApi.getDenomMetaFromLCD("x/ruji") } returns
            DenomMetadata(
                base = "x/ruji",
                symbol = "RUJI",
                display = "ruji",
                denomUnits = null,
                name = "Rujira Token",
            )
        val addresses: ChainAccountAddressRepository = mockk()
        coEvery { addresses.getAddress(Chain.ThorChain, any()) } returns (ADDRESS to "pub")

        val coins =
            newRepository(thorApi, chainAccountAddressRepository = addresses)
                .getRefreshTokens(Chain.ThorChain, Vault(id = "vault-1", name = "Main"))

        assertEquals("Rujira Token", coins.single { it.contractAddress == "x/ruji" }.name)
    }

    @Test
    fun `getEVMTokenByContract decodes the token's symbol and decimals`() = runTest {
        val repository = newRepository(evmApiFactory = evmApiFactoryAnswering(decimals = "0x12"))

        val coin = repository.getEVMTokenByContract(Chain.Ethereum.id, CONTRACT)

        assertEquals("USDC", coin?.ticker)
        assertEquals(18, coin?.decimal)
    }

    @Test
    fun `getEVMTokenByContract returns null when the contract has no decimals`() = runTest {
        // A contract that implements symbol() but not decimals() answers `0x` for the second call.
        // Decoding that with BigInteger threw NumberFormatException out of the repository, and
        // CustomTokenViewModel launches the lookup without a try, so it crashed the screen.
        val repository = newRepository(evmApiFactory = evmApiFactoryAnswering(decimals = "0x"))

        assertNull(repository.getEVMTokenByContract(Chain.Ethereum.id, CONTRACT))
    }

    @Test
    fun `getEVMTokenByContract returns null when decimals is a full-width word`() = runTest {
        // 2^256-1 wraps to -1 in toInt(); it used to pass the `!= 0` check and reach the Coin.
        val repository =
            newRepository(evmApiFactory = evmApiFactoryAnswering(decimals = "0x" + "f".repeat(64)))

        assertNull(repository.getEVMTokenByContract(Chain.Ethereum.id, CONTRACT))
    }

    private fun evmApiFactoryAnswering(decimals: String): EvmApiFactory {
        val evmApi = mockk<EvmApi>(relaxed = true)
        coEvery { evmApi.findCustomToken(CONTRACT) } returns
            listOf(
                CustomTokenResponse(id = 2, result = USDC_SYMBOL_RESULT),
                CustomTokenResponse(id = 3, result = decimals),
            )
        return mockk<EvmApiFactory>(relaxed = true).also {
            every { it.createEvmApi(Chain.Ethereum) } returns evmApi
        }
    }

    private fun newRepository(
        thorApi: ThorChainApi = mockk(relaxed = true),
        evmCoinFinder: EvmCoinFinder = mockk(relaxed = true),
        cosmosBankCoinFinder: CosmosBankCoinFinder = mockk(relaxed = true),
        rippleTokenFinder: RippleTokenFinder = mockk(relaxed = true),
        cardanoTokenFinder: CardanoTokenFinder = mockk(relaxed = true),
        chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true),
        evmApiFactory: EvmApiFactory = mockk(relaxed = true),
    ): TokenRepositoryImpl =
        TokenRepositoryImpl(
            evmApiFactory = evmApiFactory,
            thorApi = thorApi,
            chainAccountAddressRepository = chainAccountAddressRepository,
            evmCoinFinder = evmCoinFinder,
            cosmosBankCoinFinder = cosmosBankCoinFinder,
            rippleTokenFinder = rippleTokenFinder,
            cardanoTokenFinder = cardanoTokenFinder,
        )

    private companion object {
        const val CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

        // Standard ABI `string` return: offset word, length word, then "USDC" right-padded.
        const val USDC_SYMBOL_RESULT =
            "0x0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000004" +
                "5553444300000000000000000000000000000000000000000000000000000000"

        const val ADDRESS = "thor1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v"
        const val TERRA_ADDRESS = "terra1abc"
        const val TERRA_CLASSIC_ADDRESS = "terra1classic"
        const val COSMOS_ADDRESS = "cosmos1abc"
        const val RIPPLE_ADDRESS = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
    }
}
