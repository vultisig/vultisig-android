package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.MayaMemberDetails
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.lpAssetLogoRes
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.MayachainBondUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.MAYA_BOND_CACAO_KEY
import com.vultisig.wallet.ui.screens.v2.defi.MAYA_STAKE_CACAO_KEY
import com.vultisig.wallet.ui.screens.v2.defi.formatAddress
import com.vultisig.wallet.ui.screens.v2.defi.formatAmount
import com.vultisig.wallet.ui.screens.v2.defi.formatDate
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
import com.vultisig.wallet.ui.screens.v2.defi.hasBondPositions
import com.vultisig.wallet.ui.screens.v2.defi.hasMayaStakingPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState.Companion.fromApiStatus
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

private val MAYA_BOND_POSITIONS_DIALOG: List<PositionUiModelDialog>
    get() =
        listOf(
            PositionUiModelDialog(
                logo = getCoinLogo(Coins.MayaChain.CACAO.logo),
                ticker = Coins.MayaChain.CACAO.ticker,
                isSelected = true,
                positionKey = MAYA_BOND_CACAO_KEY,
            )
        )

private val MAYA_STAKE_POSITIONS_DIALOG: List<PositionUiModelDialog>
    get() =
        listOf(
            PositionUiModelDialog(
                logo = getCoinLogo(Coins.MayaChain.CACAO.logo),
                ticker = Coins.MayaChain.CACAO.ticker,
                isSelected = true,
                positionKey = MAYA_STAKE_CACAO_KEY,
            )
        )

private val MAYA_STATIC_POSITION_KEYS = setOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)

private val MAYA_DEFAULT_SELECTED_POSITIONS = listOf(MAYA_BOND_CACAO_KEY, MAYA_STAKE_CACAO_KEY)

// Nothing bonded, said in CACAO. emptyBondedTabUiModel is the Thorchain screen's and would render
// this as "0 RUNE".
private const val ZERO_CACAO_BONDED = "0 CACAO"

@Immutable
internal sealed interface MayachainDefiUiState {
    data object Loading : MayachainDefiUiState

    data class Success(val data: MayachainDefiPositionsUiModel) : MayachainDefiUiState

    data class Error(val message: String) : MayachainDefiUiState
}

@Immutable
internal data class MayachainDefiPositionsUiModel(
    // Null until the total is priced in the user's currency; a hardcoded "$0.00" was wrong for
    // every non-USD user and indistinguishable from a genuinely empty vault.
    val totalAmountPrice: String? = null,
    val bonded: BondedTabUiModel = BondedTabUiModel(totalBondedAmount = ZERO_CACAO_BONDED),
    val staking: StakingTabUiModel = StakingTabUiModel(),
    val lp: LpTabUiModel = LpTabUiModel(),
    val isTotalAmountLoading: Boolean = true,
    val isBalanceVisible: Boolean = true,
    val selectedTab: Int = DeFiTab.BONDED.displayNameRes,
    val showPositionSelectionDialog: Boolean = false,
    val bondPositionsDialog: List<PositionUiModelDialog> = MAYA_BOND_POSITIONS_DIALOG,
    val stakingPositionsDialog: List<PositionUiModelDialog> = MAYA_STAKE_POSITIONS_DIALOG,
    val lpPositionsDialog: List<PositionUiModelDialog> = emptyList(),
    // Flips true once the pool fetch returns, whatever it returns. An empty [lpPositionsDialog]
    // cannot stand in for this: "not fetched yet" and "fetched, no pools available" would look
    // identical, and the LP leg would sit unreported forever on the second one.
    val lpDialogLoaded: Boolean = false,
    val selectedPositions: List<String> = MAYA_DEFAULT_SELECTED_POSITIONS,
    val tempSelectedPositions: List<String> = MAYA_DEFAULT_SELECTED_POSITIONS,
)

