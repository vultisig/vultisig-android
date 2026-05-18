@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.DenomUnit
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceDenomTraceJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class CosmosBankCoinFinderTest {

    private val cosmosApi: CosmosApi = mockk()
    private val cosmosApiFactory: CosmosApiFactory = mockk()
    private val finder = CosmosBankCoinFinderImpl(cosmosApiFactory)

    init {
        every { cosmosApiFactory.createCosmosApi(any()) } returns cosmosApi
    }

    @Test
    fun `SUPPORTED_CHAINS matches the ticket scope`() {
        // Mirrors vultisig/vultisig-android#4500 and the parallel iOS work in
        // vultisig/vultisig-ios#4366; widening this set is a deliberate cross-platform decision.
        assertEquals(
            setOf(Chain.Terra, Chain.TerraClassic),
            CosmosBankCoinFinderImpl.SUPPORTED_CHAINS,
        )
    }

    @Test
    fun `find on a non-supported Cosmos chain returns empty without calling the API`() = runTest {
        val coins = finder.find(Chain.GaiaChain, TERRA_ADDRESS)

        assertTrue(coins.isEmpty())
        coVerify(exactly = 0) { cosmosApi.getBalance(any()) }
    }

    @Test
    fun `find skips the native fee unit denom`() = runTest {
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(
                CosmosBalance(denom = "uluna", amount = "100"),
                CosmosBalance(denom = "uusdc-stub", amount = "200"),
            )
        coEvery { cosmosApi.getDenomMetadata(any()) } returns null

        val coins = finder.find(Chain.Terra, TERRA_ADDRESS)

        assertEquals(listOf("uusdc-stub"), coins.map { it.contractAddress })
        coVerify(exactly = 0) { cosmosApi.getDenomMetadata("uluna") }
    }

    @Test
    fun `find prefers the curated catalog entry when the denom matches case-insensitively`() =
        runTest {
            // USTC on TerraClassic is curated with contractAddress "uusd"; the catalog entry should
            // win over any metadata-derived coin so the bundled logo + priceProviderID are
            // preserved.
            coEvery { cosmosApi.getBalance(TERRA_CLASSIC_ADDRESS) } returns
                listOf(CosmosBalance(denom = "UUSD", amount = "100"))

            val coins = finder.find(Chain.TerraClassic, TERRA_CLASSIC_ADDRESS)

            val ustc = coins.single()
            assertEquals(Coins.TerraClassic.USTC, ustc)
            coVerify(exactly = 0) { cosmosApi.getDenomMetadata(any()) }
        }

    @Test
    fun `find reads ticker from metadata symbol and decimals from the matching denom_unit`() =
        runTest {
            // Acceptance criterion: "Decimals correct for at least one non-6-decimal denom." A bank
            // denom whose metadata declares an 18-exponent display unit (the Axelar WETH shape)
            // must surface as 18 decimals, not the Cosmos default of 6.
            coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
                listOf(CosmosBalance(denom = "axlweth", amount = "1"))
            coEvery { cosmosApi.getDenomMetadata("axlweth") } returns
                DenomMetadata(
                    base = "axlweth",
                    symbol = "axlWETH",
                    display = "axlweth",
                    denomUnits =
                        listOf(
                            DenomUnit(denom = "axlweth", exponent = 0),
                            DenomUnit(denom = "axlWETH", exponent = 18),
                        ),
                )

            val coin = finder.find(Chain.Terra, TERRA_ADDRESS).single()

            assertEquals("axlweth", coin.contractAddress)
            assertEquals("axlWETH", coin.ticker)
            assertEquals(18, coin.decimal)
            assertEquals("", coin.priceProviderID)
            assertEquals(false, coin.isNativeToken)
        }

    @Test
    fun `find falls back to display when metadata symbol is blank`() = runTest {
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = "uosmo", amount = "1"))
        coEvery { cosmosApi.getDenomMetadata("uosmo") } returns
            DenomMetadata(
                base = "uosmo",
                symbol = "",
                display = "OSMO",
                denomUnits =
                    listOf(
                        DenomUnit(denom = "uosmo", exponent = 0),
                        DenomUnit(denom = "OSMO", exponent = 6),
                    ),
            )

        val coin = finder.find(Chain.Terra, TERRA_ADDRESS).single()

        assertEquals("OSMO", coin.ticker)
        assertEquals(6, coin.decimal)
    }

    @Test
    fun `find without metadata derives the ticker by stripping the leading u or a unit prefix`() =
        runTest {
            coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
                listOf(
                    CosmosBalance(denom = "ufoo", amount = "1"),
                    CosmosBalance(denom = "abar", amount = "1"),
                    CosmosBalance(denom = "BAZ", amount = "1"),
                )
            coEvery { cosmosApi.getDenomMetadata(any()) } returns null

            val coins = finder.find(Chain.Terra, TERRA_ADDRESS).associateBy { it.contractAddress }

            assertEquals("FOO", coins.getValue("ufoo").ticker)
            assertEquals("BAR", coins.getValue("abar").ticker)
            assertEquals("BAZ", coins.getValue("BAZ").ticker)
            assertEquals(6, coins.getValue("ufoo").decimal, "Cosmos default decimals when no meta")
        }

    @Test
    fun `find derives the ticker from the last factory subdenom segment`() = runTest {
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = "factory/terra1abc/upips", amount = "1"))
        coEvery { cosmosApi.getDenomMetadata("factory/terra1abc/upips") } returns null

        val coin = finder.find(Chain.Terra, TERRA_ADDRESS).single()

        assertEquals("PIPS", coin.ticker)
    }

    @Test
    fun `find resolves an IBC voucher via denom_traces and applies base-denom metadata`() =
        runTest {
            val ibcDenom = "ibc/E3D7E58DD5BB1FBC1289A60CDBFCCDA0023B3DE3D2C347D9E1FFEEA4F1A1AE03"
            coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
                listOf(CosmosBalance(denom = ibcDenom, amount = "1"))
            // Chain has no metadata for the IBC voucher itself ...
            coEvery { cosmosApi.getDenomMetadata(ibcDenom) } returns null
            coEvery { cosmosApi.getIbcDenomTraces(ibcDenom) } returns
                CosmosIbcDenomTraceDenomTraceJson(path = "transfer/channel-1", baseDenom = "uusdc")
            // ... but it does for the unwrapped base denom, which the finder must apply.
            coEvery { cosmosApi.getDenomMetadata("uusdc") } returns
                DenomMetadata(
                    base = "uusdc",
                    symbol = "USDC",
                    display = "usdc",
                    denomUnits =
                        listOf(
                            DenomUnit(denom = "uusdc", exponent = 0),
                            DenomUnit(denom = "usdc", exponent = 6),
                        ),
                )

            val coin = finder.find(Chain.Terra, TERRA_ADDRESS).single()

            assertEquals(ibcDenom, coin.contractAddress)
            assertEquals("USDC", coin.ticker)
            assertEquals(6, coin.decimal)
        }

    @Test
    fun `find falls back to base-denom-derived ticker when IBC base-denom has no metadata`() =
        runTest {
            val ibcDenom = "ibc/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
                listOf(CosmosBalance(denom = ibcDenom, amount = "1"))
            coEvery { cosmosApi.getDenomMetadata(any()) } returns null
            coEvery { cosmosApi.getIbcDenomTraces(ibcDenom) } returns
                CosmosIbcDenomTraceDenomTraceJson(path = "transfer/channel-72", baseDenom = "uatom")

            val coin = finder.find(Chain.Terra, TERRA_ADDRESS).single()

            assertEquals(ibcDenom, coin.contractAddress)
            assertEquals("ATOM", coin.ticker)
            assertEquals(6, coin.decimal)
        }

    @Test
    fun `find falls back to a hashed ticker when the IBC trace lookup fails`() = runTest {
        val ibcDenom = "ibc/DEAD0000000000000000000000000000000000000000000000000000000000"
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = ibcDenom, amount = "1"))
        coEvery { cosmosApi.getDenomMetadata(any()) } returns null
        coEvery { cosmosApi.getIbcDenomTraces(ibcDenom) } throws RuntimeException("404")

        val coin = finder.find(Chain.Terra, TERRA_ADDRESS).single()

        assertEquals(ibcDenom, coin.contractAddress)
        assertEquals("IBC-DEAD00", coin.ticker)
    }

    @Test
    fun `find returns empty when the balance call fails`() = runTest {
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } throws RuntimeException("boom")

        val coins = finder.find(Chain.Terra, TERRA_ADDRESS)

        assertTrue(coins.isEmpty())
        coVerify(exactly = 0) { cosmosApi.getDenomMetadata(any()) }
        coVerify(exactly = 0) { cosmosApi.getIbcDenomTraces(any()) }
    }

    @Test
    fun `find caches denom metadata across calls for the same chain and denom`() = runTest {
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = "axlweth", amount = "1"))
        coEvery { cosmosApi.getDenomMetadata("axlweth") } returns
            DenomMetadata(
                base = "axlweth",
                symbol = "axlWETH",
                display = "axlweth",
                denomUnits =
                    listOf(
                        DenomUnit(denom = "axlweth", exponent = 0),
                        DenomUnit(denom = "axlWETH", exponent = 18),
                    ),
            )

        finder.find(Chain.Terra, TERRA_ADDRESS)
        finder.find(Chain.Terra, TERRA_ADDRESS)

        coVerify(exactly = 1) { cosmosApi.getDenomMetadata("axlweth") }
    }

    @Test
    fun `find caches IBC denom traces across calls for the same chain and denom`() = runTest {
        val ibcDenom = "ibc/CACHE0000000000000000000000000000000000000000000000000000000000"
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = ibcDenom, amount = "1"))
        coEvery { cosmosApi.getDenomMetadata(any()) } returns null
        coEvery { cosmosApi.getIbcDenomTraces(ibcDenom) } returns
            CosmosIbcDenomTraceDenomTraceJson(path = "transfer/channel-9", baseDenom = "uatom")

        finder.find(Chain.Terra, TERRA_ADDRESS)
        finder.find(Chain.Terra, TERRA_ADDRESS)

        coVerify(exactly = 1) { cosmosApi.getIbcDenomTraces(ibcDenom) }
    }

    @Test
    fun `find returns an empty list when the address holds only the native fee unit`() = runTest {
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = "uluna", amount = "100"))

        val coins = finder.find(Chain.Terra, TERRA_ADDRESS)

        assertTrue(coins.isEmpty())
        coVerify(exactly = 0) { cosmosApi.getDenomMetadata(any()) }
    }

    @Test
    fun `find does not cache null metadata so transient LCD failures retry next refresh`() =
        runTest {
            // A `null` from `getDenomMetadata` is indistinguishable between a true 404 and a 5xx
            // the
            // API method swallowed. Pinning either for 24h would freeze a transient LCD outage into
            // the session — instead the next refresh must hit the endpoint again.
            coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
                listOf(CosmosBalance(denom = "uglitch", amount = "1"))
            coEvery { cosmosApi.getDenomMetadata("uglitch") } returns null

            finder.find(Chain.Terra, TERRA_ADDRESS)
            finder.find(Chain.Terra, TERRA_ADDRESS)

            coVerify(exactly = 2) { cosmosApi.getDenomMetadata("uglitch") }
        }

    @Test
    fun `find does not cache failing IBC denom traces so the next refresh retries`() = runTest {
        val ibcDenom = "ibc/FFFF0000000000000000000000000000000000000000000000000000000000"
        coEvery { cosmosApi.getBalance(TERRA_ADDRESS) } returns
            listOf(CosmosBalance(denom = ibcDenom, amount = "1"))
        coEvery { cosmosApi.getDenomMetadata(any()) } returns null
        coEvery { cosmosApi.getIbcDenomTraces(ibcDenom) } throws RuntimeException("LCD down")

        finder.find(Chain.Terra, TERRA_ADDRESS)
        finder.find(Chain.Terra, TERRA_ADDRESS)

        coVerify(exactly = 2) { cosmosApi.getIbcDenomTraces(ibcDenom) }
    }

    private companion object {
        const val TERRA_ADDRESS = "terra1abc"
        const val TERRA_CLASSIC_ADDRESS = "terra1classic"
    }
}
