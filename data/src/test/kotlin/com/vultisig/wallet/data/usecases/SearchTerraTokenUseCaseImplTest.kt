package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.cosmos.Cw20TokenInfoJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SearchTerraTokenUseCaseImplTest {

    private val cosmosApi: CosmosApi = mockk()
    private val cosmosApiFactory: CosmosApiFactory = mockk {
        every { createCosmosApi(any()) } returns cosmosApi
    }

    private val useCase = SearchTerraTokenUseCaseImpl(cosmosApiFactory)

    @Test
    fun `resolves an unknown CW20 contract to a coin with token_info metadata`() = runTest {
        givenTokenInfo(Cw20TokenInfoJson(name = "Some Token", symbol = "STK", decimals = 8))

        val result = useCase(Chain.Terra, CONTRACT)

        val coin = result?.coin
        assertEquals(Chain.Terra, coin?.chain)
        assertEquals("STK", coin?.ticker)
        assertEquals(8, coin?.decimal)
        assertEquals(CONTRACT, coin?.contractAddress)
        assertEquals("", coin?.logo)
        assertEquals("", coin?.priceProviderID)
        assertEquals(false, coin?.isNativeToken)
        assertEquals(BigDecimal.ZERO, result?.price)
    }

    @Test
    fun `prefers the curated catalog entry so known tokens keep logo and price id`() = runTest {
        givenTokenInfo(Cw20TokenInfoJson(name = "Astroport", symbol = "ASTRO", decimals = 6))

        val result = useCase(Chain.Terra, Coins.Terra.ASTRO.contractAddress)

        assertEquals(Coins.Terra.ASTRO, result?.coin)
    }

    @Test
    fun `resolves on Terra Classic via the Terra Classic api`() = runTest {
        every { cosmosApiFactory.createCosmosApi(Chain.TerraClassic) } returns cosmosApi
        givenTokenInfo(Cw20TokenInfoJson(name = "Classic Token", symbol = "CLS", decimals = 6))

        val result = useCase(Chain.TerraClassic, CONTRACT)

        assertEquals(Chain.TerraClassic, result?.coin?.chain)
        assertEquals("CLS", result?.coin?.ticker)
    }

    @Test
    fun `trims whitespace around the symbol`() = runTest {
        givenTokenInfo(Cw20TokenInfoJson(symbol = "  STK  ", decimals = 6))

        assertEquals("STK", useCase(Chain.Terra, CONTRACT)?.coin?.ticker)
    }

    @Test
    fun `null token_info returns null`() = runTest {
        givenTokenInfo(null)

        assertNull(useCase(Chain.Terra, CONTRACT))
    }

    @Test
    fun `blank symbol returns null`() = runTest {
        givenTokenInfo(Cw20TokenInfoJson(name = "No Symbol", symbol = "   ", decimals = 6))

        assertNull(useCase(Chain.Terra, CONTRACT))
    }

    @Test
    fun `missing decimals returns null`() = runTest {
        givenTokenInfo(Cw20TokenInfoJson(name = "No Decimals", symbol = "STK", decimals = null))

        assertNull(useCase(Chain.Terra, CONTRACT))
    }

    @Test
    fun `negative decimals returns null`() = runTest {
        givenTokenInfo(Cw20TokenInfoJson(symbol = "STK", decimals = -1))

        assertNull(useCase(Chain.Terra, CONTRACT))
    }

    @Test
    fun `api failure returns null instead of throwing`() = runTest {
        coEvery { cosmosApi.getCw20TokenInfo(CONTRACT) } throws RuntimeException("boom")

        assertNull(useCase(Chain.Terra, CONTRACT))
    }

    private fun givenTokenInfo(info: Cw20TokenInfoJson?) {
        coEvery { cosmosApi.getCw20TokenInfo(any()) } returns info
    }

    private companion object {
        /** Not in the [Coins] catalog, so tests exercise the unknown-token path. */
        const val CONTRACT = "terra1unknown0token0contract0address0qqqqqqqqqqqqqqqqqqqqqqqqqqqq"
    }
}
