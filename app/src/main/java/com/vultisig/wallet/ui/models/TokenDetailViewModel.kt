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
import com.vultisig.wallet.data.models.isRippleIssuedToken
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
    val canSend: Boolean = false,
    val canBuy: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val explorerUrl: String = "",
    // The vault's address on this chain, held so the Receive action can open its QR without
    // re-resolving the account.
    val chainAddress: String = "",
    // Where the token info section's "View on Explorer" row points: the contract page when the
    // chain's explorer has one, the holder's address page otherwise, and empty when the chain has
    // no explorer at all (the row is then dropped rather than offering a dead link).
    val tokenExplorerUrl: String = "",
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
    // Where the spot price sits between [low24h] and [high24h], 0 at the low and 1 at the high.
    // Null whenever the band can't be drawn honestly, which is also what hides it.
    val bandPosition: Float? = null,
    val athPrice: String? = null,
    val athDate: String? = null,
    val athChangePercent: String? = null,
    val atlPrice: String? = null,
    val atlDate: String? = null,
    val atlChangePercent: String? = null,
) {
    /** CoinGecko can return a markets entry with every field absent (a stale/inactive coin). */
    fun hasAnyValue(): Boolean =
        low24h != null ||
            high24h != null ||
            athPrice != null ||
            athDate != null ||
            atlPrice != null ||
            atlDate != null

    /** The 24h band needs both bounds and a placeable marker, or it says nothing. */
    fun hasBand(): Boolean = low24h != null && high24h != null && bandPosition != null
}

@Immutable
internal data class TokenInfoUiModel(
    val network: String = "",
    val contractAddress: String? = null,
    val decimals: String? = null,
    val hasExplorerLink: Boolean = false,
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

    fun receive() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.chainAddress.isEmpty()) return@launch
            val chain = Chain.fromRaw(chainRaw)
            navigator.route(
                Route.AddressQr(
                    vaultId = vaultId,
                    address = state.chainAddress,
                    name = chain.raw,
                    logo = chain.logo,
                )
            )
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
                                // Native coins have no contract to point at, and a chain whose
                                // explorer has no token page falls back to the holder's address
                                // page — the same rule iOS applies in getExplorerByCoinURL.
                                val tokenExplorerUrl =
                                    if (token.isNativeToken) {
                                        explorerUrl
                                    } else {
                                        explorerLinkRepository.getTokenLink(
                                            chain,
                                            token.contractAddress,
                                        ) ?: explorerUrl
                                    }

                                val isNewCoin = coin?.id != token.id
                                coin = token

                                uiState.update {
                                    it.copy(
                                        token = tokenUiModel,
                                        canDeposit = chain.isDepositSupported,
                                        // No provider routes an LP receipt or an XRPL issued
                                        // currency, whatever the chain supports.
                                        canSwap =
                                            chain.isSwapSupported &&
                                                !token.isLpToken &&
                                                !token.isRippleIssuedToken,
                                        canSend = true,
                                        canBuy = chain.isBuySupported,
                                        explorerUrl = explorerUrl,
                                        chainAddress = accountAddress,
                                        tokenExplorerUrl = tokenExplorerUrl,
                                        tokenInfo =
                                            TokenInfoUiModel(
                                                network = tokenUiModel.network,
                                                contractAddress =
                                                    token.contractAddress.takeIf {
                                                        !token.isNativeToken && it.isNotEmpty()
                                                    },
                                                decimals = token.decimal.toString(),
                                                hasExplorerLink = tokenExplorerUrl.isNotEmpty(),
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
                            stats?.toMarketStatsUiModel(currency.ticker, coin.ticker)
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
        currencyTicker: String,
        tokenTicker: String,
    ): MarketStatsUiModel =
        MarketStatsUiModel(
            marketCap = marketCap?.toStatFiatString(currencyTicker),
            marketCapRank = marketCapRank?.let { "#$it" },
            volume24h = volume24h?.toStatFiatString(currencyTicker),
            fullyDilutedValuation = fullyDilutedValuation?.toStatFiatString(currencyTicker),
            circulatingSupply = circulatingSupply?.let { formatSupply(it, tokenTicker) },
            maxSupply = maxSupply?.let { formatSupply(it, tokenTicker) },
        )

    private suspend fun CoinMarketStats.toPriceExtremesUiModel(
        currencyTicker: String
    ): PriceExtremesUiModel =
        PriceExtremesUiModel(
            low24h = low24h?.toFiatString(currencyTicker, asPrice = true),
            high24h = high24h?.toFiatString(currencyTicker, asPrice = true),
            bandPosition = positionIn24hRange()?.toFloat(),
            athPrice = athPrice?.toFiatString(currencyTicker, asPrice = true),
            athDate = athDate?.toDisplayDate(),
            athChangePercent = athChangePercent?.let(MarketStatFormatter::percent),
            atlPrice = atlPrice?.toFiatString(currencyTicker, asPrice = true),
            atlDate = atlDate?.toDisplayDate(),
            atlChangePercent = atlChangePercent?.let(MarketStatFormatter::percent),
        )

    private suspend fun BigDecimal.toFiatString(
        currencyTicker: String,
        asPrice: Boolean = false,
    ): String = fiatValueToStringMapper(FiatValue(this, currencyTicker), asPrice = asPrice)

    /**
     * A market-stats fiat figure. Caps, valuations and volumes run to twelve digits, which no
     * reader compares across a two-column row, so anything from a million up is abbreviated with
     * the currency's own symbol; smaller figures keep the currency's standard formatting.
     */
    private suspend fun BigDecimal.toStatFiatString(currencyTicker: String): String =
        if (MarketStatFormatter.isAbbreviated(this)) {
            MarketStatFormatter.currencySymbol(currencyTicker) +
                MarketStatFormatter.abbreviate(this)
        } else {
            toFiatString(currencyTicker)
        }

    private fun updateRefreshing(isRefreshing: Boolean) {
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }
}

private fun Double.toChangePercentText(): String = MarketStatFormatter.percent(this)

/** `120680000` -> `120.68M ETH`. Null for a supply CoinGecko doesn't actually track. */
private fun formatSupply(value: BigDecimal, ticker: String): String? =
    MarketStatFormatter.supply(value, ticker)

private fun Instant.toDisplayDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(this)