@HiltViewModel
internal class MayachainDefiPositionsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val bondUseCase: MayachainBondUseCase,
    private val mayachainBondRepository: MayachainBondRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val mayaCacaoStakingService: MayaCacaoStakingService,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val defiPositionsRepository: DefiPositionsRepository,
    private val fiatValueCalculator: DefiFiatValueCalculator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private lateinit var vaultId: String

    private val _state = MutableStateFlow<MayachainDefiUiState>(MayachainDefiUiState.Loading)
    val state: StateFlow<MayachainDefiUiState> = _state.asStateFlow()

    private val bondedNodesRefreshTrigger = MutableStateFlow(0)

    // Null until the leg reports in — loaded, empty, or failed. The header total is the sum of all
    // three, so pricing it while a leg is still unreported would publish a partial figure that
    // silently jumps as the rest land. Every terminal path in a leg must therefore assign here.
    private val _totalBondedRaw = MutableStateFlow<BigInteger?>(null)
    private val _totalStakingRaw = MutableStateFlow<BigInteger?>(null)
    // LP is priced from two assets per pool, so it joins the total already converted to fiat rather
    // than as a raw chain amount like the other legs — see [LpLegTotal] for why it carries a
    // currency and why an unpriceable load reports as unavailable rather than as zero.
    private val _totalLpFiat = MutableStateFlow<LpLegTotal?>(null)

    private var currencyJob: Job? = null
    private var observeTotalRawJob: Job? = null
    private var savedPositionsJob: Job? = null
    private var lpDialogJob: Job? = null
    private var loadBondedJob: Job? = null
    private var loadStakingJob: Job? = null
    private var loadLpJob: Job? = null

    private val currentModel: MayachainDefiPositionsUiModel
        get() =
            (_state.value as? MayachainDefiUiState.Success)?.data ?: MayachainDefiPositionsUiModel()

    private fun updateModel(
        transform: (MayachainDefiPositionsUiModel) -> MayachainDefiPositionsUiModel
    ) {
        _state.update { s ->
            if (s is MayachainDefiUiState.Success) s.copy(data = transform(s.data)) else s
        }
    }

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        if (_state.value !is MayachainDefiUiState.Success) {
            _state.value = MayachainDefiUiState.Success(MayachainDefiPositionsUiModel())
        }
        loadBalanceVisibility()
        savedPositionsJob?.cancel()
        savedPositionsJob = loadSavedPositions()
        lpDialogJob?.cancel()
        lpDialogJob = loadLpPositionsForDialog()
        observeTotalRawJob?.cancel()
        observeTotalRawJob = observeTotalRaw()
        currencyJob?.cancel()
        currencyJob = observeCurrencyChanges()
    }

    /**
     * Reports the LP leg together with the currency it was priced in, so a later total can tell a
     * fresh value from one left over from a previous currency.
     */
    private suspend fun reportLpFiat(value: BigDecimal) {
        val currency = appCurrencyRepository.currency.first()
        _totalLpFiat.value = LpLegTotal.Priced(FiatValue(value, currency.ticker))
    }

    /**
     * Re-prices every leg and every card when the user switches currency.
     *
     * Nothing re-converts on its own: the card fiat strings are formatted once at load time from
     * one-shot flows, and LP joins the total already converted, so a switch that only touched the
     * header would leave the Bonded and CACAO Staking cards reading in the currency the user just
     * left. Reloading is also what re-prices LP, which cannot be re-based from the stored
     * magnitude.
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
                    loadBondedJob?.cancel()
                    loadBondedJob = launch { loadBondedNodes() }
                    loadStakingJob?.cancel()
                    loadStakingJob = launch { loadStakingPosition() }
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
        _totalBondedRaw.value = null
        _totalStakingRaw.value = null
        _totalLpFiat.value = null
        updateModel { it.copy(totalAmountPrice = null, isTotalAmountLoading = true) }
    }

    private fun observeTotalRaw(): Job =
        viewModelScope.launch {
            combine(_totalBondedRaw, _totalStakingRaw, _totalLpFiat) { bonded, staking, lpFiat ->
                    if (bonded == null || staking == null || lpFiat == null) {
                        null
                    } else {
                        Pair(bonded + staking, lpFiat)
                    }
                }
                .filterNotNull()
                // collectLatest, not collect: pricing suspends on the currency lookup, and a run
                // started for an older set of legs could otherwise land after a newer one and
                // overwrite the header with a stale total.
                .collectLatest { (totalRaw, lpFiat) -> updateTotalFiatValue(totalRaw, lpFiat) }
        }

    private fun loadBalanceVisibility() {
        viewModelScope.safeLaunch {
            val isVisible =
                withContext(ioDispatcher) { balanceVisibilityRepository.getVisibility(vaultId) }
            updateModel { it.copy(isBalanceVisible = isVisible) }
        }
    }

    private fun loadSavedPositions(): Job =
        viewModelScope.launch {
            defiPositionsRepository
                .getSelectedPositions(Chain.MayaChain, vaultId)
                // Null is a vault that has never chosen on this chain, and it is the only case the
                // defaults belong to. That is what makes an empty set unambiguous here: it is a
                // selection the user cleared on purpose, and taking it at face value is safe now
                // that no other screen can write this key.
                .map { saved -> saved?.toList() ?: MAYA_DEFAULT_SELECTED_POSITIONS }
                // The store re-emits for writes that leave this vault's selection alone — another
                // screen saving into the same file, or this vault's first-ever save flipping the
                // key from absent to present, which it cannot read as unchanged — and a reload for
                // one of those would blank a settled header for nothing. Compared as sets: only
                // membership decides what gets loaded, and a stored set can come back in a
                // different order than the default list.
                .distinctUntilChanged { old, new -> old.toSet() == new.toSet() }
                .collect { positions ->
                    updateModel {
                        it.copy(selectedPositions = positions, tempSelectedPositions = positions)
                    }

                    // Every selection change re-runs this collector, so it reloads all three legs
                    // for the same reason a currency switch does and owes the header the same
                    // honesty: legs keep their pre-selection values, and without this a newly added
                    // pool would leave the total reading as settled — and short by that pool —
                    // until its fetch lands.
                    resetTotalsToPending()

                    reloadLpTab()

                    loadBondedJob?.cancel()
                    loadBondedJob = launch { loadBondedNodes() }
                    loadStakingJob?.cancel()
                    loadStakingJob = launch { loadStakingPosition() }
                }
        }

    private fun loadLpPositionsForDialog(): Job =
        viewModelScope.launch {
            try {
                val pools = withContext(ioDispatcher) { mayachainBondRepository.getMayaNodePools() }
                val lpPositions = pools.map { pool -> pool.toPositionDialogModel() }
                updateModel { it.copy(lpPositionsDialog = lpPositions, lpDialogLoaded = true) }
                reloadLpTab()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to load Maya LP positions for dialog")
                // The tab keeps its own loading state; the header total is a separate concern.
                // reloadLpTab parks the LP leg unreported while it waits for this dataset, so
                // report it as zero here or the total would never see all three legs.
                reportLpFiat(BigDecimal.ZERO)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadBondedNodes() {
        // Bond is a checkbox in Manage Positions like the others, and unchecking it has to drop the
        // card and its leg from the total — otherwise the header goes on counting a position the
        // user removed. Reported as a real zero rather than left unreported, or the total would
        // wait on a leg that is never coming.
        if (!currentModel.selectedPositions.hasBondPositions()) {
            _totalBondedRaw.value = BigInteger.ZERO
            val zero = zeroFiat()
            updateModel {
                it.copy(
                    bonded =
                        BondedTabUiModel(
                            totalBondedAmount = ZERO_CACAO_BONDED,
                            totalBondedPrice = zero,
                        )
                )
            }
            return
        }

        updateModel { it.copy(bonded = it.bonded.copy(isLoading = true)) }

        try {
            val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
            val cacaoCoin =
                vault?.coins?.find {
                    it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                }

            if (cacaoCoin == null) {
                Timber.e("Vault does not have CACAO coin")
                _totalBondedRaw.value = BigInteger.ZERO
                // No CACAO coin means nothing is bonded, which is a real zero rather than a price
                // we failed to resolve — say so instead of leaving the card on the dash.
                val zero = zeroFiat()
                updateModel {
                    it.copy(bonded = it.bonded.copy(isLoading = false, totalBondedPrice = zero))
                }
                return
            }

            bondedNodesRefreshTrigger
                .flatMapLatest {
                    bondUseCase.getActiveNodes(vaultId, cacaoCoin.address).onCompletion { cause ->
                        // A source that finishes without ever emitting is done, not pending.
                        // Report it, or the header total waits on a leg that will never arrive.
                        // Cancellation is the exception: flatMapLatest dropped this collector for
                        // a newer trigger, and reporting on behalf of a run that has been replaced
                        // understates the total until the replacement lands.
                        if (cause !is CancellationException) {
                            _totalBondedRaw.compareAndSet(null, BigInteger.ZERO)
                        }
                    }
                }
                .catch { t ->
                    Timber.e(t)
                    _totalBondedRaw.value = BigInteger.ZERO
                    val zero = zeroFiat()
                    updateModel {
                        it.copy(bonded = it.bonded.copy(isLoading = false, totalBondedPrice = zero))
                    }
                }
                .collect { activeNodes ->
                    val cacaoSymbol = Coins.MayaChain.CACAO.ticker

                    val nodeUiModels =
                        activeNodes.map { node ->
                            BondedNodeUiModel(
                                address = node.node.address.formatAddress(),
                                fullAddress = node.node.address,
                                status = node.node.state.fromApiStatus(),
                                apy = node.apy.formatPercentage(),
                                bondedAmount = node.amount.formatAmount(10, cacaoSymbol),
                                nextAward = formatCacaoReward(node.nextReward),
                                nextChurn = node.nextChurn.formatDate(),
                            )
                        }

                    val totalBondedRaw =
                        activeNodes.fold(BigInteger.ZERO) { acc, node -> acc + node.amount }
                    val totalBonded = totalBondedRaw.formatAmount(10, cacaoSymbol)

                    val bondedPrice = calculateBondedFiatPrice(totalBondedRaw)

                    updateModel {
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

                    _totalBondedRaw.value = totalBondedRaw
                }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            _totalBondedRaw.value = BigInteger.ZERO
            val zero = zeroFiat()
            updateModel {
                it.copy(bonded = it.bonded.copy(isLoading = false, totalBondedPrice = zero))
            }
        }
    }

    private suspend fun loadStakingPosition() {
        val selectedPositions = currentModel.selectedPositions
        if (!selectedPositions.hasMayaStakingPositions()) {
            _totalStakingRaw.value = BigInteger.ZERO
            updateModel { it.copy(staking = StakingTabUiModel(positions = emptyList())) }
            return
        }

        val loadingPosition =
            StakePositionUiModel(
                coin = Coins.MayaChain.CACAO,
                stakeAssetHeader = UiText.StringResource(R.string.cacao_pool),
                stakedAmountDisplay = "0 CACAO",
                apy = null,
                isLoading = true,
                canStake = false,
                canUnstake = false,
            )
        updateModel { it.copy(staking = StakingTabUiModel(positions = listOf(loadingPosition))) }

        try {
            val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
            val cacaoCoin =
                vault?.coins?.find {
                    it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                }
                    ?: run {
                        Timber.e("Vault does not have CACAO coin")
                        _totalStakingRaw.value = BigInteger.ZERO
                        updateModel {
                            it.copy(staking = StakingTabUiModel(positions = emptyList()))
                        }
                        return
                    }

            mayaCacaoStakingService
                .getStakingDetails(cacaoCoin.address)
                .catch { t ->
                    Timber.e(t, "Failed to load CACAO staking details")
                    _totalStakingRaw.value = BigInteger.ZERO
                    settleStakingPosition(loadingPosition)
                }
                // Same on the way out of a cancelled load: the switch that cancelled it has already
                // started the replacement, and settling the leg here would report a zero on that
                // replacement's behalf while its own fetch is still in flight.
                .onCompletion { cause ->
                    if (cause !is CancellationException) {
                        _totalStakingRaw.compareAndSet(null, BigInteger.ZERO)
                    }
                }
                .collect { details ->
                    _totalStakingRaw.value = details.stakeAmount
                    val stakeAmount = details.stakeAmount.toValue(10)
                    val stakedFiat = calculateStakingFiatPrice(stakeAmount)
                    val position =
                        StakePositionUiModel(
                            coin = Coins.MayaChain.CACAO,
                            stakeAssetHeader = UiText.StringResource(R.string.cacao_pool),
                            stakeAmount = stakeAmount,
                            stakedAmountDisplay =
                                "${stakeAmount.setScale(4, RoundingMode.DOWN).toPlainString()} CACAO",
                            stakedFiatDisplay = stakedFiat,
                            apy = details.apr?.formatPercentage(),
                            isLoading = false,
                            canStake = true,
                            canUnstake = details.canUnstake,
                            unstakeUnlocksInSeconds = details.unstakeUnlocksInSeconds,
                            isUnstakeMaturityUnknown = details.isUnstakeMaturityUnknown,
                        )
                    updateModel {
                        it.copy(staking = StakingTabUiModel(positions = listOf(position)))
                    }
                }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t, "Failed to load CACAO staking position")
            _totalStakingRaw.value = BigInteger.ZERO
            settleStakingPosition(loadingPosition)
        }
    }

    /**
     * Settles the CACAO card after a failed load. Clearing the spinner alone left it showing an
     * amount with no fiat line at all; a formatted zero states the position's worth instead.
     */
    private suspend fun settleStakingPosition(loadingPosition: StakePositionUiModel) {
        val zero = zeroFiat()
        updateModel {
            it.copy(
                staking =
                    StakingTabUiModel(
                        positions =
                            listOf(
                                loadingPosition.copy(isLoading = false, stakedFiatDisplay = zero)
                            )
                    )
            )
        }
    }

    /**
     * Prices the header total once every leg has reported. This is the only place that clears
     * [MayachainDefiPositionsUiModel.isTotalAmountLoading] — a leg settling its own card must not
     * stop the header spinner, because the other legs may still be in flight.
     */
    private suspend fun updateTotalFiatValue(totalRaw: BigInteger, lpLeg: LpLegTotal) {
        try {
            val lpFiat =
                when (lpLeg) {
                    // The LP positions couldn't be priced, and they read as zero when they can't. A
                    // sum including them would understate the real total while looking just as
                    // settled as a correct one, so say the total is unavailable instead of quietly
                    // getting it wrong.
                    LpLegTotal.Unavailable -> {
                        updateModel {
                            it.copy(totalAmountPrice = null, isTotalAmountLoading = false)
                        }
                        return
                    }

                    is LpLegTotal.Priced -> lpLeg.fiatValue
                }

            val currency = appCurrencyRepository.currency.first()
            if (lpFiat.currency != currency.ticker) {
                // The currency changed after LP was priced. The raw legs re-convert on every run,
                // but LP is stored already converted, so adding it now would sum two currencies.
                // observeCurrencyChanges has already dropped the leg and asked for a re-price, so
                // park the header on its spinner: keeping the old figure on screen would show a
                // total in the currency the user just navigated away from.
                updateModel { it.copy(totalAmountPrice = null, isTotalAmountLoading = true) }
                return
            }
            val totalInCacao = totalRaw.toValue(10)
            val fiatValue =
                fiatValueCalculator.createFiatValue(totalInCacao, Coins.MayaChain.CACAO, currency)
            val currencyFormat =
                withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
            updateModel {
                it.copy(
                    totalAmountPrice = currencyFormat.format(fiatValue.value.add(lpFiat.value)),
                    isTotalAmountLoading = false,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to calculate Maya total fiat value")
            updateModel { it.copy(isTotalAmountLoading = false) }
        }
    }

    /**
     * Zero rendered in the user's currency. Failed legs settle here rather than at a blank, so a
     * card always states what the position is worth instead of dropping the line entirely. Null
     * when the currency format itself can't be resolved, which the UI shows as unavailable.
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

    private suspend fun calculateStakingFiatPrice(amount: BigDecimal): String? {
        return try {
            val currency = appCurrencyRepository.currency.first()
            val fiatValue =
                fiatValueCalculator.createFiatValue(amount, Coins.MayaChain.CACAO, currency)
            val currencyFormat =
                withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
            currencyFormat.format(fiatValue.value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to calculate Maya staking fiat price")
            null
        }
    }

    private suspend fun calculateBondedFiatPrice(totalBondedRaw: BigInteger): String? {
        return try {
            val totalInCacao = totalBondedRaw.toValue(10)
            val currency = appCurrencyRepository.currency.first()
            val fiatValue =
                fiatValueCalculator.createFiatValue(totalInCacao, Coins.MayaChain.CACAO, currency)
            val currencyFormat =
                withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }
            currencyFormat.format(fiatValue.value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to calculate Maya bonded fiat price")
            null
        }
    }

    private fun reloadLpTab() {
        val model = currentModel
        val selectedKeys = model.selectedPositions.toSet()
        val selectedLpKeys = selectedKeys - MAYA_STATIC_POSITION_KEYS
        val selectedPools = model.lpPositionsDialog.filter { it.positionKey in selectedKeys }

        if (selectedLpKeys.isEmpty()) {
            loadLpJob?.cancel()
            updateModel { it.copy(lp = LpTabUiModel(isLoading = false, positions = emptyList())) }
            loadLpJob = viewModelScope.launch { reportLpFiat(BigDecimal.ZERO) }
            return
        }

        // Wait for the pool fetch, not for a non-empty list: a chain that legitimately reports no
        // pools would otherwise park the LP leg here for good and strand the header on its spinner,
        // pull-to-refresh included. loadLpPositionsForDialog calls back here once it settles.
        if (!model.lpDialogLoaded) {
            updateModel { it.copy(lp = it.lp.copy(isLoading = true)) }
            return
        }

        if (selectedPools.isEmpty()) {
            loadLpJob?.cancel()
            updateModel { it.copy(lp = LpTabUiModel(isLoading = false, positions = emptyList())) }
            loadLpJob = viewModelScope.launch { reportLpFiat(BigDecimal.ZERO) }
            return
        }

        updateModel { it.copy(lp = it.lp.copy(isLoading = true)) }

        loadLpJob?.cancel()
        loadLpJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Maya LP positions")
                    reportLpFiat(BigDecimal.ZERO)
                    updateModel { it.copy(lp = it.lp.copy(isLoading = false)) }
                }
            ) {
                // The zero has to be resolved before the placeholders are built: a failed load
                // freezes these exact objects into the terminal state, so a placeholder that
                // snapshotted an unresolved zero would strand the card on the dash for good.
                val zero = zeroFiat()
                val placeholderPositions =
                    selectedPools.map { pool ->
                        val assetTicker = pool.ticker.substringAfter("/")
                        LpPositionUiModel(
                            titleLp = "${pool.ticker} Pool",
                            totalPriceLp = zero,
                            icon = pool.logo,
                            assetTicker = assetTicker,
                            apr = null,
                            position = "0 CACAO + 0 $assetTicker",
                            positionKey = pool.positionKey,
                            canRemove = false,
                            chainLogo = pool.chainLogo as? Int,
                        )
                    }
                updateModel {
                    it.copy(lp = LpTabUiModel(isLoading = true, positions = placeholderPositions))
                }

                val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                val cacaoCoin =
                    vault?.coins?.find {
                        it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                    }

                if (cacaoCoin == null) {
                    Timber.e("Vault does not have CACAO coin for LP positions")
                    reportLpFiat(BigDecimal.ZERO)
                    updateModel {
                        it.copy(
                            lp = LpTabUiModel(isLoading = false, positions = placeholderPositions)
                        )
                    }
                    return@safeLaunch
                }

                if (!chainAccountAddressRepository.isValid(Chain.MayaChain, cacaoCoin.address)) {
                    Timber.e("CACAO coin address failed MayaChain validation")
                    reportLpFiat(BigDecimal.ZERO)
                    updateModel {
                        it.copy(
                            lp = LpTabUiModel(isLoading = false, positions = placeholderPositions)
                        )
                    }
                    return@safeLaunch
                }

                val (loadedMemberDetails, poolStats) =
                    withContext(ioDispatcher) {
                        coroutineScope {
                            val memberDeferred = async {
                                try {
                                    mayachainBondRepository.getMemberDetails(cacaoCoin.address)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Timber.e(e, "Failed to fetch Maya member details")
                                    // Null rather than an empty MayaMemberDetails: an empty one
                                    // reads as "this vault holds no liquidity anywhere", which is a
                                    // claim a failed request has no business making.
                                    null
                                }
                            }
                            val statsDeferred = async {
                                try {
                                    mayachainBondRepository.getLpPoolStats()
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Timber.e(e, "Failed to fetch Maya LP pool stats")
                                    emptyList()
                                }
                            }
                            Pair(memberDeferred.await(), statsDeferred.await())
                        }
                    }

                // Losing the pool stats is survivable — the share calculation below falls back to
                // the amounts the user added, which still values the position. Losing the member
                // details is not: every selected pool would read as zero liquidity.
                val memberDetails = loadedMemberDetails ?: MayaMemberDetails()
                val isPriceable = loadedMemberDetails != null

                val memberPoolMap = memberDetails.pools.associateBy { it.pool }
                val poolStatsMap = poolStats.associateBy { it.asset }
                val currency = appCurrencyRepository.currency.first()
                val currencyFormat =
                    withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }

                val lpPositions =
                    selectedPools.map { pool ->
                        val memberPool = memberPoolMap[pool.positionKey]
                        val stats = poolStatsMap[pool.positionKey]

                        val liquidityUnits =
                            memberPool?.liquidityUnits?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val totalPoolUnits = stats?.units?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val poolAssetDepth =
                            stats?.assetDepth?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val poolCacaoDepth =
                            stats?.cacaoDepth?.toBigDecimalOrNull() ?: BigDecimal.ZERO

                        val (assetAmount, cacaoAmount) =
                            if (totalPoolUnits > BigDecimal.ZERO) {
                                val share =
                                    liquidityUnits.divide(totalPoolUnits, 18, RoundingMode.HALF_UP)
                                val asset =
                                    poolAssetDepth
                                        .multiply(share)
                                        .divide(BigDecimal.TEN.pow(8), 18, RoundingMode.HALF_UP)
                                val cacao =
                                    poolCacaoDepth
                                        .multiply(share)
                                        .divide(BigDecimal.TEN.pow(10), 18, RoundingMode.HALF_UP)
                                Pair(asset, cacao)
                            } else {
                                val assetAdded =
                                    memberPool?.assetAdded?.toBigIntegerOrNull() ?: BigInteger.ZERO
                                val cacaoAdded =
                                    memberPool?.cacaoAdded?.toBigIntegerOrNull() ?: BigInteger.ZERO
                                Pair(assetAdded.toValue(8), cacaoAdded.toValue(10))
                            }

                        val assetChain =
                            mayaPoolChainPrefixToChain(pool.positionKey.substringBefore("."))
                        val assetCoinTicker =
                            pool.positionKey.substringAfter(".").substringBefore("-")
                        val assetContractAddress =
                            pool.positionKey.substringAfter(".").substringAfter("-", "")
                        val assetCoin =
                            vault.coins.find {
                                it.chain == assetChain &&
                                    // Case-insensitive, as the THORChain LP tab already matches:
                                    // chain metadata is not uniformly uppercased the way the EVM
                                    // token lists are, so an exact match dropped a held token
                                    // whose case differed from the pool string and priced its leg
                                    // at zero.
                                    it.ticker.equals(assetCoinTicker, ignoreCase = true) &&
                                    (assetContractAddress.isEmpty() ||
                                        it.contractAddress.equals(
                                            assetContractAddress,
                                            ignoreCase = true,
                                        ))
                            }

                        val assetFiatValue =
                            if (assetCoin != null) {
                                fiatValueCalculator.createFiatValue(
                                    assetAmount,
                                    assetCoin,
                                    currency,
                                )
                            } else if (assetChain != null) {
                                fiatValueCalculator.createFiatValueFromPoolAsset(
                                    assetAmount,
                                    assetChain,
                                    assetCoinTicker,
                                    assetContractAddress,
                                    currency,
                                )
                            } else {
                                FiatValue(BigDecimal.ZERO, currency.ticker)
                            }
                        val cacaoFiatValue =
                            fiatValueCalculator.createFiatValue(
                                cacaoAmount,
                                Coins.MayaChain.CACAO,
                                currency,
                            )
                        val totalFiatValue =
                            FiatValue(
                                value = assetFiatValue.value.add(cacaoFiatValue.value),
                                currency = currency.ticker,
                            )

                        val apr = stats?.annualPercentageRate?.toDoubleOrNull()

                        LpPositionUiModel(
                            titleLp = "${pool.ticker} Pool",
                            totalPriceLp =
                                if (isPriceable) currencyFormat.format(totalFiatValue.value)
                                else null,
                            totalFiatValue = totalFiatValue.value,
                            icon = pool.logo,
                            assetTicker = assetCoinTicker,
                            apr = apr?.formatPercentage(),
                            position =
                                "${cacaoAmount.setScale(4, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} CACAO + ${assetAmount.setScale(4, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} $assetCoinTicker",
                            positionKey = pool.positionKey,
                            canRemove = liquidityUnits > BigDecimal.ZERO,
                            chainLogo = assetChain?.monoToneLogo,
                        )
                    }

                _totalLpFiat.value =
                    if (isPriceable) {
                        LpLegTotal.Priced(
                            FiatValue(
                                lpPositions.fold(BigDecimal.ZERO) { acc, position ->
                                    acc + position.totalFiatValue
                                },
                                currency.ticker,
                            )
                        )
                    } else {
                        // Every position folded in as zero above, so this sum would understate the
                        // header total rather than admit a value is missing.
                        LpLegTotal.Unavailable
                    }
                updateModel {
                    it.copy(lp = LpTabUiModel(isLoading = false, positions = lpPositions))
                }
            }
    }

    fun onTabSelected(tab: DeFiTab) {
        updateModel { it.copy(selectedTab = tab.displayNameRes) }
    }

    fun setPositionSelectionDialogVisibility(visible: Boolean) {
        updateModel {
            it.copy(
                showPositionSelectionDialog = visible,
                tempSelectedPositions = it.selectedPositions,
            )
        }
    }

    fun onPositionSelectionChange(ticker: String, selected: Boolean) {
        updateModel { current ->
            val updated =
                if (selected) current.tempSelectedPositions + ticker
                else current.tempSelectedPositions - ticker
            current.copy(tempSelectedPositions = updated)
        }
    }

    fun onPositionSelectionDone() {
        viewModelScope.launch {
            val selectedPositions = currentModel.tempSelectedPositions

            // A Done that changed nothing has nothing to persist and nothing to refetch.
            if (selectedPositions.toSet() == currentModel.selectedPositions.toSet()) {
                updateModel { it.copy(showPositionSelectionDialog = false) }
                return@launch
            }

            defiPositionsRepository.saveSelectedPositions(
                Chain.MayaChain,
                vaultId,
                selectedPositions,
            )
            updateModel {
                it.copy(showPositionSelectionDialog = false, selectedPositions = selectedPositions)
            }
        }
    }

    fun onNavigateToStake(action: DeFiNavActions) {
        viewModelScope.launch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.MayaChain.id,
                    depositType = action.type,
                )
            )
        }
    }

    fun onNavigateToLp(poolId: String, action: DeFiNavActions) {
        viewModelScope.launch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.MayaChain.id,
                    depositType = action.type,
                    poolId = poolId,
                )
            )
        }
    }

    fun onClickBond(nodeAddress: String) {
        viewModelScope.launch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.MayaChain.id,
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
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.UNBOND.type,
                    bondAddress = nodeAddress,
                )
            )
        }
    }

    fun bondToNode() {
        viewModelScope.launch {
            navigator.route(Route.BondForm(vaultId = vaultId, chainId = Chain.MayaChain.id))
        }
    }
}

private fun MayaNodePool.toPositionDialogModel(): PositionUiModelDialog {
    val tickerPart = asset.substringAfter(".").substringBefore("-")
    val contractAddress = asset.substringAfter(".").substringAfter("-", "")
    val chain = mayaPoolChainPrefixToChain(asset.substringBefore("."))
    val resolvedAssetLogo = lpAssetLogoRes(chain, tickerPart, contractAddress)
    return PositionUiModelDialog(
        logo = resolvedAssetLogo ?: getCoinLogo(tickerPart.lowercase()),
        ticker = "CACAO/$tickerPart",
        isSelected = false,
        positionKey = asset,
        chainLogo = chain?.monoToneLogo,
    )
}

private fun mayaPoolChainPrefixToChain(prefix: String): Chain? =
    when (prefix.uppercase()) {
        "BTC" -> Chain.Bitcoin
        "ETH" -> Chain.Ethereum
        "DASH" -> Chain.Dash
        "KUJI" -> Chain.Kujira
        "MAYA" -> Chain.MayaChain
        "BASE" -> Chain.Base
        "ARB" -> Chain.Arbitrum
        "AVAX" -> Chain.Avalanche
        "BSC" -> Chain.BscChain
        else -> null
    }

private fun formatCacaoReward(reward: Double): String {
    val rewardBase = BigDecimal.valueOf(reward).setScale(0, RoundingMode.DOWN).toBigInteger()
    val cacaoAmount = rewardBase.toValue(10).setScale(4, RoundingMode.DOWN)
    return "${cacaoAmount.toPlainString()} ${Coins.MayaChain.CACAO.ticker}"
}
