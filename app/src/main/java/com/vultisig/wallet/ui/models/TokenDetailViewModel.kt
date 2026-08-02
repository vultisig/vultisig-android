package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.CoinMarketStats
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.MarketChart
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.hasMarketDataSource
import com.vultisig.wallet.data.models.isBuySupported
import com.vultisig.wallet.data.models.isDepositSupported
import com.vultisig.wallet.data.models.isLpToken
import com.vultisig.wallet.data.models.isReadOnlyAsset
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.TokenPriceChartRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
internal data class TokenDetailUiModel(
    val token: ChainTokenUiModel = ChainTokenUiModel(),
    val isRefreshing: Boolean = false,
    val canDeposit: Boolean = false,
    val canSwap: Boolean = false,
    // Closed until the token is loaded, like every other action flag here: the read-only check
    // runs only once a matching account resolves, and AssetActionButton has no disabled state, so
    // a default of true would leave SEND tappable for a read-only asset while that load is in
    // flight or after it finds nothing.
    val canSend: Boolean = false,
    val canBuy: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val explorerUrl: String = "",
    // Null until the first load attempt resolves for the current coin: stays null forever for a
    // pool-priced coin with no CoinGecko source, so the chart section never renders for it.
    val chart: ChartUiModel? = null,
    // True while the initial stats/extremes fetch for the current coin is in flight — the sections
    // render a placeholder skeleton in this state rather than staying absent and then popping in,
    // so the sheet's layout doesn't jump once the network call resolves.
    val statsLoading: Boolean = false,
    val marketStats: MarketStatsUiModel? = null,
    val priceExtremes: PriceExtremesUiModel? = null,
    val tokenInfo: TokenInfoUiModel? = null,
)

@Immutable
internal data class ChartUiModel(
    val selectedRange: ChartRange = ChartRange.ONE_DAY,
    val points: List<ChartPointUiModel> = emptyList(),
    val isPositive: Boolean = true,
    val changePercentText: String = "",
    val isLoading: Boolean = false,
    // True when [points]/[changePercentText] don't actually belong to [selectedRange] and the
    // current currency (a range/currency switch's fetch failed before any data for it ever loaded),
    // so they're being shown only as a placeholder rather than fresh data. Lets a failed range be
    // retapped to retry instead of being silently deduped as "already selected".
    val isStale: Boolean = false,
)

@Immutable
internal data class ChartPointUiModel(
    val timestampMillis: Long,
    val price: Double,
    val priceText: String,
)

@Immutable
internal data class MarketStatsUiModel(
    val marketCap: String? = null,
    val marketCapRank: String? = null,
    val fullyDilutedValuation: String? = null,
    val volume24h: String? = null,
    val circulatingSupply: String? = null,
    val maxSupply: String? = null,
) {
    /** CoinGecko can return a markets entry with every field absent (a stale/inactive coin). */
    fun hasAnyValue(): Boolean =
        marketCap != null ||
            marketCapRank != null ||
            fullyDilutedValuation != null ||
            volume24h != null ||
            circulatingSupply != null ||
            maxSupply != null
}

@Immutable
internal data class PriceExtremesUiModel(
    val low24h: String? = null,
    val high24h: String? = null,
    val athPrice: String? = null,
    val athDate: String? = null,
    val atlPrice: String? = null,
    val atlDate: String? = null,
) {
    /** CoinGecko can return a markets entry with every field absent (a stale/inactive coin). */
    fun hasAnyValue(): Boolean =
        low24h != null ||
            high24h != null ||
            athPrice != null ||
            athDate != null ||
            atlPrice != null ||
            atlDate != null
}

@Immutable
internal data class TokenInfoUiModel(
    val contractAddress: String? = null,
    val decimals: String? = null,
)

@HiltViewModel
internal class TokenDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val tokenPriceChartRepository: TokenPriceChartRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ViewModel() {

    private val tokenDetail = savedStateHandle.toRoute<Route.TokenDetail>()
    private val chainRaw: String = tokenDetail.chainId
    private val vaultId: String = tokenDetail.vaultId
    private val tokenId: String = tokenDetail.tokenId
    private val mergedBalance: String = tokenDetail.mergeId

