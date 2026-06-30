package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.LiQuestApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class TokenPriceRepositoryImplTest {

    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var coinGeckoApi: CoinGeckoApi
    private lateinit var liQuestApi: LiQuestApi
    private lateinit var thorApi: ThorChainApi
    private lateinit var mayaApi: MayaChainApi
    private lateinit var tokenPriceDao: TokenPriceDao
    private lateinit var repository: TokenPriceRepositoryImpl

    @BeforeEach
    fun setUp() {
        appCurrencyRepository = mockk()
        coinGeckoApi = mockk()
        liQuestApi = mockk()
        thorApi = mockk()
        mayaApi = mockk()
        tokenPriceDao = mockk(relaxed = true)

        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        // No thor/maya tokens in these tests, so pool fetches are not exercised.

        repository =
            TokenPriceRepositoryImpl(
                appCurrencyRepository = appCurrencyRepository,
                coinGeckoApi = coinGeckoApi,
                liQuestApi = liQuestApi,
                thorApi = thorApi,
                mayaApi = mayaApi,
                tokenPriceDao = tokenPriceDao,
            )
    }

    private val ezEth =
        Coin(
            chain = Chain.Base,
            ticker = "ezETH",
            logo = "ezeth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ezETH",
            contractAddress = "0x2416092f143378750bb29b79eD961ab195CcEea5",
            isNativeToken = false,
        )

    @Test
    fun `falls back to contract-address lookup when priceProviderID returns no price`() = runTest {
        // CoinGecko does not recognize the "ezETH" id, so the provider-id lookup is empty.
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        // The contract-address lookup returns a valid price.
        coEvery { coinGeckoApi.getContractsPrice(eq(Chain.Base), any(), any()) } returns
            mapOf(ezEth.contractAddress to mapOf("usd" to BigDecimal("3500.0")))

        repository.refresh(listOf(ezEth))

        // The contract-address fallback must have been attempted for ezETH's contract.
        coVerify {
            coinGeckoApi.getContractsPrice(
                Chain.Base,
                match { it.contains(ezEth.contractAddress) },
                any(),
            )
        }

        val price = repository.getPrice(ezEth, AppCurrency.USD).first()
        assertEquals(BigDecimal("3500.0"), price)
    }

    @Test
    fun `getPrice falls back to the persisted price before any refresh`() = runTest {
        // Cold start: the in-memory map is empty, but Room holds a last-known price. getPrice must
        // serve it instead of $0 so decoupled balance fetches don't flash cached fiat to zero.
        coEvery { tokenPriceDao.getTokenPrice(ezEth.id, "usd") } returns "3500.0"

        val price = repository.getPrice(ezEth, AppCurrency.USD).first()

        assertEquals(BigDecimal("3500.0"), price)
    }

    @Test
    fun `getPrice prefers the refreshed in-memory price over the persisted price`() = runTest {
        // Room holds a stale price, but a refresh populated the in-memory map with a newer one.
        coEvery { tokenPriceDao.getTokenPrice(ezEth.id, "usd") } returns "3000.0"
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("ezETH" to mapOf("usd" to BigDecimal("3400.0")))

        repository.refresh(listOf(ezEth))
        val price = repository.getPrice(ezEth, AppCurrency.USD).first()

        assertEquals(BigDecimal("3400.0"), price)
    }

    @Test
    fun `does not call contract-address lookup when priceProviderID returns a price`() = runTest {
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("ezETH" to mapOf("usd" to BigDecimal("3400.0")))

        repository.refresh(listOf(ezEth))

        coVerify(exactly = 0) { coinGeckoApi.getContractsPrice(any(), any(), any()) }

        val price = repository.getPrice(ezEth, AppCurrency.USD).first()
        assertEquals(BigDecimal("3400.0"), price)
    }
}
