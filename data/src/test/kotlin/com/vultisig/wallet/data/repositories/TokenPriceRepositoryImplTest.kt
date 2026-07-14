package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.LiQuestApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.VaultRedemptionResponseJson
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
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
import kotlinx.serialization.json.Json
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

    private val bRune = Coins.ThorChain.bRUNE
    private val ybRune = Coins.ThorChain.ybRUNE

    // Silences the CoinGecko/LiFi contract-address fallback that any THORChain bank denom (empty
    // priceProviderID) is fanned through, so only fetchThorContractPrices sets bRUNE/ybRUNE prices.
    private fun stubEmptyContractFallback() {
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { liQuestApi.getLifiContractPriceUsd(any(), any()) } throws
            RuntimeException("no lifi")
        coEvery { thorApi.getPools() } returns emptyList()
    }

    // BigDecimal.equals is scale-sensitive (5.0 != 5.00000000); compare by value instead.
    private fun assertPriceEquals(expected: String, actual: BigDecimal) =
        assertEquals(
            0,
            BigDecimal(expected).compareTo(actual),
            "expected $expected but was $actual",
        )

    private fun redemption(bondSize: String, bondShares: String): VaultRedemptionResponseJson =
        Json.decodeFromString(
            """{"data":{"liquid_bond_size":"$bondSize","liquid_bond_shares":"$bondShares"}}"""
        )

    @Test
    fun `bRUNE tracks RUNE at parity and ybRUNE is NAV times RUNE`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        // RUNE price is served from the cache, keyed by Coin.id.
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"
        // ybRUNE NAV = 200 / 100 = 2.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "200", bondShares = "100")

        repository.refresh(listOf(bRune, ybRune))

        assertPriceEquals("5", repository.getPrice(bRune, AppCurrency.USD).first())
        assertPriceEquals("10", repository.getPrice(ybRune, AppCurrency.USD).first())
    }

    @Test
    fun `runePriceUsd reads the cache by coin id, not price provider id`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"

        repository.refresh(listOf(bRune))

        assertPriceEquals("5", repository.getPrice(bRune, AppCurrency.USD).first())
        // A correct cache hit means no live RUNE fetch was needed.
        coVerify(exactly = 0) {
            coinGeckoApi.getCryptoPrices(match { it.contains("thorchain") }, any())
        }
    }

    @Test
    fun `runePriceUsd live fallback fetches RUNE in USD and applies FX only once`() = runTest {
        // Non-USD app currency, no cached RUNE price: the live fallback must fetch RUNE in USD and
        // the caller applies the tether (currency-per-USD) rate exactly once.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns null
        coEvery {
            coinGeckoApi.getCryptoPrices(
                match { it.contains("thorchain") },
                match { it.contains("usd") },
            )
        } returns mapOf("thorchain" to mapOf("usd" to BigDecimal("5.0")))
        coEvery { coinGeckoApi.getCryptoPrices(match { it.contains("tether") }, any()) } returns
            mapOf("tether" to mapOf("eur" to BigDecimal("0.9")))

        repository.refresh(listOf(bRune))

        // 5 USD × 0.9 EUR/USD = 4.5 EUR. A double-applied FX would yield 4.05.
        assertPriceEquals("4.5", repository.getPrice(bRune, AppCurrency.EUR).first())
    }

    @Test
    fun `does not persist a zero ybRUNE price when the NAV field is malformed`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"
        // Unparseable bond size with positive shares → NAV 0 → price 0, which must not overwrite
        // the
        // last-known price.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "n/a", bondShares = "100")

        repository.refresh(listOf(ybRune))

        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(match { it.tokenId == ybRune.id }) }
    }

    @Test
    fun `VaultRedemption response maps the liquid bond JSON fields`() = runTest {
        // Pins the @SerialName mapping for the {"status":{}} contract query: a renamed field would
        // otherwise deserialize to the empty-string default and silently price ybRUNE at parity.
        val response = redemption(bondSize = "123", bondShares = "45")
        assertEquals("123", response.data.liquidBondSize)
        assertEquals("45", response.data.liquidBondShares)
    }
}