    val uiState = MutableStateFlow(TokenDetailUiModel())

    private var loadDataJob: Job? = null
    private var chartJob: Job? = null
    private var statsJob: Job? = null

    // The resolved Coin for the currently displayed token, used to fetch its chart/stats. Null
    // until the first loadData() emission resolves an account for tokenId.
    private var coin: Coin? = null

    // (range, currencyTicker) of the chart data currently held in [uiState], updated only on a
    // successful fetch. Lets a failed fetch tell "this exact view just failed to refresh" (safe to
    // keep showing what's there) apart from "a different range/currency was requested and failed"
    // (must not relabel that stale data as if it belonged to the new selection).
    private var lastLoadedChartKey: Pair<ChartRange, String>? = null

    // Currency the market stats/price extremes currently held in [uiState] were formatted for,
    // updated only on a successful fetch — same purpose as [lastLoadedChartKey] for the chart.
    private var lastLoadedStatsCurrency: String? = null

    init {
        // Deliberately not deferred to the sheet's onExpand: waiting for the open animation to
        // settle left the first frame empty, and the content growing underneath it made the sheet
        // re-derive its anchors mid-animation.
        loadData()
        viewModelScope.safeLaunch {
            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
            uiState.update { it.copy(isBalanceVisible = isBalanceVisible) }
        }
        // Re-fetch chart/stats in the new currency. drop(1) skips the flow's replay of the
        // current currency on subscription — that initial load is already triggered by loadData()
        // resolving the coin, so re-running it here would fire a redundant fetch on every screen
        // open.
        viewModelScope.safeLaunch {
            appCurrencyRepository.currency.drop(1).collect {
                val currentCoin = coin ?: return@collect
                if (currentCoin.hasMarketDataSource) {
                    loadChart(uiState.value.chart?.selectedRange ?: ChartRange.ONE_DAY)
                }
                if (currentCoin.priceProviderID.isNotEmpty()) {
                    uiState.update { it.copy(statsLoading = true) }
                    loadStats()
                }
            }
        }
    }

    fun onChartRangeSelected(range: ChartRange) {
        val chart = uiState.value.chart
        // A stale chart's selectedRange was already set optimistically before its fetch failed, so
        // re-tapping that same range must still retry rather than being deduped as a no-op.
        if (chart?.selectedRange == range && !chart.isStale) return
        loadChart(range)
    }

    fun send() {
        viewModelScope.launch {
            navigator.route(Route.Send(vaultId = vaultId, chainId = chainRaw, tokenId = tokenId))
        }
    }

    fun swap() {
        viewModelScope.launch {
            navigator.route(Route.Swap(vaultId = vaultId, chainId = chainRaw, srcTokenId = tokenId))
        }
    }

