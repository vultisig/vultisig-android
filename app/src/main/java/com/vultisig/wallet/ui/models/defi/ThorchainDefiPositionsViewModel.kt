package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolStatsJson
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_POSITION_COIN_IDS
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.ThorChainLpPosition
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.lpAssetLogoRes
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionsUseCase
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.defaultPositionsBondDialog
import com.vultisig.wallet.ui.screens.v2.defi.defaultPositionsStakingDialog
import com.vultisig.wallet.ui.screens.v2.defi.defaultSelectedPositionsDialog
import com.vultisig.wallet.ui.screens.v2.defi.emptyBondedTabUiModel
import com.vultisig.wallet.ui.screens.v2.defi.emptyStakingTabUiModel
import com.vultisig.wallet.ui.screens.v2.defi.formatAmount
import com.vultisig.wallet.ui.screens.v2.defi.formatDate
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
import com.vultisig.wallet.ui.screens.v2.defi.formatToString
import com.vultisig.wallet.ui.screens.v2.defi.getContractByDeFiAction
import com.vultisig.wallet.ui.screens.v2.defi.hasBondPositions
import com.vultisig.wallet.ui.screens.v2.defi.hasStakingPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.screens.v2.defi.thorchainSupportStakingDeFi
import com.vultisig.wallet.ui.screens.v2.defi.toUiModel
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.formatTokenAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType

internal data class ThorchainDefiPositionsUiModel(
    // tabs info
    // Starts null/loading rather than at a hardcoded "$0.00": the currency isn't known until the
    // first read, and a USD literal was wrong for everyone else.
    val totalAmountPrice: String? = null,
    val selectedTab: Int = R.string.defi_tab_bonded,
    val bonded: BondedTabUiModel = BondedTabUiModel(),
    val staking: StakingTabUiModel = StakingTabUiModel(),
    val lp: LpTabUiModel = LpTabUiModel(),
    val isTotalAmountLoading: Boolean = true,
    val isBalanceVisible: Boolean = true,

    // position selection dialog
    val showPositionSelectionDialog: Boolean = false,
    val bondPositionsDialog: List<PositionUiModelDialog> = defaultPositionsBondDialog(),
    val stakingPositionsDialog: List<PositionUiModelDialog> = defaultPositionsStakingDialog(),
    val lpPositionsDialog: List<PositionUiModelDialog> = emptyList(),
    // Flips true once the available-pools fetch returns. Until then the LP tab should stay in
    // loading state instead of flashing the empty/no-positions UI when the user has selected
    // positions whose keys don't yet match the (empty) dialog list.
    val lpDialogLoaded: Boolean = false,
    val selectedPositions: List<String> = defaultSelectedPositionsDialog(),
    val tempSelectedPositions: List<String> = defaultSelectedPositionsDialog(),
)

/** A complete set of leg values: only built once every leg has reported. */
internal data class TotalDefiValue(
    val bondAmount: BigInteger = BigInteger.ZERO,
    val defaultStakeValues: StakeDefaultValues = StakeDefaultValues(),
    val rujiStakeAmount: BigInteger = BigInteger.ZERO,
    val tcyStakeAmount: BigInteger = BigInteger.ZERO,
    val lpFiatValue: LpLegTotal,
)

