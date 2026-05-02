package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaMemberDetails
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

@Immutable
internal sealed interface MayachainDefiUiState {
    data object Loading : MayachainDefiUiState

    data class Success(val data: MayachainDefiPositionsUiModel) : MayachainDefiUiState

    data class Error(val message: String) : MayachainDefiUiState
}

@Immutable
internal data class MayachainDefiPositionsUiModel(
    val totalAmountPrice: String = DEFAULT_ZERO_BALANCE,
    val bonded: BondedTabUiModel = BondedTabUiModel(totalBondedAmount = "0 CACAO"),
    val staking: StakingTabUiModel = StakingTabUiModel(),
    val lp: LpTabUiModel = LpTabUiModel(),
    val isTotalAmountLoading: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val selectedTab: Int = DeFiTab.BONDED.displayNameRes,
    val showPositionSelectionDialog: Boolean = false,
    val bondPositionsDialog: List<PositionUiModelDialog> = MAYA_BOND_POSITIONS_DIALOG,
    val stakingPositionsDialog: List<PositionUiModelDialog> = MAYA_STAKE_POSITIONS_DIALOG,
    val lpPositionsDialog: List<PositionUiModelDialog> = emptyList(),
    val selectedPositions: List<String> = MAYA_DEFAULT_SELECTED_POSITIONS,
    val tempSelectedPositions: List<String> = MAYA_DEFAULT_SELECTED_POSITIONS,
) {
    companion object {
        const val DEFAULT_ZERO_BALANCE = "$0.00"
    }
}

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
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val defiPositionsRepository: DefiPositionsRepository,
) : ViewModel() {

    private lateinit var vaultId: String

    private val _state = MutableStateFlow<MayachainDefiUiState>(MayachainDefiUiState.Loading)
    val state: StateFlow<MayachainDefiUiState> = _state.asStateFlow()

    private val bondedNodesRefreshTrigger = MutableStateFlow(0)

    private val _totalBondedRaw = MutableStateFlow(BigInteger.ZERO)
    private val _totalStakingRaw = MutableStateFlow(BigInteger.ZERO)

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
        _state.value = MayachainDefiUiState.Success(MayachainDefiPositionsUiModel())
        loadBalanceVisibility()
        savedPositionsJob?.cancel()
        savedPositionsJob = loadSavedPositions()
        lpDialogJob?.cancel()
        lpDialogJob = loadLpPositionsForDialog()
        observeTotalRawJob?.cancel()
        observeTotalRawJob = observeTotalRaw()
    }

    private fun observeTotalRaw(): Job =
        viewModelScope.launch {
            combine(_totalBondedRaw, _totalStakingRaw) { bonded, staking -> bonded + staking }
                .collect { totalRaw -> updateTotalFiatValue(totalRaw) }
        }

    private fun loadBalanceVisibility() {
        viewModelScope.safeLaunch {
            val isVisible =
                withContext(Dispatchers.IO) { balanceVisibilityRepository.getVisibility(vaultId) }
            updateModel { it.copy(isBalanceVisible = isVisible) }
        }
    }

    private fun loadSavedPositions(): Job =
        viewModelScope.launch {
            defiPositionsRepository.getSelectedPositions(vaultId).collect { saved ->
                val hasMayaPositions =
                    saved.any { it in MAYA_STATIC_POSITION_KEYS || it.contains(".") }
                val positions =
                    if (hasMayaPositions) {
                        saved.toList()
                    } else {
                        MAYA_DEFAULT_SELECTED_POSITIONS
                    }
                updateModel {
                    it.copy(selectedPositions = positions, tempSelectedPositions = positions)
                }
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
                val pools =
                    withContext(Dispatchers.IO) { mayachainBondRepository.getMayaNodePools() }
                val lpPositions = pools.map { pool -> pool.toPositionDialogModel() }
                updateModel { it.copy(lpPositionsDialog = lpPositions) }
                reloadLpTab()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to load Maya LP positions for dialog")
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadBondedNodes() {
        updateModel { it.copy(bonded = it.bonded.copy(isLoading = true)) }

        try {
            val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
            val cacaoCoin =
                vault?.coins?.find {
                    it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                }

            if (cacaoCoin == null) {
                Timber.e("Vault does not have CACAO coin")
                _totalBondedRaw.value = BigInteger.ZERO
                updateModel { it.copy(bonded = it.bonded.copy(isLoading = false)) }
                return
            }

            bondedNodesRefreshTrigger
                .flatMapLatest { bondUseCase.getActiveNodes(vaultId, cacaoCoin.address) }
                .catch { t ->
                    Timber.e(t)
                    _totalBondedRaw.value = BigInteger.ZERO
                    updateModel { it.copy(bonded = it.bonded.copy(isLoading = false)) }
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
                            isTotalAmountLoading = false,
                            bonded =
                                BondedTabUiModel(
                                    isLoading = false,
                                    totalBondedAmount = totalBonded,
                                    totalBondedPrice = bondedPrice,
                                    nodes = nodeUiModels,
                                ),
                        )
                    }

                    _totalBondedRaw.value = totalBondedRaw
                }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            _totalBondedRaw.value = BigInteger.ZERO
            updateModel {
                it.copy(isTotalAmountLoading = false, bonded = it.bonded.copy(isLoading = false))
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
            val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
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
                    updateModel {
                        it.copy(
                            staking =
                                StakingTabUiModel(
                                    positions = listOf(loadingPosition.copy(isLoading = false))
                                )
                        )
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
                        )
                    updateModel {
                        it.copy(staking = StakingTabUiModel(positions = listOf(position)))
                    }
                }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t, "Failed to load CACAO staking position")
            _totalStakingRaw.value = BigInteger.ZERO
            updateModel {
                it.copy(
                    staking =
                        StakingTabUiModel(
                            positions = listOf(loadingPosition.copy(isLoading = false))
                        )
                )
            }
        }
    }

    private fun updateTotalFiatValue(totalRaw: BigInteger) {
        viewModelScope.launch {
            try {
                val currency = appCurrencyRepository.currency.first()
                val totalInCacao = totalRaw.toValue(10)
                val fiatValue = createFiatValue(totalInCacao, Coins.MayaChain.CACAO, currency)
                val currencyFormat =
                    withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }
                updateModel {
                    it.copy(
                        totalAmountPrice = currencyFormat.format(fiatValue.value),
                        isTotalAmountLoading = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to calculate Maya total fiat value")
                updateModel { it.copy(isTotalAmountLoading = false) }
            }
        }
    }

    private suspend fun calculateStakingFiatPrice(amount: BigDecimal): String {
        return try {
            val currency = appCurrencyRepository.currency.first()
            val fiatValue = createFiatValue(amount, Coins.MayaChain.CACAO, currency)
            val currencyFormat =
                withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }
            currencyFormat.format(fiatValue.value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to calculate Maya staking fiat price")
            ""
        }
    }

    private suspend fun calculateBondedFiatPrice(totalBondedRaw: BigInteger): String {
        return try {
            val totalInCacao = totalBondedRaw.toValue(10)
            val currency = appCurrencyRepository.currency.first()
            val fiatValue = createFiatValue(totalInCacao, Coins.MayaChain.CACAO, currency)
            val currencyFormat =
                withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }
            currencyFormat.format(fiatValue.value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to calculate Maya bonded fiat price")
            ""
        }
    }

    private suspend fun createFiatValue(
        amount: BigDecimal,
        coin: com.vultisig.wallet.data.models.Coin,
        currency: AppCurrency,
    ): FiatValue {
        return try {
            if (amount == BigDecimal.ZERO) return FiatValue(BigDecimal.ZERO, currency.ticker)
            val price =
                tokenPriceRepository.getCachedPrice(tokenId = coin.id, appCurrency = currency)
                    ?: tokenPriceRepository.getPriceByContactAddress(
                        coin.chain.id,
                        coin.contractAddress,
                    )
            FiatValue(
                value = amount.multiply(price).setScale(2, RoundingMode.DOWN),
                currency = currency.ticker,
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            FiatValue(value = BigDecimal.ZERO, currency = currency.ticker)
        }
    }

    private suspend fun createFiatValueFromPoolAsset(
        amount: BigDecimal,
        chain: Chain,
        ticker: String,
        contractAddress: String,
        currency: AppCurrency,
    ): FiatValue {
        return try {
            if (amount == BigDecimal.ZERO) return FiatValue(BigDecimal.ZERO, currency.ticker)
            val price =
                tokenPriceRepository.getCachedPrice(
                    tokenId = "$ticker-${chain.id}",
                    appCurrency = currency,
                ) ?: tokenPriceRepository.getPriceByContactAddress(chain.id, contractAddress)
            FiatValue(
                value = amount.multiply(price).setScale(2, RoundingMode.DOWN),
                currency = currency.ticker,
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            FiatValue(value = BigDecimal.ZERO, currency = currency.ticker)
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
            return
        }

        if (model.lpPositionsDialog.isEmpty()) {
            updateModel { it.copy(lp = it.lp.copy(isLoading = true)) }
            return
        }

        if (selectedPools.isEmpty()) {
            loadLpJob?.cancel()
            updateModel { it.copy(lp = LpTabUiModel(isLoading = false, positions = emptyList())) }
            return
        }

        val placeholderPositions =
            selectedPools.map { pool ->
                val assetTicker = pool.ticker.substringAfter("/")
                LpPositionUiModel(
                    titleLp = "${pool.ticker} Pool",
                    totalPriceLp = MayachainDefiPositionsUiModel.DEFAULT_ZERO_BALANCE,
                    icon = (pool.logo as? Int) ?: R.drawable.cacao,
                    apr = null,
                    position = "0 CACAO + 0 $assetTicker",
                    positionKey = pool.positionKey,
                    canRemove = false,
                )
            }
        updateModel {
            it.copy(lp = LpTabUiModel(isLoading = true, positions = placeholderPositions))
        }

        loadLpJob?.cancel()
        loadLpJob =
            viewModelScope.safeLaunch {
                val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
                val cacaoCoin =
                    vault?.coins?.find {
                        it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                    }

                if (cacaoCoin == null) {
                    Timber.e("Vault does not have CACAO coin for LP positions")
                    updateModel {
                        it.copy(
                            lp = LpTabUiModel(isLoading = false, positions = placeholderPositions)
                        )
                    }
                    return@safeLaunch
                }

                if (!chainAccountAddressRepository.isValid(Chain.MayaChain, cacaoCoin.address)) {
                    Timber.e("CACAO coin address failed MayaChain validation")
                    updateModel {
                        it.copy(
                            lp = LpTabUiModel(isLoading = false, positions = placeholderPositions)
                        )
                    }
                    return@safeLaunch
                }

                val (memberDetails, poolStats) =
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            val memberDeferred = async {
                                try {
                                    mayachainBondRepository.getMemberDetails(cacaoCoin.address)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Timber.e(e, "Failed to fetch Maya member details")
                                    MayaMemberDetails()
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

                val memberPoolMap = memberDetails.pools.associateBy { it.pool }
                val poolStatsMap = poolStats.associateBy { it.asset }
                val currency = appCurrencyRepository.currency.first()
                val currencyFormat =
                    withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }

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
                                    it.ticker == assetCoinTicker &&
                                    (assetContractAddress.isEmpty() ||
                                        it.contractAddress.equals(
                                            assetContractAddress,
                                            ignoreCase = true,
                                        ))
                            }

                        val assetFiatValue =
                            if (assetCoin != null) {
                                createFiatValue(assetAmount, assetCoin, currency)
                            } else if (assetChain != null) {
                                createFiatValueFromPoolAsset(
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
                            createFiatValue(cacaoAmount, Coins.MayaChain.CACAO, currency)
                        val totalFiatValue =
                            FiatValue(
                                value = assetFiatValue.value.add(cacaoFiatValue.value),
                                currency = currency.ticker,
                            )

                        val apr = stats?.annualPercentageRate?.toDoubleOrNull()

                        LpPositionUiModel(
                            titleLp = "${pool.ticker} Pool",
                            totalPriceLp = currencyFormat.format(totalFiatValue.value),
                            icon = (pool.logo as? Int) ?: R.drawable.cacao,
                            apr = apr?.formatPercentage(),
                            position =
                                "${cacaoAmount.setScale(4, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} CACAO + ${assetAmount.setScale(4, RoundingMode.DOWN).stripTrailingZeros().toPlainString()} $assetCoinTicker",
                            positionKey = pool.positionKey,
                            canRemove = liquidityUnits > BigDecimal.ZERO,
                        )
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
            defiPositionsRepository.saveSelectedPositions(vaultId, selectedPositions)
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

    fun onBackClick() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }
}

private fun MayaNodePool.toPositionDialogModel(): PositionUiModelDialog {
    val assetTicker = asset.substringAfter(".")
    val coinName = assetTicker.substringBefore("-").lowercase()
    val coinLogo = getCoinLogo(coinName)
    val logo: Int =
        if (coinLogo is Int) coinLogo
        else mayaPoolChainPrefixToChain(asset.substringBefore("."))?.logo ?: R.drawable.cacao
    return PositionUiModelDialog(
        logo = logo,
        ticker = "CACAO/${assetTicker.substringBefore("-")}",
        isSelected = false,
        positionKey = asset,
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
