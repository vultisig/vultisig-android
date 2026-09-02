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
import com.vultisig.wallet.data.models.ThorChainPendingLpDeposit
import com.vultisig.wallet.data.models.Vault
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
import com.vultisig.wallet.data.usecases.GetThorChainPendingLpDepositsUseCase
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
import com.vultisig.wallet.ui.utils.lpRefundsInUiText
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
    // Flips true once the available-pools fetch settles, success or failure. Until then the LP tab
    // should stay in loading state instead of flashing the empty/no-positions UI when the user has
    // selected positions whose keys don't yet match the (empty) dialog list. A failed fetch settles
    // too: an unclearable spinner is worse than the no-positions container, which at least carries
    // the Manage Positions retry.
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
    private val getThorChainPendingLpDepositsUseCase: GetThorChainPendingLpDepositsUseCase,
    private val snapshotCache: DeFiPositionsSnapshotCache,
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
    private var loadPendingLpJob: Job? = null
    private var loadBondedNodesJob: Job? = null
    private var loadStakingPositionsJob: Job? = null

    // A caller-supplied tab is applied once and then forgotten: the screen leaves and re-enters
    // composition every time the wallet / DeFi toggle flips, and re-seeding there would throw away
    // whichever tab the user had chosen since.
    private var hasAppliedInitialTab = false

    // Guards the one-shot restore: setData also runs on every pull-to-refresh, and seeding there
    // would drop whatever the refresh has already published back onto the snapshot.
    private var hasRestoredSnapshot = false

    fun setData(vaultId: VaultId, initialTab: DeFiTab? = null) {
        this.vaultId = vaultId
        restoreSnapshot(vaultId)
        if (initialTab != null && !hasAppliedInitialTab) {
            hasAppliedInitialTab = true
            state.update { it.copy(selectedTab = initialTab.displayNameRes) }
        }
        loadBalanceVisibility()
        lpDialogJob?.cancel()
        lpDialogJob = loadLpPositionsForDialog()
        loadSavedPositions()
        loadTotalValue()
        currencyJob?.cancel()
        currencyJob = observeCurrencyChanges()
    }

    /**
     * Paints the state this vault's screen was last showing, so a re-entry starts from the cards,
     * totals and enabled set the user left behind instead of from an empty model whose defaults
     * would flash RUNE + TCY back on. Everything here is refreshed by the loads [setData] kicks off
     * straight after; only the position picker is dropped, because a sheet the user closed by
     * leaving the screen must not reopen itself.
     */
    private fun restoreSnapshot(vaultId: VaultId) {
        if (hasRestoredSnapshot) return
        hasRestoredSnapshot = true
        val cached = snapshotCache.read(vaultId, ThorchainDefiPositionsUiModel::class) ?: return
        state.value =
            cached.copy(
                showPositionSelectionDialog = false,
                tempSelectedPositions = cached.selectedPositions,
            )
    }

    override fun onCleared() {
        if (::vaultId.isInitialized) {
            snapshotCache.write(vaultId, state.value)
        }
        super.onCleared()
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
                // lpDialogLoaded means "settled", not "succeeded": leaving it false parks the tab
                // in a spinner that nothing can ever clear, while any pending half-deposit still
                // renders through it. Settling drops the tab to its no-positions container, whose
                // Manage Positions button is the retry, and lets a pending card stand on its own.
                state.update { it.copy(lpDialogLoaded = true) }
                // The header total is a separate concern: reloadLpTab parks the LP leg unreported
                // while it waits for this dataset, so report it as zero here or the total would
                // never see all five legs and would spin forever.
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
                    dropFiatPricedInPreviousCurrency()

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
     * Drops the fiat the cards carry when the display currency changes.
     *
     * Every other reload now leaves settled figures in place and swaps them when the fresh ones
     * land, which is what stops a re-entry looking like a first open. A currency switch is the one
     * case where that would be wrong: these strings were formatted for the currency being left, so
     * they are cleared here and the reload puts each card back on its shimmer until it can price
     * them again.
     */
    private fun dropFiatPricedInPreviousCurrency() {
        state.update { current ->
            current.copy(
                bonded = current.bonded.copy(totalBondedPrice = null),
                staking =
                    current.staking.copy(
                        positions =
                            current.staking.positions.map { position ->
                                position.copy(isLoading = true, stakedFiatDisplay = null)
                            }
                    ),
                lp = current.lp.copy(positions = emptyList(), livePoolKeys = emptySet()),
            )
        }
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

            loadPendingLpDeposits()
        }
    }

    /**
     * Loads half-finished symmetric adds. Deliberately independent of [reloadLpTab]: those are
     * gated on the pools the user picked in Manage Positions, and a deposit stuck in a pool they
     * never selected is exactly the one that would otherwise be refunded unseen.
     */
    private fun loadPendingLpDeposits() {
        loadPendingLpJob?.cancel()
        // Re-arm the gate only when there is a list that could now be wrong. A deposit found before
        // the app was backgrounded may already have been refunded, and Complete Deposit on a
        // refunded one just burns inbound gas — so hide it behind the spinner until this scan
        // confirms it. With nothing pending there is nothing to invalidate, and re-arming would
        // flash the whole tab to a spinner on every resume for the users who have no half-deposit
        // at all.
        if (state.value.lp.pendingDeposits.isNotEmpty()) {
            state.update { it.copy(lp = it.lp.copy(pendingDepositsLoaded = false)) }
        }
        loadPendingLpJob =
            viewModelScope.safeLaunch(
                onError = {
                    Timber.e(it, "Failed to load pending THORChain LP deposits")
                    markPendingLpDepositsSettled()
                }
            ) {
                val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                val runeCoin =
                    vault?.coins?.find { it.ticker == "RUNE" && it.chain == Chain.ThorChain }
                if (runeCoin == null) {
                    markPendingLpDepositsSettled()
                    return@safeLaunch
                }

                val pending =
                    withContext(ioDispatcher) {
                        getThorChainPendingLpDepositsUseCase(runeAddress = runeCoin.address)
                    }

                val models = pending.map { it.toUiModel(vault.assetAddressForPool(it.pool)) }
                state.update {
                    it.copy(lp = it.lp.copy(pendingDeposits = models, pendingDepositsLoaded = true))
                }
            }
    }

    /**
     * Marks the scan as settled without erasing [LpTabUiModel.pendingDeposits]: a failed reload
     * must still show a list an earlier one found, because the refund timer is running on it and
     * the user needs to know. Completion is withdrawn instead — the whole point of re-scanning is
     * that THORChain may already have refunded the deposit, and nothing downstream re-checks:
     * onClickCompletePendingLp reads this list directly, and the add-liquidity preflight only asks
     * about pool-wide pause and status, never about this record. A card the scan could not confirm
     * therefore stays visible but cannot spend inbound gas on a dead deposit.
     */
    private fun markPendingLpDepositsSettled() {
        state.update {
            it.copy(
                lp =
                    it.lp.copy(
                        pendingDeposits =
                            it.lp.pendingDeposits.map { deposit ->
                                deposit.copy(canComplete = false)
                            },
                        pendingDepositsLoaded = true,
                    )
            )
        }
    }

    /**
     * Re-scans for pending half-deposits when the screen comes back to the foreground. They sit on
     * a refund timer that keeps running while the app is backgrounded, so a stale list can offer
     * Complete Deposit on a deposit THORChain has already refunded. Positions are left to
     * pull-to-refresh; only this leg goes stale on its own.
     */
    fun onScreenResumed() {
        if (!::vaultId.isInitialized) return
        loadPendingLpDeposits()
    }

    private fun String.shortenForDisplay(): String =
        if (length > 12) "${take(6)}…${takeLast(4)}" else this

    /** The vault's address on a pool's non-RUNE chain, or `null` when it holds no account there. */
    private fun Vault.assetAddressForPool(poolId: String): String? {
        val assetChain = parseThorChainPool(poolId).chain ?: return null
        if (assetChain == Chain.ThorChain) return null
        val coin =
            coins.firstOrNull { it.chain == assetChain && it.isNativeToken }
                ?: coins.firstOrNull { it.chain == assetChain }
        return coin?.address
    }

    private fun ThorChainPendingLpDeposit.toUiModel(
        assetAddress: String?
    ): PendingLpDepositUiModel {
        val parsed = parseThorChainPool(pool)
        val assetTicker = parsed.ticker
        val runeTicker = Coins.ThorChain.RUNE.ticker
        val depositedAmount =
            if (isRunePending) {
                CoinType.THORCHAIN.toValue(pendingRune)
                    .stripTrailingZeros()
                    .formatTokenAmount(runeTicker)
            } else {
                CoinType.THORCHAIN.toValue(pendingAsset)
                    .stripTrailingZeros()
                    .formatTokenAmount(assetTicker)
            }

        // The card is about the side that has not arrived — its title names that ticker — so the
        // icon and chain badge have to follow it. Pinning them to the pool's asset put an ETH logo
        // next to "Waiting for matching RUNE deposit".
        val awaitedChain = if (isRunePending) parsed.chain else Chain.ThorChain
        val awaitedTicker = if (isRunePending) assetTicker else runeTicker
        val awaitedContractAddress = if (isRunePending) parsed.contractAddress else ""

        return PendingLpDepositUiModel(
            poolId = pool,
            icon =
                lpAssetLogoRes(awaitedChain, awaitedTicker, awaitedContractAddress)
                    ?: getCoinLogo(awaitedTicker.lowercase()),
            chainLogo = awaitedChain?.monoToneLogo,
            awaitedTicker = awaitedTicker,
            depositedAmount = depositedAmount,
            pairedAddress = pairedAddress?.shortenForDisplay(),
            refundsIn = blocksUntilRefund?.let { lpRefundsInUiText(it * THORCHAIN_BLOCK_SECONDS) },
            // Completing means sending the missing side, which needs an account on its chain. When
            // RUNE is the missing half the vault always has one.
            canComplete = !isRunePending || assetAddress != null,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBondedNodes() {
        loadedTabs.add(DeFiTab.BONDED.displayNameRes)

        // Cancel any in-flight collector before starting a new one. getActiveNodes never
        // completes, so without this each refresh would stack another collector that writes
        // state out of order.
        loadBondedNodesJob?.cancel()
        loadBondedNodesJob =
            viewModelScope.launch {
                if (!state.value.selectedPositions.hasBondPositions()) {
                    _totalValueBond.value = BigInteger.ZERO

                    val zero = zeroFiat()
                    state.update { it.copy(bonded = emptyBondedTabUiModel(zero)) }
                    return@launch
                }

                // A total the last load already priced stays on screen while this one runs. The
                // shimmer belongs to a cold start; re-arming it on every entry and pull is what
                // made a re-entry look like a first open.
                state.update {
                    it.copy(bonded = it.bonded.copy(isLoading = it.bonded.totalBondedPrice == null))
                }

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
                // Cards the last load already settled stay up while this one runs, and are
                // replaced in place when it lands; only a position with nothing behind it yet
                // gets the placeholder. Rebuilding every card from the placeholders is what blanked
                // the tab on re-entry.
                // Keyed by coin rather than by selection key: RUJI and its auto-compounding
                // sRUJI card are both toggled by the one "RUJI" tile, so keying by that would
                // collapse the two into one and render it twice.
                val settled =
                    state.value.staking.positions
                        .filterNot { it.isLoading }
                        .associateBy { it.coin.id }
                val defaultLoadingPositions =
                    loadDefaultStakingPositions()
                        .filter { position -> selectedPositions.contains(position.selectionKey()) }
                        .map { positionUiModel ->
                            settled[positionUiModel.coin.id]
                                ?: positionUiModel.copy(isLoading = true, stakedFiatDisplay = zero)
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
                            it.coin.id == Coins.ThorChain.sTCY.id ||
                            it.coin.id == Coins.ThorChain.ybRUNE.id
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

                            val isBondedRuneReceipt =
                                coin.id.equals(Coins.ThorChain.ybRUNE.id, true)

                            // Both compounding receipts are plain THORChain bank denoms, so the
                            // vault can move either one to another address — #5585 asks for it on
                            // ybRUNE, and iOS offers Transfer on every compound position. Matched
                            // on the coin id: a substring of the ticker is what left this card
                            // with the wrong logo, since ybRUNE contains none of the others.
                            val canTransfer =
                                coin.id.equals(Coins.ThorChain.sTCY.id, true) || isBondedRuneReceipt

                            val headerResId =
                                if (supportsMint) {
                                    R.string.defi_header_minted
                                } else if (
                                    defaultPosition.coin.id.equals(Coins.ThorChain.sTCY.id, true) ||
                                        isBondedRuneReceipt
                                ) {
                                    R.string.defi_header_compounded
                                } else {
                                    R.string.defi_header_staked
                                }
                            // Titled in the same unit the amount below it is counted in. The
                            // sRUJI card can say RUJI because its amount is the pool's RUJI
                            // liquidSize; this one is the raw receipt balance, and a share is
                            // worth more than one bRUNE, so naming it bRUNE would understate the
                            // position by the compounding it exists to earn.
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
            state.update { it.copy(lp = it.lp.copy(isLoading = it.lp.positions.isEmpty())) }
            ensureAvailablePoolsLoaded()
            return
        }

        val selectedPools = state.value.lpPositionsDialog.filter { it.positionKey in selectedKeys }

        if (selectedPools.isEmpty()) {
            loadLpJob?.cancel()
            state.update {
                it.copy(
                    lp =
                        it.lp.copy(
                            isLoading = false,
                            positions = emptyList(),
                            livePoolKeys = emptySet(),
                        )
                )
            }
            loadLpJob = viewModelScope.launch { reportLpFiat(BigDecimal.ZERO) }
            return
        }

        state.update { it.copy(lp = it.lp.copy(isLoading = it.lp.positions.isEmpty())) }

        loadLpJob?.cancel()
        loadLpJob =
            viewModelScope.launch {
                // The zero has to be resolved before the placeholders are built: a failed load
                // freezes these exact objects into the terminal state, so a placeholder that
                // snapshotted an unresolved zero would strand the card on the dash for good.
                val zero = zeroFiat()
                // Show placeholder cards for each selected pool first — even pools where the user
                // has no liquidity yet should be visible so the Add button is reachable.
                // Pools the last load already priced keep their card; only a pool with nothing
                // behind it yet falls back to the placeholder, and the shimmer is raised only for
                // those — a refresh over cards that already carry figures leaves them readable.
                val lpTab = state.value.lp
                val loaded =
                    lpTab.positions
                        .filter { it.positionKey in lpTab.livePoolKeys }
                        .associateBy { it.positionKey }
                val placeholders =
                    selectedPools.map { pool ->
                        loaded[pool.positionKey]?.copy(isLoading = false)
                            ?: pool.toPlaceholderUiModel(zero).copy(isLoading = true)
                    }
                val isCold = selectedPools.any { loaded[it.positionKey] == null }
                state.update {
                    it.copy(lp = it.lp.copy(isLoading = isCold, positions = placeholders))
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
                            it.copy(
                                lp =
                                    it.lp.copy(
                                        isLoading = false,
                                        positions =
                                            placeholders.map { p -> p.copy(isLoading = false) },
                                    )
                            )
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
                    val livePoolKeys =
                        selectedPools
                            .filterNot { it in failedSelectedPools }
                            .map { it.positionKey }
                            .toSet()
                    state.update {
                        it.copy(
                            lp =
                                it.lp.copy(
                                    isLoading = false,
                                    positions = merged,
                                    livePoolKeys = livePoolKeys,
                                )
                        )
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to load THORChain LP positions")
                    reportLpFiat(BigDecimal.ZERO)
                    state.update {
                        it.copy(
                            lp =
                                it.lp.copy(
                                    isLoading = false,
                                    positions = placeholders.map { p -> p.copy(isLoading = false) },
                                )
                        )
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

    /**
     * Sends the user into the deposit flow for the half they still owe, on that half's own chain,
     * with the pool pre-selected. The memo the flow builds is what THORChain matches against the
     * pending record, so this completes the add rather than opening a second one.
     */
    fun onClickCompletePendingLp(poolId: String) {
        val pending = state.value.lp.pendingDeposits.find { it.poolId == poolId } ?: return
        // Also enforced by the card's disabled button; held here too so a card the last scan
        // could not confirm can never route into a deposit, whatever the UI does.
        if (!pending.canComplete) return
        val missingSideChain =
            if (pending.awaitedTicker == Coins.ThorChain.RUNE.ticker) Chain.ThorChain
            else parseThorChainPool(poolId).chain ?: return

        viewModelScope.safeLaunch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = missingSideChain.id,
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
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.BOND.type,
                    bondAddress = nodeAddress,
                )
            )
        }
    }

    fun onClickUnBond(nodeAddress: String) {
        viewModelScope.launch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.UNBOND.type,
                    bondAddress = nodeAddress,
                )
            )
        }
    }

    fun bondToNode() {
        viewModelScope.launch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.ThorChain.id,
                    depositType = DeFiNavActions.BOND.type,
                )
            )
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
                    // Bonding spends bRUNE; only the unbond starts from the receipt.
                    DeFiNavActions.STAKE_YBRUNE -> Coins.ThorChain.bRUNE.id
                    DeFiNavActions.UNSTAKE_YBRUNE -> Coins.ThorChain.ybRUNE.id
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

    /**
     * Opens the plain send form on [coin], the position's own receipt.
     *
     * Carries the position's coin rather than a fixed one: sTCY was the only transferable card when
     * this was written, and the ybRUNE receipt would otherwise have sent the wrong token.
     */
    fun onClickTransfer(coin: Coin) {
        viewModelScope.launch {
            navigator.route(
                Route.Send(vaultId = vaultId, chainId = coin.chain.id, tokenId = coin.id)
            )
        }
    }

    companion object {
        private const val RUJI_SYMBOL = "RUJI"
        private const val RUJI_REWARDS_SYMBOL = "USDC"
        private const val POSITION_DISPLAY_SCALE = 4
        private const val THORCHAIN_BLOCK_SECONDS = 6L

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
            val ybrune = Coins.ThorChain.ybRUNE

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
                StakePositionUiModel(
                    coin = ybrune,
                    stakeAssetHeader =
                        UiText.FormattedText(
                            R.string.defi_header_compounded,
                            listOf(ybrune.ticker),
                        ),
                    stakedAmountDisplay = "0 ${ybrune.ticker}",
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
