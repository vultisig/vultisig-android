@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.CoinMarketStats
import com.vultisig.wallet.data.models.MarketChart
import com.vultisig.wallet.data.models.MarketChartPoint
import com.vultisig.wallet.data.models.logo
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

    @Test
    fun `hasBand is false without a marker position, so a bandless section drops the block`() {
        PriceExtremesUiModel(low24h = "$0.98", high24h = "$1.02").hasBand() shouldBe false
        PriceExtremesUiModel(low24h = "$0.98", high24h = "$1.02", bandPosition = 0.5f)
            .hasBand() shouldBe true
    }

    @Test
    fun `receive opens the address QR for the account resolved on this chain`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null

            val vm = createViewModel()
            advanceUntilIdle()
            vm.receive()
            advanceUntilIdle()

            coVerify {
                navigator.route(
                    Route.AddressQr(
                        vaultId = "vault-1",
                        address = "bc1qxyz",
                        name = Chain.Bitcoin.raw,
                        logo = Chain.Bitcoin.logo,
                    )
                )
            }
        }

    @Test
    fun `a native coin's explorer row points at the holder's address page`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null
            every { explorerLinkRepository.getAddressLink(any(), any()) } returns
                "https://mempool.space/address/bc1qxyz"

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.tokenExplorerUrl shouldBe "https://mempool.space/address/bc1qxyz"
            vm.uiState.value.tokenInfo?.hasExplorerLink shouldBe true
            // Native coins have no contract to show — their address is the chain's, not a token's.
            vm.uiState.value.tokenInfo?.contractAddress shouldBe null
        }

    @Test
    fun `a token's explorer row prefers its contract page over the holder's address page`() =
        runTest(testDispatcher) {
            val token =
                coin.copy(
                    chain = Chain.Ethereum,
                    ticker = "USDT",
                    isNativeToken = false,
                    contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                )
            coEvery { accountsRepository.loadAddress(any(), any()) } returns
                flowOf(
                    Address(
                        chain = Chain.Ethereum,
                        address = "0xholder",
                        accounts =
                            listOf(
                                Account(
                                    token = token,
                                    tokenValue = null,
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )
            every { any<SavedStateHandle>().toRoute<Route.TokenDetail>() } returns
                Route.TokenDetail(
                    vaultId = "vault-1",
                    chainId = Chain.Ethereum.raw,
                    tokenId = token.id,
                    mergeId = "",
                )
            coEvery {
                tokenPriceChartRepository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(token, AppCurrency.USD) } returns null
            every { explorerLinkRepository.getAddressLink(any(), any()) } returns
                "https://etherscan.io/address/0xholder"
            every {
                explorerLinkRepository.getTokenLink(Chain.Ethereum, token.contractAddress)
            } returns "https://etherscan.io/token/${'$'}{token.contractAddress}"

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.tokenExplorerUrl shouldBe
                "https://etherscan.io/token/${'$'}{token.contractAddress}"
            vm.uiState.value.tokenInfo?.contractAddress shouldBe token.contractAddress
            // The cube button in the header keeps pointing at the vault's address.
            vm.uiState.value.explorerUrl shouldBe "https://etherscan.io/address/0xholder"
        }

    @Test
    fun `a chain whose explorer has no token page falls back to the address page`() =
        runTest(testDispatcher) {
            val token = coin.copy(isNativeToken = false, contractAddress = "gaia-token")
            coEvery { accountsRepository.loadAddress(any(), any()) } returns
                flowOf(
                    Address(
                        chain = Chain.Bitcoin,
                        address = "bc1qxyz",
                        accounts =
                            listOf(
                                Account(
                                    token = token,
                                    tokenValue = null,
                                    fiatValue = null,
                                    price = null,
                                )
                            ),
                    )
                )
            coEvery {
                tokenPriceChartRepository.getChart(token, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(token, AppCurrency.USD) } returns null
            every { explorerLinkRepository.getAddressLink(any(), any()) } returns
                "https://mempool.space/address/bc1qxyz"
            every { explorerLinkRepository.getTokenLink(any(), any()) } returns null

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.tokenExplorerUrl shouldBe "https://mempool.space/address/bc1qxyz"
        }

    @Test
    fun `a chain with no explorer at all drops the row rather than linking nowhere`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns null
            every { explorerLinkRepository.getAddressLink(any(), any()) } returns ""

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.tokenInfo?.hasExplorerLink shouldBe false
        }

    @Test
    fun `price extremes carry the band position and the all-time change percentages`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns
                marketStats(
                    currentPrice = BigDecimal("110"),
                    low24h = BigDecimal("100"),
                    high24h = BigDecimal("140"),
                    athChangePercent = -62.1637,
                    atlChangePercent = 23.164,
                )

            val vm = createViewModel()
            advanceUntilIdle()

            val extremes = vm.uiState.value.priceExtremes
            // 110 sits a quarter of the way up a 100..140 band.
            extremes?.bandPosition shouldBe 0.25f
            extremes?.athChangePercent shouldBe "-62.16%"
            extremes?.atlChangePercent shouldBe "+23.16%"
            extremes?.hasBand() shouldBe true
        }

    @Test
    fun `a degenerate 24h band leaves no marker to place`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns
                marketStats(
                    currentPrice = BigDecimal("100"),
                    low24h = BigDecimal("100"),
                    high24h = BigDecimal("100"),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.priceExtremes?.bandPosition shouldBe null
            vm.uiState.value.priceExtremes?.hasBand() shouldBe false
        }

    @Test
    fun `market stats abbreviate large fiat figures and tag supply with the coin's ticker`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns
                marketStats(
                    marketCap = BigDecimal("2226290000000"),
                    volume24h = BigDecimal("6960000"),
                    circulatingSupply = BigDecimal("120680000"),
                    // Below the abbreviation threshold, so this one keeps standard formatting —
                    // which the mocked mapper stands in for.
                    fullyDilutedValuation = BigDecimal("999999"),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            val stats = vm.uiState.value.marketStats
            stats?.marketCap shouldBe "$2.22T"
            stats?.volume24h shouldBe "$6.96M"
            stats?.circulatingSupply shouldBe "120.68M BTC"
            stats?.fullyDilutedValuation shouldBe "$100.00"
        }

    @Test
    fun `a supply CoinGecko does not track is dropped rather than shown as zero`() =
        runTest(testDispatcher) {
            coEvery {
                tokenPriceChartRepository.getChart(coin, ChartRange.ONE_DAY, AppCurrency.USD)
            } returns chart(sign = 1)
            coEvery { tokenPriceChartRepository.getStats(coin, AppCurrency.USD) } returns
                marketStats(maxSupply = BigDecimal.ZERO)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.marketStats?.maxSupply shouldBe null
        }

    private fun marketStats(
        currentPrice: BigDecimal? = null,
        marketCap: BigDecimal? = null,
        fullyDilutedValuation: BigDecimal? = null,
        volume24h: BigDecimal? = null,
        circulatingSupply: BigDecimal? = null,
        maxSupply: BigDecimal? = null,
        low24h: BigDecimal? = null,
        high24h: BigDecimal? = null,
        athChangePercent: Double? = null,
        atlChangePercent: Double? = null,
    ) =
        CoinMarketStats(
            currentPrice = currentPrice,
            marketCap = marketCap,
            marketCapRank = null,
            fullyDilutedValuation = fullyDilutedValuation,
            volume24h = volume24h,
            circulatingSupply = circulatingSupply,
            maxSupply = maxSupply,
            low24h = low24h,
            high24h = high24h,
            athPrice = if (athChangePercent != null) BigDecimal("4956") else null,
            athDate = null,
            athChangePercent = athChangePercent,
            atlPrice = if (atlChangePercent != null) BigDecimal("0.43") else null,
            atlDate = null,
            atlChangePercent = atlChangePercent,
        )
}
