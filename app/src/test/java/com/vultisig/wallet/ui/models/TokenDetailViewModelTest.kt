@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.MarketChart
import com.vultisig.wallet.data.models.MarketChartPoint
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.TokenPriceChartRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TokenDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var fiatValueToStringMapper: FiatValueToStringMapper
    private lateinit var mapTokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var explorerLinkRepository: ExplorerLinkRepository
    private lateinit var tokenPriceChartRepository: TokenPriceChartRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var currencyFlow: MutableStateFlow<AppCurrency>

    private val coin =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "bitcoin",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )

    private lateinit var originalLocale: Locale

    @BeforeEach
    fun setUp() {
        originalLocale = Locale.getDefault()
        // Pins decimal-separator formatting in changePercentText assertions to "." regardless of
        // the CI/dev machine's default locale.
        Locale.setDefault(Locale.US)

        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.TokenDetail>() } returns
            Route.TokenDetail(
                vaultId = "vault-1",
                chainId = coin.chain.raw,
                tokenId = coin.id,
                mergeId = "",
            )

        navigator = mockk(relaxed = true)
        fiatValueToStringMapper = mockk()
        coEvery { fiatValueToStringMapper(any(), any(), any()) } returns "$100.00"
        mapTokenValueToStringWithUnitMapper = mockk(relaxed = true)
        accountsRepository = mockk()
        balanceVisibilityRepository = mockk(relaxed = true)
        explorerLinkRepository = mockk(relaxed = true)
        tokenPriceChartRepository = mockk()
        appCurrencyRepository = mockk()
        currencyFlow = MutableStateFlow(AppCurrency.USD)
        coEvery { appCurrencyRepository.currency } returns currencyFlow
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance()

        coEvery { accountsRepository.loadAddress(any(), any()) } returns
            flowOf(
                Address(
                    chain = Chain.Bitcoin,
                    address = "bc1qxyz",
                    accounts =
                        listOf(
                            Account(token = coin, tokenValue = null, fiatValue = null, price = null)
                        ),
                )
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
        Locale.setDefault(originalLocale)
    }

    private fun createViewModel() =
        TokenDetailViewModel(
            savedStateHandle = mockk(relaxed = true),
            navigator = navigator,
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToStringWithUnitMapper = mapTokenValueToStringWithUnitMapper,
            accountsRepository = accountsRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            explorerLinkRepository = explorerLinkRepository,
            tokenPriceChartRepository = tokenPriceChartRepository,
            appCurrencyRepository = appCurrencyRepository,
        )

    private fun chart(sign: Int) =
        MarketChart(
            points =
                listOf(MarketChartPoint(1L, BigDecimal.TEN), MarketChartPoint(2L, BigDecimal.TEN)),
            changePercent = sign.toDouble(),
        )

    @Test
    fun `a coin with a CoinGecko source loads a chart defaulting to 1D`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.chart?.selectedRange shouldBe ChartRange.ONE_DAY
            vm.uiState.value.chart?.points?.size shouldBe 2
            vm.uiState.value.chart?.isPositive shouldBe true
        }

    @Test
    fun `a pool-priced coin with no CoinGecko source never shows a chart section`() =
        runTest(testDispatcher) {
            val poolCoin = coin.copy(priceProviderID = "", contractAddress = "")
            coEvery { accountsRepository.loadAddress(any(), any()) } returns
                flowOf(
                    Address(
                        chain = Chain.Bitcoin,
                        address = "bc1qxyz",
                        accounts =
                            listOf(
                                Account(
                                    token = poolCoin,
                                    tokenValue = null,
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.chart shouldBe null
            vm.uiState.value.marketStats shouldBe null
            vm.uiState.value.priceExtremes shouldBe null
        }

    @Test
    fun `selecting a new range cancels the in-flight fetch for the previous range`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null
            // ONE_WEEK never resolves within this test's virtual time budget — it must be cancelled
            // rather than eventually overwriting the ONE_MONTH result below.
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_WEEK, AppCurrency.USD)
            } coAnswers
                {
                    delay(10_000)
                    chart(sign = -1)
                }
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_MONTH, AppCurrency.USD)
            } returns chart(sign = 1).copy(changePercent = 42.0)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onChartRangeSelected(ChartRange.ONE_WEEK)
            vm.onChartRangeSelected(ChartRange.ONE_MONTH)
            advanceUntilIdle()

            vm.uiState.value.chart?.selectedRange shouldBe ChartRange.ONE_MONTH
            vm.uiState.value.chart?.changePercentText shouldBe "+42.00%"
        }

    @Test
    fun `range switch keeps the previous series visible while the new range loads`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_WEEK, AppCurrency.USD)
            } coAnswers
                {
                    delay(10_000)
                    chart(sign = -1)
                }

            val vm = createViewModel()
            advanceUntilIdle()
            val pointsBeforeSwitch = vm.uiState.value.chart?.points

            vm.onChartRangeSelected(ChartRange.ONE_WEEK)

            // While ONE_WEEK's fetch is in flight, the 1D series must still be on screen.
            vm.uiState.value.chart?.points shouldBe pointsBeforeSwitch
            vm.uiState.value.chart?.isLoading shouldBe true
        }

    @Test
    fun `switching the app currency re-fetches the chart and stats in the new currency`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.EUR)
            } returns chart(sign = 1).copy(changePercent = 7.0)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.EUR) } returns null

            val vm = createViewModel()
            advanceUntilIdle()

            currencyFlow.value = AppCurrency.EUR
            advanceUntilIdle()

            coVerify {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.EUR)
            }
            coVerify { tokenPriceChartRepository.getStats(coin, AppCurrency.EUR) }
            vm.uiState.value.chart?.changePercentText shouldBe "+7.00%"
        }

    @Test
    fun `a first-ever chart resolution with zero points hides the chart section entirely`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns null
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.chart shouldBe null
        }

    @Test
    fun `a failed re-fetch on currency switch clears the section instead of relabeling the old currency's data as fresh`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.EUR)
            } returns null
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.EUR) } returns null

            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.chart?.points?.size shouldBe 2

            currencyFlow.value = AppCurrency.EUR
            advanceUntilIdle()

            // The USD series must not be shown as if it were EUR data: the section stays visible
            // (the coin does have a data source) but with its points cleared and marked stale.
            vm.uiState.value.chart?.points shouldBe emptyList()
            vm.uiState.value.chart?.isStale shouldBe true
            vm.uiState.value.chart?.isLoading shouldBe false
        }

    @Test
    fun `a pool-priced coin chart stays null after a currency switch`() =
        runTest(testDispatcher) {
            val poolCoin = coin.copy(priceProviderID = "", contractAddress = "")
            coEvery { accountsRepository.loadAddress(any(), any()) } returns
                flowOf(
                    Address(
                        chain = Chain.Bitcoin,
                        address = "bc1qxyz",
                        accounts =
                            listOf(
                                Account(
                                    token = poolCoin,
                                    tokenValue = null,
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )

            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.chart shouldBe null

            currencyFlow.value = AppCurrency.EUR
            advanceUntilIdle()

            vm.uiState.value.chart shouldBe null
        }

    @Test
    fun `MarketStatsUiModel hasAnyValue is false when every field is null`() {
        MarketStatsUiModel().hasAnyValue() shouldBe false
    }

    @Test
    fun `MarketStatsUiModel hasAnyValue is true when any single field is set`() {
        MarketStatsUiModel(marketCap = "$1.2B").hasAnyValue() shouldBe true
    }

    @Test
    fun `PriceExtremesUiModel hasAnyValue is false when every field is null`() {
        PriceExtremesUiModel().hasAnyValue() shouldBe false
    }

    @Test
    fun `PriceExtremesUiModel hasAnyValue is true when any single field is set`() {
        PriceExtremesUiModel(low24h = "$0.98").hasAnyValue() shouldBe true
    }
}
