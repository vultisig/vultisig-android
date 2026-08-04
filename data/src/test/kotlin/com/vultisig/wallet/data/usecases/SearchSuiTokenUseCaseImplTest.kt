package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.SuiCoinMetadata
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SearchSuiTokenUseCaseImplTest {

    private val suiApi: SuiApi = mockk()
    private val tokenPriceRepository: TokenPriceRepository = mockk()

    private val useCase = SearchSuiTokenUseCaseImpl(suiApi, tokenPriceRepository)

    @Test
    fun `curated coin type resolves via catalog logo and price without an RPC call`() = runTest {
        // Zero-padded form of the catalog's DEEP contract address.
        val padded =
            "0x0deeb7a4662eec9f2f3def03fb937a663dddaa2e215b8078a284d026b7946c270::deep::DEEP"
        coEvery { tokenPriceRepository.getPriceByPriceProviderId("deep") } returns
            BigDecimal("0.05")

        val result = useCase(padded)

        assertEquals("DEEP", result?.coin?.ticker)
        assertEquals(
            "https://s2.coinmarketcap.com/static/img/coins/64x64/33391.png",
            result?.coin?.logo,
        )
        assertEquals(BigDecimal("0.05"), result?.price)
        coVerify(exactly = 0) { suiApi.getCoinMetadata(any()) }
    }

    @Test
    fun `uncatalogued coin type falls back to on-chain metadata with zero price`() = runTest {
        val coinType = "0xabc::widget::WIDGET"
        coEvery { suiApi.getCoinMetadata(coinType) } returns
            SuiCoinMetadata(decimals = 9, symbol = "WIDGET", iconUrl = "https://example.com/w.png")

        val result = useCase(coinType)

        assertEquals("WIDGET", result?.coin?.ticker)
        assertEquals("https://example.com/w.png", result?.coin?.logo)
        assertEquals(BigDecimal.ZERO, result?.price)
    }

    @Test
    fun `node with no metadata returns null`() = runTest {
        val coinType = "0xabc::widget::WIDGET"
        coEvery { suiApi.getCoinMetadata(coinType) } returns null

        assertNull(useCase(coinType))
    }
}