    fun deposit() {
        viewModelScope.launch {
            navigator.route(Route.Deposit(vaultId = vaultId, chainId = chainRaw))
        }
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun buy() {
        viewModelScope.launch {
            navigator.route(Route.OnRamp(vaultId = vaultId, chainId = chainRaw))
        }
    }

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load token detail for %s", tokenId)
                    updateRefreshing(false)
                }
            ) {
                updateRefreshing(true)

                val chain = Chain.fromRaw(chainRaw)

                accountsRepository
                    .loadAddress(vaultId = vaultId, chain = chain)
                    .catch {
                        updateRefreshing(false)
                        Timber.e(it)
                    }
                    .onEach { address ->
                        address.accounts
                            .firstOrNull { it.token.id == tokenId }
                            ?.let { account ->
                                val token = account.token
                                val tokenUiModel =
                                    ChainTokenUiModel(
                                        id = token.id,
                                        name = token.ticker,
                                        balance =
                                            account.tokenValue?.let(
                                                mapTokenValueToStringWithUnitMapper
                                            ) ?: "",
                                        fiatBalance =
                                            account.fiatValue?.let { fiatValueToStringMapper(it) },
                                        tokenLogo = getCoinLogo(token.logo),
                                        chainLogo = chain.logo,
                                        mergeBalance = mergedBalance,
                                        price =
                                            account.price?.let {
                                                fiatValueToStringMapper(it, asPrice = true)
                                            },
                                        network = token.chain.raw,
                                    )

                                val accountAddress = address.address
                                val explorerUrl =
                                    explorerLinkRepository.getAddressLink(chain, accountAddress)

                                val isNewCoin = coin?.id != token.id
                                coin = token

                                uiState.update {
                                    it.copy(
                                        token = tokenUiModel,
                                        canDeposit = chain.isDepositSupported,
                                        // LP receipt tokens (e.g. bRUNE/ybRUNE) can't be a swap
                                        // source; gate the button here too, not only in the asset
                                        // pickers, so it can't be entered from this screen.
                                        canSwap =
                                            chain.isSwapSupported &&
                                                !token.isLpToken &&
                                                !token.isReadOnlyAsset,
                                        // Read-only assets (XRPL issued currencies) have no
                                        // signing path yet, so the send entry point is closed
                                        // here as well as in the asset pickers.
                                        canSend = !token.isReadOnlyAsset,
                                        canBuy = chain.isBuySupported,
                                        explorerUrl = explorerUrl,
                                        tokenInfo =
                                            TokenInfoUiModel(
                                                contractAddress =
                                                    token.contractAddress.takeIf {
                                                        !token.isNativeToken && it.isNotEmpty()
                                                    },
                                                decimals = token.decimal.toString(),
                                            ),
                                    )
                                }

                                if (isNewCoin) {
                                    if (token.hasMarketDataSource) {
                                        loadChart(ChartRange.ONE_DAY)
                                    } else {
                                        // Pool-priced token with no CoinGecko source at all: hide
                                        // the chart entirely rather than showing a spinner for a
                                        // fetch that will never resolve.
                                        uiState.update { it.copy(chart = null) }
                                    }
                                    // Stats/extremes need a real CoinGecko id (the markets
                                    // endpoint has no contract-address variant), a narrower gate
                                    // than the chart's.
                                    if (token.priceProviderID.isNotEmpty()) {
                                        uiState.update {
                                            it.copy(
                                                statsLoading = true,
                                                marketStats = null,
                                                priceExtremes = null,
                                            )
                                        }
                                        loadStats()
                                    } else {
                                        uiState.update {
                                            it.copy(
                                                statsLoading = false,
                                                marketStats = null,
                                                priceExtremes = null,
                                            )
                                        }
                                    }
                                }
                            } ?: run { updateRefreshing(false) }
                    }
                    .onCompletion { updateRefreshing(false) }
                    .collect()
            }
    }

    private fun loadChart(range: ChartRange) {
        val coin = this.coin ?: return
        chartJob?.cancel()
        // Captured before the optimistic update below overwrites `chart` with a loading
        // placeholder: true only when a fetch has ever actually succeeded for this coin, as
        // opposed to the fresh, still-empty ChartUiModel the optimistic update is about to write.
        val hadPriorPoints = uiState.value.chart?.points?.isNotEmpty() == true
        chartJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load chart for %s", coin.id)
                    uiState.update {
                        it.copy(
                            chart =
                                it.chart?.toFallback(range, hadPriorPoints, currencyTicker = null)
                        )
                    }
                }
            ) {
                uiState.update {
                    it.copy(
                        chart =
                            (it.chart ?: ChartUiModel()).copy(
                                selectedRange = range,
                                isLoading = true,
                            )
                    )
                }
                val currency = appCurrencyRepository.currency.first()
                val chart = tokenPriceChartRepository.getChart(coin, range, currency)
                if (chart != null) {
                    lastLoadedChartKey = range to currency.ticker
                }
                val chartUiModel =
                    chart?.let { c ->
                        c.toUiModel(range, appCurrencyRepository.getCurrencyFormat())
                    }
                uiState.update {
                    it.copy(
                        chart =
                            chartUiModel
                                ?: it.chart?.toFallback(range, hadPriorPoints, currency.ticker)
                    )
                }
            }
    }

    /**
     * Builds the chart shown after a failed/empty fetch for [range]. Returns null (hiding the
     * section) if this coin has never had a real series to fall back on; otherwise keeps the
     * currently-held points only if they were last successfully loaded for this exact (range,
     * currency) pair — a fetch for a different range/currency that just failed must not relabel
     * older, mismatched data as if it belonged to the new selection.
     */
    private fun ChartUiModel.toFallback(
        range: ChartRange,
        hadPriorPoints: Boolean,
        currencyTicker: String?,
    ): ChartUiModel? {
        if (!hadPriorPoints) return null
        val matchesHeldData =
            currencyTicker != null && lastLoadedChartKey == range to currencyTicker
        return if (matchesHeldData) {
            copy(isLoading = false, isStale = false)
        } else {
            copy(isLoading = false, isStale = true, points = emptyList(), changePercentText = "")
        }
    }

    private fun loadStats() {
        val coin = this.coin ?: return
        statsJob?.cancel()
        statsJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load market stats for %s", coin.id)
                    uiState.update { it.copy(statsLoading = false) }
                }
            ) {
                val currency = appCurrencyRepository.currency.first()
                val stats = tokenPriceChartRepository.getStats(coin, currency)
                // A failed fetch may only fall back to previously-held stats if those were
                // formatted for this exact currency — otherwise a currency switch would silently
                // keep displaying amounts in the old currency with statsLoading already false.
                val keepHeldOnFailure = lastLoadedStatsCurrency == currency.ticker
                uiState.update { state ->
                    state.copy(
                        statsLoading = false,
                        marketStats =
                            stats?.toMarketStatsUiModel(currency.ticker)
                                ?: state.marketStats.takeIf { keepHeldOnFailure },
                        priceExtremes =
                            stats?.toPriceExtremesUiModel(currency.ticker)
                                ?: state.priceExtremes.takeIf { keepHeldOnFailure },
                    )
                }
                if (stats != null) {
                    lastLoadedStatsCurrency = currency.ticker
                }
            }
    }

    private fun MarketChart.toUiModel(range: ChartRange, priceFormat: NumberFormat): ChartUiModel =
        ChartUiModel(
            selectedRange = range,
            points =
                points.map {
                    ChartPointUiModel(
                        timestampMillis = it.timestampMillis,
                        price = it.price.toDouble(),
                        priceText = priceFormat.format(it.price),
                    )
                },
            isPositive = isPositive,
            changePercentText = changePercent.toChangePercentText(),
            isLoading = false,
        )

    private suspend fun CoinMarketStats.toMarketStatsUiModel(
        currencyTicker: String
    ): MarketStatsUiModel =
        MarketStatsUiModel(
            marketCap = marketCap?.toFiatString(currencyTicker),
            marketCapRank = marketCapRank?.let { "#$it" },
            fullyDilutedValuation = fullyDilutedValuation?.toFiatString(currencyTicker),
            volume24h = volume24h?.toFiatString(currencyTicker),
            circulatingSupply = circulatingSupply?.let(::formatSupply),
            maxSupply = maxSupply?.let(::formatSupply),
        )

    private suspend fun CoinMarketStats.toPriceExtremesUiModel(
        currencyTicker: String
    ): PriceExtremesUiModel =
        PriceExtremesUiModel(
            low24h = low24h?.toFiatString(currencyTicker),
            high24h = high24h?.toFiatString(currencyTicker),
            athPrice = athPrice?.toFiatString(currencyTicker, asPrice = true),
            athDate = athDate?.toDisplayDate(),
            atlPrice = atlPrice?.toFiatString(currencyTicker, asPrice = true),
            atlDate = atlDate?.toDisplayDate(),
        )

    private suspend fun BigDecimal.toFiatString(
        currencyTicker: String,
        asPrice: Boolean = false,
    ): String = fiatValueToStringMapper(FiatValue(this, currencyTicker), asPrice = asPrice)

    private fun updateRefreshing(isRefreshing: Boolean) {
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }
}

private fun Double.toChangePercentText(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign${"%.2f".format(Locale.getDefault(), this)}%"
}

private fun formatSupply(value: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault())
    format.maximumFractionDigits = 0
    return format.format(value)
}

private fun Instant.toDisplayDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(this)