@HiltViewModel
internal class ThorchainDefiPositionsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val bondUseCase: ThorchainBondUseCase,
    private val tokenPriceRepository: TokenPriceRepository,
    private val fiatValueCalculator: DefiFiatValueCalculator,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val rujiStakingService: RujiStakingService,
    private val tcyStakingService: TCYStakingService,
    private val defiPositionsRepository: DefiPositionsRepository,
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val getThorChainLpPositionsUseCase: GetThorChainLpPositionsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private lateinit var vaultId: String

    val state = MutableStateFlow(ThorchainDefiPositionsUiModel())

    private val bondedNodesRefreshTrigger = MutableStateFlow(0)

    private val loadedTabs = mutableSetOf<Int>()

    // Null until the leg reports in — loaded, empty, or failed. The header total is the sum of all
    // five, so pricing it while a leg is still unreported would publish a partial figure that
    // silently jumps as the rest land. A single shared "is loading" flag could not express that:
    // whichever leg finished first cleared it for everyone. Every terminal path in a leg — the
    // success collect, each .catch, and every early bail-out — must therefore assign here.
    private val _totalValueBond = MutableStateFlow<BigInteger?>(null)
    private val _totalValueDefaultStake = MutableStateFlow<StakeDefaultValues?>(null)
    private val _totalValueRujiStake = MutableStateFlow<BigInteger?>(null)
    private val _totalValueTCYStake = MutableStateFlow<BigInteger?>(null)
    // LP is priced per pool from two different assets, so it joins the total already converted to
    // fiat rather than as a raw chain amount like the other legs — see [LpLegTotal] for why it
    // carries a currency and why a failed pool reports as unavailable rather than as zero.
    private val _totalValueLpFiat = MutableStateFlow<LpLegTotal?>(null)

    val totalValueBond: StateFlow<BigInteger?> = _totalValueBond
    val totalValueDefaultStake: StateFlow<StakeDefaultValues?> = _totalValueDefaultStake
    val totalValueRujiStake: StateFlow<BigInteger?> = _totalValueRujiStake
    val totalValueTCYStake: StateFlow<BigInteger?> = _totalValueTCYStake
    val totalValueLpFiat: StateFlow<LpLegTotal?> = _totalValueLpFiat

    // Cached "available" pool list shared by the Manage-Positions dialog and the LP tab loader so
    // cold start makes a single getPoolStats call instead of two. `null` means "not loaded yet"
    // (or "previous fetch failed and should be retried"); `emptyList()` would mean "loaded, none
    // available", but Midgard never returns that in practice.
    private val availablePools = MutableStateFlow<List<ThorChainPoolStatsJson>?>(null)

    private var currencyJob: Job? = null
    private var lpDialogJob: Job? = null
    private var loadLpJob: Job? = null
    private var loadBondedNodesJob: Job? = null
    private var loadStakingPositionsJob: Job? = null

    // Latest bonded nodes, kept so onClickUnBond can resolve the selected node's raw bonded amount
    // (the UI model only carries the formatted string). Read/written on the main dispatcher.
    private var activeBondedNodes: List<BondedNodePosition> = emptyList()

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        loadBalanceVisibility()
        lpDialogJob?.cancel()
        lpDialogJob = loadLpPositionsForDialog()
        loadSavedPositions()
        loadTotalValue()
        currencyJob?.cancel()
        currencyJob = observeCurrencyChanges()
    }

    private fun loadLpPositionsForDialog(): Job =
        viewModelScope.launch {
            try {
                val pools =
                    withContext(ioDispatcher) {
                        getThorChainLpPositionsUseCase.fetchAvailablePools()
                    }
                availablePools.value = pools
                val dialogPositions =
                    pools
                        .map { pool -> pool.asset.toLpPositionDialogModel() }
                        .sortedBy { it.ticker }
                state.update { it.copy(lpPositionsDialog = dialogPositions, lpDialogLoaded = true) }
                // The first LP load (kicked off by loadSavedPositions) may have run before the
                // dialog data arrived, leaving the LP tab empty. Trigger a fresh load now that we
                // know the available pool set so selected positions are re-evaluated.
                reloadLpTab()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to load THORChain LP pools for dialog")
                // Leave availablePools null so the next user interaction (e.g. opening Manage
                // Positions or saving a selection) retries instead of soft-locking.
                // The tab deliberately stays in its loading state (see above), but the header total
                // is a separate concern: reloadLpTab parks the LP leg unreported while it waits for
                // this dataset, so report it as zero here or the total would never see all five
                // legs and would spin forever.
                reportLpFiat(BigDecimal.ZERO)
            }
        }

    private fun ensureAvailablePoolsLoaded() {
        if (availablePools.value != null) return
        if (lpDialogJob?.isActive == true) return
        lpDialogJob = loadLpPositionsForDialog()
    }

    private fun loadBalanceVisibility() {
        viewModelScope.launch {
            val isVisible =
                withContext(ioDispatcher) { balanceVisibilityRepository.getVisibility(vaultId) }
            state.update { it.copy(isBalanceVisible = isVisible) }
        }
    }

    private fun loadTotalValue() {
        viewModelScope.launch {
            combine(
                    totalValueBond,
                    totalValueDefaultStake,
                    totalValueRujiStake,
                    totalValueTCYStake,
                    totalValueLpFiat,
                ) { bondValue, stakeValue, rujiStake, tcyStake, lpFiat ->
                    if (
                        bondValue == null ||
                            stakeValue == null ||
                            rujiStake == null ||
                            tcyStake == null ||
                            lpFiat == null
                    ) {
                        null
                    } else {
                        TotalDefiValue(
                            bondAmount = bondValue,
                            defaultStakeValues = stakeValue,
                            rujiStakeAmount = rujiStake,
                            tcyStakeAmount = tcyStake,
                            lpFiatValue = lpFiat,
                        )
                    }
                }
                .filterNotNull()
                // collectLatest, not collect: pricing suspends on the currency and price lookups,
                // and a run started for an older set of legs could otherwise land after a newer
                // one and overwrite the header with a stale total.
                .collectLatest { totalValue -> handleTotalValueUpdate(totalValue) }
        }
    }

    /**
     * Prices the header total once every leg has reported. This is the only place that clears
     * [ThorchainDefiPositionsUiModel.isTotalAmountLoading] — a leg settling its own card must not
     * stop the header spinner, because the other legs may still be in flight.
     */
    private suspend fun handleTotalValueUpdate(totalValue: TotalDefiValue) {
        val totalInRune = CoinType.THORCHAIN.toValue(totalValue.bondAmount)
        val totalInRuji = CoinType.THORCHAIN.toValue(totalValue.rujiStakeAmount)
        val totalInTCY = CoinType.THORCHAIN.toValue(totalValue.tcyStakeAmount)

        try {
            val currency = appCurrencyRepository.currency.first()

            val runeFiatValue =
                fiatValueCalculator.createFiatValue(totalInRune, Coins.ThorChain.RUNE, currency)
            val rujiFiatValue =
                fiatValueCalculator.createFiatValue(totalInRuji, Coins.ThorChain.RUJI, currency)
            val tcyFiatValue =
                fiatValueCalculator.createFiatValue(totalInTCY, Coins.ThorChain.TCY, currency)

            val defaultStakingFiatValues =
                totalValue.defaultStakeValues.stakeElements.map { position ->
                    val decimalAmount = CoinType.THORCHAIN.toValue(position.amount)
                    fiatValueCalculator.createFiatValue(decimalAmount, position.coin, currency)
                }

            val lpFiatValue =
                when (val lpLeg = totalValue.lpFiatValue) {
                    // A pool we were asked to price failed to load. Its positions read as zero, so
                    // a sum including it would understate the real total while looking just as
                    // settled as a correct one. Say the total is unavailable instead of quietly
                    // getting it wrong.
                    LpLegTotal.Unavailable -> {
                        state.update {
                            it.copy(totalAmountPrice = null, isTotalAmountLoading = false)
                        }
                        return
                    }

                    is LpLegTotal.Priced -> lpLeg.fiatValue
                }

            if (lpFiatValue.currency != currency.ticker) {
                // The currency changed after LP was priced. The raw legs re-convert on every run,
                // but LP is stored already converted, so adding it now would sum two currencies.
                // observeCurrencyChanges has already dropped the leg and asked for a re-price, so
                // park the header on its spinner: keeping the old figure on screen would show a
                // total in the currency the user just navigated away from.
                state.update { it.copy(totalAmountPrice = null, isTotalAmountLoading = true) }
                return
            }

            val totalFiatValue =
                listOf(runeFiatValue, rujiFiatValue, tcyFiatValue, lpFiatValue)
                    .plus(defaultStakingFiatValues)
                    .fold(FiatValue(BigDecimal.ZERO, currency.ticker)) { acc, fiatValue ->
                        acc + fiatValue
                    }

            val currencyFormat =
                withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }

            state.update {
                it.copy(
                    totalAmountPrice = currencyFormat.format(totalFiatValue.value),
                    isTotalAmountLoading = false,
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to calculate total fiat value")

            state.update { it.copy(isTotalAmountLoading = false) }
        }
    }

    private suspend fun calculateStakingFiatPrice(amount: BigDecimal, coin: Coin): String? {
        return try {
            val currency = appCurrencyRepository.currency.first()
            val currencyFormat =
                withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
            formatFiatString(amount, coin, currency, currencyFormat)
        } catch (e: java.io.IOException) {
            Timber.e(e, "Failed to calculate THORChain staking fiat price")
            null
        }
    }

    /**
     * Zero rendered in the user's currency. Positions that failed to load are worth showing as an
     * explicit zero rather than a blank, and the amount has to go through the same currency format
     * as a real balance so a non-USD user never sees a dollar sign. Returns `null` if the format
     * itself can't be resolved, which the UI renders as "price unavailable".
     */
    private suspend fun zeroFiat(): String? =
        try {
            withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
                .format(BigDecimal.ZERO)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to format zero balance")
            null
        }

    /**
     * Reports the LP leg together with the currency it was priced in, so a later total can tell a
     * fresh value from one left over from a previous currency.
     */
    private suspend fun reportLpFiat(value: BigDecimal) {
        val currency = appCurrencyRepository.currency.first()
        _totalValueLpFiat.value = LpLegTotal.Priced(FiatValue(value, currency.ticker))
    }

    /**
     * Re-prices every leg and every card when the user switches currency.
     *
     * Nothing re-converts on its own: the card fiat strings are formatted once at load time from
     * one-shot flows, and LP joins the total already converted, so a switch that only touched the
     * header would leave the Bonded and Staking cards reading in the currency the user just left.
     * Reloading is also what re-prices LP, which cannot be re-based from the stored magnitude.
     */
    private fun observeCurrencyChanges(): Job =
        viewModelScope.launch {
            appCurrencyRepository.currency
                .map { it.ticker }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    resetTotalsToPending()

                    bondedNodesRefreshTrigger.value++
                    loadBondedNodes()
                    loadStakingPositions()
                    reloadLpTab()
                }
        }

    /**
     * Drops every leg back to unreported and puts the header back on its spinner.
     *
     * Legs keep their last value across a reload, so without this the header would go on showing a
     * settled total — in the old currency — for as long as the refetch takes. Clearing the value
     * and the legs together is what makes the spinner honest.
     */
    private fun resetTotalsToPending() {
        _totalValueBond.value = null
        _totalValueDefaultStake.value = null
        _totalValueRujiStake.value = null
        _totalValueTCYStake.value = null
        _totalValueLpFiat.value = null
        state.update { it.copy(totalAmountPrice = null, isTotalAmountLoading = true) }
    }

    /**
     * Reports every staking leg as zero. Used where no staking source will run at all — nothing
     * selected, no RUNE coin, or the whole load threw — so the header total isn't left waiting on
     * legs that will never arrive.
     */
    private fun settleStakingTotals() {
        _totalValueDefaultStake.update { StakeDefaultValues() }
        _totalValueRujiStake.update { BigInteger.ZERO }
        _totalValueTCYStake.update { BigInteger.ZERO }
    }

    /**
     * Settles staking cards a failed load left mid-flight. Clearing the spinner alone used to leave
     * the card as a bare ticker with no amount and no fiat, which reads as a dropped value; filling
     * the price with a formatted zero keeps the card showing what the position is actually worth.
     */
    private suspend fun settleStakingPositions(matches: (StakePositionUiModel) -> Boolean) {
        val zero = zeroFiat()
        state.update { current ->
            current.copy(
                staking =
                    current.staking.copy(
                        positions =
                            current.staking.positions.map { position ->
                                if (matches(position)) {
                                    position.copy(
                                        isLoading = false,
                                        stakedFiatDisplay = position.stakedFiatDisplay ?: zero,
                                    )
                                } else {
                                    position
                                }
                            }
                    )
            )
        }
    }

    private suspend fun formatFiatString(
        amount: BigDecimal,
        coin: Coin,
        currency: AppCurrency,
        currencyFormat: java.text.NumberFormat,
    ): String {
        val fiatValue = fiatValueCalculator.createFiatValue(amount, coin, currency)
        return currencyFormat.format(fiatValue.value)
    }

    private fun loadSavedPositions() {
        viewModelScope.launch {
            // Null is a vault that has never chosen on this chain, which is the only case the
            // defaults belong to — an empty set is a selection the user cleared on purpose.
            val savedPositions =
                defiPositionsRepository
                    .getSelectedPositions(Chain.ThorChain, vaultId)
                    .first()
                    ?.toList() ?: defaultSelectedPositionsDialog()
            state.update {
                it.copy(selectedPositions = savedPositions, tempSelectedPositions = savedPositions)
            }

            loadBondedNodes()

            loadStakingPositions()

            reloadLpTab()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBondedNodes() {
        loadedTabs.add(DeFiTab.BONDED.displayNameRes)

        // Cancel any in-flight collector before starting a new one. getActiveNodes never
        // completes, so without this each refresh would stack another collector that writes
        // `activeBondedNodes`/state out of order — leaving onClickUnBond to resolve a stale bond.
        loadBondedNodesJob?.cancel()
        loadBondedNodesJob =
            viewModelScope.launch {
                if (!state.value.selectedPositions.hasBondPositions()) {
                    _totalValueBond.value = BigInteger.ZERO

                    val zero = zeroFiat()
                    state.update { it.copy(bonded = emptyBondedTabUiModel(zero)) }
                    return@launch
                }

                state.update { it.copy(bonded = it.bonded.copy(isLoading = true)) }

                // Load selected positions, if disabled then show nothing
                try {
                    val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                    // THORChain vaults hold several coins (RUNE, RUJI, TCY, …); match RUNE
                    // explicitly so we bond against the RUNE address, not the first THORChain coin.
                    val runeCoin =
                        vault?.coins?.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }

                    if (runeCoin == null) {
                        Timber.e("Vault does not have RUNE coin")
                        val zero = zeroFiat()
                        state.update {
                            it.copy(
                                bonded = it.bonded.copy(isLoading = false, totalBondedPrice = zero)
                            )
                        }
                        _totalValueBond.update { BigInteger.ZERO }
                        return@launch
                    }

                    val address = runeCoin.address

                    bondedNodesRefreshTrigger
                        .flatMapLatest {
                            bondUseCase.getActiveNodes(vaultId, address).onCompletion { cause ->
                                // A source that finishes without ever emitting is done, not
                                // pending. Report it, or the header total waits on a leg that is
                                // never going to arrive. Cancellation means flatMapLatest dropped
                                // this collector for a newer trigger, and settling the leg for a
                                // run that has been replaced understates the total until the
                                // replacement lands.
                                if (cause !is CancellationException) {
                                    _totalValueBond.compareAndSet(null, BigInteger.ZERO)
                                }
                            }
                        }
                        .catch { t ->
                            Timber.e(t)
                            // The bond leg is one input to the header total. Leaving it unreported
                            // would strand the header on its spinner, so settle it at zero instead
                            // of letting a failed leg look like a total that never arrived.
                            val zero = zeroFiat()
                            state.update {
                                it.copy(
                                    bonded =
                                        it.bonded.copy(isLoading = false, totalBondedPrice = zero)
                                )
                            }
                            _totalValueBond.update { BigInteger.ZERO }
                        }
                        .collect { activeNodes ->
                            activeBondedNodes = activeNodes
                            // Format UI data and show
                            val nodeUiModels = activeNodes.map { it.toUiModel() }
                            val totalBonded = calculateTotalBonded(activeNodes)

                            val totalBondedRaw =
                                activeNodes.fold(BigInteger.ZERO) { acc, node -> acc + node.amount }

                            val bondedPrice = calculateBondedFiatPrice(totalBondedRaw)

                            state.update {
                                it.copy(
                                    bonded =
                                        BondedTabUiModel(
                                            isLoading = false,
                                            totalBondedAmount = totalBonded,
                                            totalBondedPrice = bondedPrice,
                                            nodes = nodeUiModels,
                                        )
                                )
                            }

                            _totalValueBond.update { totalBondedRaw }
                        }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.e(t)
                    // Same contract as the .catch above: this wraps the vault lookup, and leaving
                    // the leg unreported here would hang the header total forever.
                    val zero = zeroFiat()
                    state.update {
                        it.copy(bonded = it.bonded.copy(isLoading = false, totalBondedPrice = zero))
                    }
                    _totalValueBond.update { BigInteger.ZERO }
                }
            }
    }

    private fun calculateTotalBonded(nodes: List<BondedNodePosition>): String {
        val total = nodes.fold(BigInteger.ZERO) { acc, node -> acc + node.amount }
        return total.formatAmount(CoinType.THORCHAIN)
    }

    private suspend fun calculateBondedFiatPrice(totalBondedRaw: BigInteger): String? {
        return try {
            val totalInRune = CoinType.THORCHAIN.toValue(totalBondedRaw)
            val currency = appCurrencyRepository.currency.first()
            val fiatValue =
                fiatValueCalculator.createFiatValue(totalInRune, Coins.ThorChain.RUNE, currency)
            val currencyFormat =
                withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
            currencyFormat.format(fiatValue.value)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to calculate bonded fiat price")
            null
        }
    }

    private fun loadStakingPositions() {
        loadedTabs.add(DeFiTab.STAKED.displayNameRes)

        // Cancel the in-flight load before starting another, the way loadBondedNodes does. A
        // currency switch relaunches both; without this the two staking loads run side by side and
        // the slower older one lands last, leaving the cards priced in the currency just left.
        loadStakingPositionsJob?.cancel()
        loadStakingPositionsJob =
            viewModelScope.launch {
                val selectedPositions = state.value.selectedPositions

                // Initial Loading Status
                if (!selectedPositions.hasStakingPositions()) {
                    settleStakingTotals()

                    state.update { it.copy(staking = emptyStakingTabUiModel()) }
                    return@launch
                }
                val zero = zeroFiat()
                val defaultLoadingPositions =
                    loadDefaultStakingPositions()
                        .filter { position -> selectedPositions.contains(position.selectionKey()) }
                        .map { positionUiModel ->
                            positionUiModel.copy(isLoading = true, stakedFiatDisplay = zero)
                        }
                state.update {
                    it.copy(staking = StakingTabUiModel(positions = defaultLoadingPositions))
                }

                try {
                    val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }

                    // THORChain hosts several coins (RUNE, RUJI, TCY…); staking is held against the
                    // RUNE account, so match the ticker explicitly rather than the first chain
                    // coin.
                    val runeCoin =
                        vault?.coins?.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }

                    if (runeCoin == null) {
                        Timber.e("Vault does not have RUNE coin")

                        settleStakingTotals()
                        settleStakingPositions { true }
                        return@launch
                    }

                    val address = runeCoin.address
                    val coinsToLoad =
                        thorchainSupportStakingDeFi
                            .filter { coin -> selectedPositions.contains(coin.ticker) }
                            .map { coin -> coin.id }

                    // A staking source that isn't selected still has to report, or the header total
                    // would wait on a leg that is never going to load.
                    if (coinsToLoad.contains(Coins.ThorChain.RUJI.id)) {
                        createRujiStakePosition(address, vaultId)
                    } else {
                        _totalValueRujiStake.update { BigInteger.ZERO }
                    }
                    if (coinsToLoad.contains(Coins.ThorChain.TCY.id)) {
                        createTCYStakePosition(address, vaultId)
                    } else {
                        _totalValueTCYStake.update { BigInteger.ZERO }
                    }

                    createGenericStakePosition(address, vaultId, coinsToLoad)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Timber.e(t, "Failed to load staking positions")
                    settleStakingTotals()
                    settleStakingPositions { true }
                }
            }
    }

    // The three sources launch into the caller's scope rather than viewModelScope so they are
    // children of the load that started them: cancelling that load has to reach the collectors it
    // spawned, or a superseded run keeps writing cards and legs after its parent is gone.
    private fun CoroutineScope.createRujiStakePosition(address: String, vaultId: String) {
        launch {
            rujiStakingService
                .getStakingDetails(address, vaultId)
                .catch { t ->
                    Timber.e(t, "Failed to load staking positions RUJI")
                    // Report the leg as zero rather than leaving it unset: the header sums it, so
                    // an unreported failure would keep the total spinning, and a stale prior value
                    // would survive a refresh whose cards have already fallen back to zero.
                    _totalValueRujiStake.update { BigInteger.ZERO }
                    settleStakingPositions { it.coin.id in RUJI_POSITION_COIN_IDS }
                }
                // A source that finishes without ever emitting is done, not pending, and has to
                // report or the header waits forever. Cancellation is the exception: this load has
                // been superseded, and settling the leg on its way out would hand the replacement's
                // still-pending leg a zero it never reported.
                .onCompletion { cause ->
                    if (cause !is CancellationException) {
                        _totalValueRujiStake.compareAndSet(null, BigInteger.ZERO)
                    }
                }
                .collect { detailsList ->
                    for (details in detailsList) {
                        updateExistingPosition(rujiPositionUiModel(details))
                    }

                    // Both positions are denominated in RUJI, so the tab's RUJI total is their sum.
                    _totalValueRujiStake.update {
                        detailsList.fold(BigInteger.ZERO) { acc, details ->
                            acc + details.stakeAmount
                        }
                    }
                }
        }
    }

    /**
     * Builds the card for one of the two RUJI staking positions.
     *
     * Both are denominated in RUJI — the auto-compounding one is valued at the pool's share price
     * rather than shown as a raw sRUJI share count — and are priced off RUJI. The APR and the
     * claimable USDC belong to the bonded position; the auto-compounding one reinvests its revenue
     * into its own amount and so stays stat-free.
     */
    private suspend fun rujiPositionUiModel(details: StakingDetails): StakePositionUiModel {
        val isAutoCompound = details.coin.id == Coins.ThorChain.sRUJI.id
        val stakedAmount = Chain.ThorChain.coinType.toValue(details.stakeAmount)
        val formattedAmount = stakedAmount.formatTokenAmount(RUJI_SYMBOL)
        val stakedFiat = calculateStakingFiatPrice(stakedAmount, Coins.ThorChain.RUJI)

        val rewards =
            details.rewards?.let { rewardAmount ->
                val rewardAmountFormatted = Chain.ThorChain.coinType.toValue(rewardAmount)
                val rewardValue = rewardAmountFormatted.setScale(6, RoundingMode.DOWN)
                rewardValue.formatTokenAmount(details.rewardsCoin?.ticker ?: RUJI_REWARDS_SYMBOL)
            }

        return StakePositionUiModel(
            coin = details.coin,
            stakeAssetHeader =
                if (isAutoCompound) {
                    UiText.FormattedText(R.string.defi_header_compounded, listOf(RUJI_SYMBOL))
                } else {
                    UiText.StringResource(R.string.staked_ruji_header)
                },
            stakeAmount = stakedAmount,
            stakedAmountDisplay = formattedAmount,
            stakedFiatDisplay = stakedFiat,
            apy = details.apr?.formatPercentage(),
            canWithdraw = details.rewards?.let { it > BigDecimal.ZERO } == true,
            canStake = true,
            canUnstake = details.stakeAmount > BigInteger.ZERO,
            rewards = rewards,
            nextReward = null,
            nextPayout = null,
        )
    }

    private fun CoroutineScope.createTCYStakePosition(address: String, vaultId: String) {
        launch {
            tcyStakingService
                .getStakingDetails(address = address, vaultId = vaultId)
                .catch { t ->
                    Timber.e(t, "Failed to load staking positions TCY Stake")
                    _totalValueTCYStake.update { BigInteger.ZERO }
                    settleStakingPositions { it.coin.id == Coins.ThorChain.TCY.id }
                }
                .onCompletion { cause ->
                    if (cause !is CancellationException) {
                        _totalValueTCYStake.compareAndSet(null, BigInteger.ZERO)
                    }
                }
                .collect { position ->
                    val stakedAmount = Chain.ThorChain.coinType.toValue(position.stakeAmount)
                    val formattedAmount = stakedAmount.formatTokenAmount("TCY")
                    val stakedFiat = calculateStakingFiatPrice(stakedAmount, position.coin)

                    // Create and return the UI model
                    val stakePosition =
                        StakePositionUiModel(
                            coin = position.coin,
                            stakeAssetHeader = UiText.StringResource(R.string.staked_tcy_header),
                            stakedAmountDisplay = formattedAmount,
                            stakedFiatDisplay = stakedFiat,
                            stakeAmount = stakedAmount,
                            apy = position.apr?.formatPercentage(),
                            canWithdraw = false, // TCY auto-distributes rewards
                            canStake = true,
                            canUnstake = true,
                            rewards = null,
                            nextReward = position.estimatedRewards?.toDouble()?.formatToString(),
                            nextPayout = position.nextPayoutDate?.formatDate(),
                        )

                    updateExistingPosition(stakePosition)

                    _totalValueTCYStake.update { position.stakeAmount }
                }
        }
    }

    private fun CoroutineScope.createGenericStakePosition(
        address: String,
        vaultId: String,
        coinsToLoad: List<String>,
    ) {
        launch {
            defaultStakingPositionService
                .getStakingDetails(address, vaultId)
                .catch { t ->
                    Timber.e(t, "Failed to load staking positions")
                    _totalValueDefaultStake.update { StakeDefaultValues() }
                    settleStakingPositions {
                        it.coin.id == Coins.ThorChain.yRUNE.id ||
                            it.coin.id == Coins.ThorChain.yTCY.id ||
                            it.coin.id == Coins.ThorChain.sTCY.id
                    }
                }
                .onCompletion { cause ->
                    if (cause !is CancellationException) {
                        _totalValueDefaultStake.compareAndSet(null, StakeDefaultValues())
                    }
                }
                .collect { defaultPositions ->
                    val loadedPositions = defaultPositions.filter { it.coin.id in coinsToLoad }

                    // sTCY, yTCY and yRUNE are position tokens, not wallet balances: unless the
                    // user separately enabled them the vault does not hold them, and the periodic
                    // price refresh only ever covers vault coins. Refresh them here — nothing else
                    // will — or their cards fall through to a contract lookup with no cache row
                    // behind it and render $0.00. Skipped when nothing loaded: refresh has no
                    // empty-list guard of its own and would spend a live request on no ids.
                    if (loadedPositions.isNotEmpty()) {
                        withContext(ioDispatcher) {
                            try {
                                tokenPriceRepository.refresh(loadedPositions.map { it.coin })
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                // Pricing is cosmetic here: a failed refresh leaves the previous
                                // cached price in place rather than blocking the cards.
                                Timber.e(t, "Failed to refresh staking position prices")
                            }
                        }
                    }

                    // Resolve currency and format once; the non-suspend .map below can't call
                    // suspend functions, so fiat strings are pre-computed here.
                    val currency = appCurrencyRepository.currency.first()
                    val currencyFormat =
                        withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
                    val stakedFiatByCoinId = mutableMapOf<String, String>()
                    for (defaultPosition in loadedPositions) {
                        val amount = Chain.ThorChain.coinType.toValue(defaultPosition.stakeAmount)
                        stakedFiatByCoinId[defaultPosition.coin.id] =
                            formatFiatString(amount, defaultPosition.coin, currency, currencyFormat)
                    }

                    val positions =
                        loadedPositions.map { defaultPosition ->
                            val stakeAmount =
                                Chain.ThorChain.coinType.toValue(defaultPosition.stakeAmount)
                            val coin = defaultPosition.coin
                            val supportsMint =
                                coin.ticker.contains("yrune", ignoreCase = true) ||
                                    coin.ticker.contains("ytcy", ignoreCase = true)

                            val canTransfer = coin.ticker.contains("stcy", ignoreCase = true)

                            val headerResId =
                                if (supportsMint) {
                                    R.string.defi_header_minted
                                } else if (
                                    defaultPosition.coin.id.equals(Coins.ThorChain.sTCY.id, true)
                                ) {
                                    R.string.defi_header_compounded
                                } else {
                                    R.string.defi_header_staked
                                }
                            val position =
                                StakePositionUiModel(
                                    coin = defaultPosition.coin,
                                    stakeAssetHeader =
                                        UiText.FormattedText(headerResId, listOf(coin.ticker)),
                                    stakedAmountDisplay =
                                        stakeAmount.formatTokenAmount(coin.ticker),
                                    // No .orEmpty(): a missed lookup means "we have no price",
                                    // which the card states as unavailable. Coercing it to "" would
                                    // drop the fiat line instead.
                                    stakedFiatDisplay = stakedFiatByCoinId[coin.id],
                                    stakeAmount = stakeAmount,
                                    apy = null,
                                    supportsMint = supportsMint,
                                    canWithdraw = false, // TCY auto-distributes rewards
                                    canStake = true,
                                    canTransfer = canTransfer,
                                    canUnstake = stakeAmount > BigDecimal.ZERO,
                                    rewards = null,
                                    nextReward = null,
                                    nextPayout = null,
                                )

                            updateExistingPosition(position)

                            position to defaultPosition.stakeAmount
                        }

                    _totalValueDefaultStake.update {
                        StakeDefaultValues(
                            stakeElements =
                                positions.map { position ->
                                    StakeDefaultValues.StakingElement(
                                        coin = position.first.coin,
                                        amount = position.second,
                                    )
                                }
                        )
                    }
                }
        }
    }

    fun updateExistingPosition(stakePosition: StakePositionUiModel) {
        // Ensure only RUJI can have withdraw enabled
        val correctedPosition =
            if (stakePosition.coin.id != Coins.ThorChain.RUJI.id) {
                stakePosition.copy(canWithdraw = false)
            } else {
                stakePosition
            }

        state.update { currentState ->
            val existingPositions = currentState.staking.positions
            val positionExists = existingPositions.any { it.coin.id == correctedPosition.coin.id }

            if (positionExists) {
                currentState.copy(
                    staking =
                        currentState.staking.copy(
                            positions =
                                existingPositions.map {
                                    if (it.coin.id == correctedPosition.coin.id) correctedPosition
                                    else it
                                }
                        )
                )
            } else {
                currentState.copy(
                    staking =
                        currentState.staking.copy(positions = existingPositions + correctedPosition)
                )
            }
        }
    }

    fun onTabSelected(tab: DeFiTab) {
        state.update { currentState -> currentState.copy(selectedTab = tab.displayNameRes) }
    }

    private fun reloadLpTab() {
        val selectedKeys = state.value.selectedPositions.toSet()
        val pools = availablePools.value
        // Dialog dataset hasn't loaded yet (or last fetch failed). Don't run with stale state —
        // loadLpPositionsForDialog calls reloadLpTab again once it succeeds.
        if (pools == null) {
            state.update { it.copy(lp = it.lp.copy(isLoading = true)) }
            ensureAvailablePoolsLoaded()
            return
        }

        val selectedPools = state.value.lpPositionsDialog.filter { it.positionKey in selectedKeys }

        if (selectedPools.isEmpty()) {
            loadLpJob?.cancel()
            state.update { it.copy(lp = LpTabUiModel(isLoading = false, positions = emptyList())) }
            loadLpJob = viewModelScope.launch { reportLpFiat(BigDecimal.ZERO) }
            return
        }

        state.update { it.copy(lp = it.lp.copy(isLoading = true)) }

        loadLpJob?.cancel()
        loadLpJob =
            viewModelScope.launch {
                // The zero has to be resolved before the placeholders are built: a failed load
                // freezes these exact objects into the terminal state, so a placeholder that
                // snapshotted an unresolved zero would strand the card on the dash for good.
                val zero = zeroFiat()
                // Show placeholder cards for each selected pool first — even pools where the user
                // has no liquidity yet should be visible so the Add button is reachable.
                val placeholders = selectedPools.map { it.toPlaceholderUiModel(zero) }
                state.update {
                    it.copy(lp = LpTabUiModel(isLoading = true, positions = placeholders))
                }

                try {
                    val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                    // THORChain hosts several coins (RUNE, RUJI, TCY…); LP positions are held
                    // against the RUNE account, so match the ticker explicitly rather than the
                    // first chain coin.
                    val runeCoin =
                        vault?.coins?.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }

                    if (runeCoin == null) {
                        Timber.e("Vault does not have RUNE coin for LP positions")
                        reportLpFiat(BigDecimal.ZERO)
                        state.update {
                            it.copy(lp = LpTabUiModel(isLoading = false, positions = placeholders))
                        }
                        return@launch
                    }

                    val assetAddressesByPool =
                        selectedPools
                            .mapNotNull { dialogPool ->
                                val parsed = parseThorChainPool(dialogPool.positionKey)
                                val assetChain = parsed.chain ?: return@mapNotNull null
                                if (assetChain == Chain.ThorChain) return@mapNotNull null
                                val assetCoin =
                                    vault.coins.firstOrNull {
                                        it.chain == assetChain && it.isNativeToken
                                    } ?: vault.coins.firstOrNull { it.chain == assetChain }
                                assetCoin?.address?.let { dialogPool.positionKey to it }
                            }
                            .toMap()

                    val lpPositions =
                        withContext(ioDispatcher) {
                            getThorChainLpPositionsUseCase(
                                runeAddress = runeCoin.address,
                                assetAddressesByPool = assetAddressesByPool,
                                availablePools = pools,
                            )
                        }
                    val positionsByPool = lpPositions.positions.associateBy { it.pool }
                    // The use case queries every available pool, so only failures among the pools
                    // the user actually selected can affect what this screen shows.
                    val failedSelectedPools =
                        selectedPools.filter { it.positionKey in lpPositions.failedPools }

                    val currency = appCurrencyRepository.currency.first()
                    val currencyFormat =
                        withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
                    val runePrice = priceFor(Coins.ThorChain.RUNE, currency)

                    val merged =
                        selectedPools.map { dialogPool ->
                            val realPosition = positionsByPool[dialogPool.positionKey]
                            when {
                                realPosition != null ->
                                    realPosition.toUiModel(
                                        vault.coins,
                                        runePrice,
                                        currency,
                                        currencyFormat,
                                    )
                                // This pool's lookup errored, so we don't know what it holds. A
                                // zero here would be a claim we can't make.
                                dialogPool in failedSelectedPools ->
                                    dialogPool.toPlaceholderUiModel(null)
                                else -> dialogPool.toPlaceholderUiModel(zero)
                            }
                        }

                    _totalValueLpFiat.value =
                        if (failedSelectedPools.isEmpty()) {
                            LpLegTotal.Priced(
                                FiatValue(
                                    merged.fold(BigDecimal.ZERO) { acc, position ->
                                        acc + position.totalFiatValue
                                    },
                                    currency.ticker,
                                )
                            )
                        } else {
                            // Those pools contribute zero to the fold above, which would quietly
                            // understate the header total rather than admit a value is missing.
                            LpLegTotal.Unavailable
                        }
                    state.update {
                        it.copy(lp = LpTabUiModel(isLoading = false, positions = merged))
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to load THORChain LP positions")
                    reportLpFiat(BigDecimal.ZERO)
                    state.update {
                        it.copy(lp = LpTabUiModel(isLoading = false, positions = placeholders))
                    }
                }
            }
    }

    private fun PositionUiModelDialog.toPlaceholderUiModel(zero: String?): LpPositionUiModel {
        val parsed = parseThorChainPool(positionKey)
        val assetTicker = parsed.ticker
        val resolvedAssetLogo: ImageModel? =
            lpAssetLogoRes(parsed.chain, parsed.ticker, parsed.contractAddress) ?: (logo as? Int)
        return LpPositionUiModel(
            titleLp = "$ticker Pool",
            totalPriceLp = zero,
            icon = resolvedAssetLogo ?: getCoinLogo(assetTicker.lowercase()),
            assetTicker = assetTicker,
            apr = null,
            position = "0 ${Coins.ThorChain.RUNE.ticker} + 0 $assetTicker",
            positionKey = positionKey,
            canRemove = false,
            chainLogo = parsed.chain?.monoToneLogo,
        )
    }

    private suspend fun ThorChainLpPosition.toUiModel(
        vaultCoins: List<Coin>,
        runePrice: BigDecimal,
        currency: AppCurrency,
        currencyFormat: java.text.NumberFormat,
    ): LpPositionUiModel {
        val parsed = parseThorChainPool(pool)
        val assetTicker = parsed.ticker
        val assetContractAddress = parsed.contractAddress
        val assetChain = parsed.chain

        val runeAmount =
            CoinType.THORCHAIN.toValue(runeRedeemValue)
                .setScale(POSITION_DISPLAY_SCALE, RoundingMode.DOWN)
        val assetAmount =
            CoinType.THORCHAIN.toValue(assetRedeemValue)
                .setScale(POSITION_DISPLAY_SCALE, RoundingMode.DOWN)

        val assetCoin =
            vaultCoins.find { coin ->
                coin.chain == assetChain &&
                    coin.ticker.equals(assetTicker, ignoreCase = true) &&
                    (assetContractAddress.isEmpty() ||
                        coin.contractAddress.equals(assetContractAddress, ignoreCase = true))
            }

        val assetPrice =
            assetCoin?.let { priceFor(it, currency) }
                ?: assetChain?.let {
                    priceForPoolAsset(it, assetTicker, assetContractAddress, currency)
                }
                ?: BigDecimal.ZERO

        val totalFiat =
            runeAmount
                .multiply(runePrice)
                .add(assetAmount.multiply(assetPrice))
                .setScale(2, RoundingMode.DOWN)

        val resolvedAssetLogo = lpAssetLogoRes(assetChain, assetTicker, assetContractAddress)

        return LpPositionUiModel(
            titleLp = "RUNE/$assetTicker Pool",
            totalPriceLp = currencyFormat.format(totalFiat),
            totalFiatValue = totalFiat,
            icon = resolvedAssetLogo ?: getCoinLogo(assetTicker.lowercase()),
            assetTicker = assetTicker,
            apr = annualPercentageRate?.formatPercentage(),
            position =
                runeAmount.stripTrailingZeros().formatTokenAmount(Coins.ThorChain.RUNE.ticker) +
                    " + " +
                    assetAmount.stripTrailingZeros().formatTokenAmount(assetTicker),
            positionKey = pool,
            chainLogo = assetChain?.monoToneLogo,
        )
    }

    /**
     * The price of one [coin], for the LP legs this screen totals itself rather than through
     * [DefiFiatValueCalculator.createFiatValue]. The lookup order lives on the calculator: this
     * screen used to carry its own copy, and the two drifted on which route a NAV-priced receipt
     * may take. A failed lookup leaves the leg at zero rather than collapsing the whole card.
     */
    private suspend fun priceFor(coin: Coin, currency: AppCurrency): BigDecimal =
        try {
            fiatValueCalculator.priceOf(coin, currency)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to fetch price for %s", coin.id)
            BigDecimal.ZERO
        }

    /**
     * Prices an LP leg whose asset the vault doesn't hold. The pool names the asset by chain and
     * ticker, which is enough to find its curated [Coin] and so its CoinGecko id — going straight
     * to the contract route instead left every native pool asset (BTC, ETH, …) at $0.00, because a
     * native asset has no contract address to look up.
     */
    private suspend fun priceForPoolAsset(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        currency: AppCurrency,
    ): BigDecimal =
        try {
            fiatValueCalculator.priceOfPoolAsset(chain, ticker, contractAddress, currency)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to fetch price for %s %s", chain, contractAddress)
            BigDecimal.ZERO
        }

    fun onClickAddLp(poolId: String) {
        viewModelScope.safeLaunch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.ADD_LP.type,
                    poolId = poolId,
                )
            )
        }
    }

    fun onClickRemoveLp(poolId: String) {
        viewModelScope.safeLaunch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.REMOVE_LP.type,
                    poolId = poolId,
                )
            )
        }
    }

    fun setPositionSelectionDialogVisibility(show: Boolean) {
        viewModelScope.launch {
            if (show) {
                // Retry a previously-failed getPoolStats so reopening the dialog isn't a soft-lock.
                ensureAvailablePoolsLoaded()
                state.update {
                    it.copy(
                        showPositionSelectionDialog = true,
                        tempSelectedPositions = it.selectedPositions,
                    )
                }
            } else {
                state.update {
                    it.copy(
                        showPositionSelectionDialog = false,
                        tempSelectedPositions = it.selectedPositions,
                    )
                }
            }
        }
    }

    fun onPositionSelectionChange(positionTitle: String, isSelected: Boolean) {
        viewModelScope.launch {
            state.update { currentState ->
                val updatedPositions =
                    if (isSelected) {
                        currentState.tempSelectedPositions + positionTitle
                    } else {
                        currentState.tempSelectedPositions - positionTitle
                    }
                currentState.copy(tempSelectedPositions = updatedPositions)
            }
        }
    }

    fun onPositionSelectionDone() {
        viewModelScope.launch {
            val selectedPositions = state.value.tempSelectedPositions

            // A Done that changed nothing has nothing to persist and nothing to refetch. Compared
            // as sets because only membership decides what gets loaded.
            if (selectedPositions.toSet() == state.value.selectedPositions.toSet()) {
                state.update { it.copy(showPositionSelectionDialog = false) }
                return@launch
            }

            launch {
                withContext(ioDispatcher) {
                    defiPositionsRepository.saveSelectedPositions(
                        Chain.ThorChain,
                        vaultId,
                        selectedPositions,
                    )
                }
            }

            state.update {
                it.copy(showPositionSelectionDialog = false, selectedPositions = selectedPositions)
            }

            loadedTabs.clear()

            // Same reload set as a currency switch, so the header owes the same honesty: legs keep
            // their pre-selection values, and without this a newly added pool would leave the total
            // reading as settled — and short by that pool — until its fetch lands.
            resetTotalsToPending()

            bondedNodesRefreshTrigger.value++

            loadBondedNodes()

            loadStakingPositions()

            // If a previous getPoolStats fetch failed, retry now so the user isn't soft-locked.
            ensureAvailablePoolsLoaded()
            reloadLpTab()
        }
    }

    fun onClickBond(nodeAddress: String) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            val runeCoin = vault.coins.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }

            if (runeCoin != null) {
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        type = DeFiNavActions.BOND.type,
                        chainId = Chain.ThorChain.id,
                        tokenId = runeCoin.id,
                        address = nodeAddress,
                    )
                )
            } else {
                Timber.e("RUNE coin not found in vault")
            }
        }
    }

    fun onClickUnBond(nodeAddress: String) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            val runeCoin = vault.coins.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }

            if (runeCoin != null) {
                val bondedAmount =
                    activeBondedNodes.firstOrNull { it.node.address == nodeAddress }?.amount
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        type = DeFiNavActions.UNBOND.type,
                        chainId = Chain.ThorChain.id,
                        tokenId = runeCoin.id,
                        address = nodeAddress,
                        bondedAmount = bondedAmount?.toString(),
                    )
                )
            } else {
                Timber.e("RUNE coin not found in vault")
            }
        }
    }

    fun bondToNode() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            val runeCoin = vault.coins.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }

            if (runeCoin != null) {
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        type = DeFiNavActions.BOND.type,
                        chainId = Chain.ThorChain.id,
                        tokenId = runeCoin.id,
                    )
                )
            } else {
                Timber.e("RUNE coin not found in vault")
            }
        }
    }

    fun onNavigateToFunctions(defiNavAction: DeFiNavActions) {
        viewModelScope.launch {
            val tokenId =
                when (defiNavAction) {
                    DeFiNavActions.STAKE_RUJI -> Coins.ThorChain.RUJI.id
                    DeFiNavActions.UNSTAKE_RUJI -> Coins.ThorChain.RUJI.id
                    // Compounding is funded with RUJI; only the redemption starts from the receipt.
                    DeFiNavActions.STAKE_SRUJI -> Coins.ThorChain.RUJI.id
                    DeFiNavActions.UNSTAKE_SRUJI -> Coins.ThorChain.sRUJI.id
                    DeFiNavActions.STAKE_TCY -> Coins.ThorChain.TCY.id
                    DeFiNavActions.UNSTAKE_TCY -> Coins.ThorChain.TCY.id
                    DeFiNavActions.STAKE_STCY -> Coins.ThorChain.TCY.id
                    DeFiNavActions.UNSTAKE_STCY -> Coins.ThorChain.sTCY.id
                    DeFiNavActions.MINT_YTCY -> Coins.ThorChain.TCY.id
                    DeFiNavActions.REDEEM_YTCY -> Coins.ThorChain.yTCY.id
                    DeFiNavActions.MINT_YRUNE -> Coins.ThorChain.RUNE.id
                    DeFiNavActions.REDEEM_YRUNE -> Coins.ThorChain.yRUNE.id
                    DeFiNavActions.WITHDRAW_RUJI -> "USDC-${Chain.ThorChain.id}"
                    else -> null
                }
            if (tokenId == null) {
                navigator.route(
                    Route.Deposit(
                        vaultId = vaultId,
                        chainId = Chain.ThorChain.id,
                        depositType = defiNavAction.type,
                    )
                )
            } else {
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        type = defiNavAction.type,
                        chainId = Chain.ThorChain.id,
                        tokenId = tokenId,
                        address = defiNavAction.getContractByDeFiAction(), // dst address
                    )
                )
            }
        }
    }

    fun onClickTransfer() {
        viewModelScope.launch {
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = Chain.ThorChain.id,
                    tokenId = Coins.ThorChain.sTCY.id,
                )
            )
        }
    }

    companion object {
        private const val RUJI_SYMBOL = "RUJI"
        private const val RUJI_REWARDS_SYMBOL = "USDC"
        private const val POSITION_DISPLAY_SCALE = 4

        /**
         * The Manage-Positions key a placeholder is gated on. Both RUJI positions are toggled by
         * the single "RUJI" tile, so the auto-compounding placeholder borrows the bonded ticker;
         * every other placeholder is keyed by its own ticker.
         *
         * Reads the ticker off the coin rather than off [StakePositionUiModel.stakedAmountDisplay],
         * which now carries a formatted "0 TICKER" amount instead of the bare ticker.
         */
        private fun StakePositionUiModel.selectionKey(): String =
            if (coin.id == Coins.ThorChain.sRUJI.id) Coins.ThorChain.RUJI.ticker else coin.ticker

        private fun loadDefaultStakingPositions(): List<StakePositionUiModel> {
            val rujiCoin = Coins.ThorChain.RUJI
            val sruji = Coins.ThorChain.sRUJI
            val tcy = Coins.ThorChain.TCY
            val stcy = Coins.ThorChain.sTCY
            val ytcy = Coins.ThorChain.yTCY
            val yrune = Coins.ThorChain.yRUNE

            return listOf(
                StakePositionUiModel(
                    coin = rujiCoin,
                    stakeAssetHeader = UiText.StringResource(R.string.staked_ruji_header),
                    stakedAmountDisplay = "0 ${rujiCoin.ticker}",
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null,
                ),
                StakePositionUiModel(
                    coin = sruji,
                    stakeAssetHeader =
                        UiText.FormattedText(
                            R.string.defi_header_compounded,
                            listOf(rujiCoin.ticker),
                        ),
                    stakedAmountDisplay = "0 ${sruji.ticker}",
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null,
                ),
                StakePositionUiModel(
                    coin = tcy,
                    stakeAssetHeader = UiText.StringResource(R.string.staked_tcy_header),
                    stakedAmountDisplay = "0 ${tcy.ticker}",
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null,
                ),
                StakePositionUiModel(
                    coin = stcy,
                    stakeAssetHeader = UiText.StringResource(R.string.compounded_tcy_header),
                    stakedAmountDisplay = "0 ${stcy.ticker}",
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null,
                ),
                StakePositionUiModel(
                    coin = ytcy,
                    stakeAssetHeader = UiText.StringResource(R.string.staked_ytcy_header),
                    stakedAmountDisplay = "0 ${ytcy.ticker}",
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null,
                ),
                StakePositionUiModel(
                    coin = yrune,
                    stakeAssetHeader = UiText.StringResource(R.string.staked_yrune_header),
                    stakedAmountDisplay = "0 ${yrune.ticker}",
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null,
                ),
            )
        }
    }
}

data class StakeDefaultValues(val stakeElements: List<StakingElement> = emptyList()) {
    data class StakingElement(val coin: Coin, val amount: BigInteger)
}

private fun String.toLpPositionDialogModel(): PositionUiModelDialog {
    val parsed = parseThorChainPool(this)
    val resolvedAssetLogo = lpAssetLogoRes(parsed.chain, parsed.ticker, parsed.contractAddress)
    return PositionUiModelDialog(
        logo = resolvedAssetLogo ?: getCoinLogo(parsed.ticker.lowercase()),
        ticker = "RUNE/${parsed.ticker}",
        isSelected = false,
        positionKey = this,
        chainLogo = parsed.chain?.monoToneLogo,
    )
}
