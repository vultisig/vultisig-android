package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.symbol
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
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.screens.v2.defi.thorchainSupportStakingDeFi
import com.vultisig.wallet.ui.screens.v2.defi.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

internal data class ThorchainDefiPositionsUiModel(
    // tabs info
    val totalAmountPrice: String = ThorchainDefiPositionsViewModel.DEFAULT_ZERO_BALANCE,
    val selectedTab: Int = R.string.defi_tab_bonded,
    val bonded: BondedTabUiModel = BondedTabUiModel(),
    val staking: StakingTabUiModel = StakingTabUiModel(),
    val lp: LpTabUiModel = LpTabUiModel(),
    val isTotalAmountLoading: Boolean = false,
    val isBalanceVisible: Boolean = true,

    // position selection dialog
    val showPositionSelectionDialog: Boolean = false,
    val bondPositionsDialog: List<PositionUiModelDialog> = defaultPositionsBondDialog(),
    val stakingPositionsDialog: List<PositionUiModelDialog> = defaultPositionsStakingDialog(),
    val lpPositionsDialog: List<PositionUiModelDialog> = emptyList(),
    val selectedPositions: List<String> = defaultSelectedPositionsDialog(),
    val tempSelectedPositions: List<String> = defaultSelectedPositionsDialog(),
)

internal data class BondedTabUiModel(
    val isLoading: Boolean = false,
    val totalBondedAmount: String = "0 ${Chain.ThorChain.coinType.symbol}",
    val nodes: List<BondedNodeUiModel> = emptyList(),
)

internal data class StakingTabUiModel(
    val positions: List<StakePositionUiModel> = emptyList()
)

internal data class LpTabUiModel(
    val isLoading: Boolean = false,
    val positions: List<LpPositionUiModel> = emptyList(),
)

internal data class LpPositionUiModel(
    val titleLp: String,
    val totalPriceLp: String,
    val icon: Int,
    val apr: String?,
    val position: String,
)

internal data class StakePositionUiModel(
    val coin: Coin,
    val stakeAssetHeader: String,
    val stakeAmount: String,
    val apy: String?,
    val isLoading: Boolean = false,
    val supportsMint: Boolean = false,
    val canWithdraw: Boolean = false,
    val canStake: Boolean = true,
    val canUnstake: Boolean = false,
    val rewards: String? = null,
    val nextReward: String? = null,
    val nextPayout: String? = null,
)

internal data class BondedNodeUiModel(
    val address: String,
    val fullAddress: String,
    val status: BondNodeState,
    val apy: String,
    val bondedAmount: String,
    val nextAward: String,
    val nextChurn: String,
)

data class TotalDefiValue(
    val bondAmount: BigInteger = BigInteger.ZERO,
    val defaultStakeValues: StakeDefaultValues = StakeDefaultValues(),
    val rujiStakeAmount: BigInteger = BigInteger.ZERO,
    val tcyStakeAmount: BigInteger = BigInteger.ZERO,
    val isLoading: Boolean = false,
)

@HiltViewModel
internal class ThorchainDefiPositionsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val bondUseCase: ThorchainBondUseCase,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val rujiStakingService: RujiStakingService,
    private val tcyStakingService: TCYStakingService,
    private val defiPositionsRepository: DefiPositionsRepository,
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
) : ViewModel() {

    private lateinit var vaultId: String

    val state = MutableStateFlow(ThorchainDefiPositionsUiModel())

    private val bondedNodesRefreshTrigger = MutableStateFlow(0)

    private val loadedTabs = mutableSetOf<Int>()

    private val _totalValueBond = MutableStateFlow(BigInteger.ZERO)
    private val _totalValueDefaultStake = MutableStateFlow(StakeDefaultValues())
    private val _totalValueRujiStake = MutableStateFlow(BigInteger.ZERO)
    private val _totalValueTCYStake = MutableStateFlow(BigInteger.ZERO)
    private val _isLoadingTotalAmount = MutableStateFlow(true)

    val totalValueBond: StateFlow<BigInteger> = _totalValueBond
    val totalValueDefaultStake: StateFlow<StakeDefaultValues> = _totalValueDefaultStake
    val totalValueRujiStake: StateFlow<BigInteger> = _totalValueRujiStake
    val totalValueTCYStake: StateFlow<BigInteger> = _totalValueTCYStake
    val isLoadingTotalAmount: StateFlow<Boolean> = _isLoadingTotalAmount



    fun setData(vaultId: VaultId){
        this.vaultId = vaultId
        loadBalanceVisibility()
        loadSavedPositions()
        loadTotalValue()
    }

    private fun loadBalanceVisibility() {
        viewModelScope.launch {
            val isVisible = withContext(Dispatchers.IO) {
                balanceVisibilityRepository.getVisibility(vaultId)
            }
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
                isLoadingTotalAmount,
            ) { bondValue, stakeValue, rujiStake, tcyStake, isLoading ->
                TotalDefiValue(
                    bondAmount = bondValue,
                    defaultStakeValues = stakeValue,
                    rujiStakeAmount = rujiStake,
                    tcyStakeAmount = tcyStake,
                    isLoading = isLoading,
                )
            }.collect { totalValue ->
                if (!totalValue.isLoading) {
                    handleTotalValueUpdate(totalValue)
                }
            }
        }
    }

    private fun handleTotalValueUpdate(totalValue: TotalDefiValue) {
        viewModelScope.launch {
            val totalInRune = CoinType.THORCHAIN.toValue(totalValue.bondAmount)
            val totalInRuji = CoinType.THORCHAIN.toValue(totalValue.rujiStakeAmount)
            val totalInTCY = CoinType.THORCHAIN.toValue(totalValue.tcyStakeAmount)

            try {
                val currency = appCurrencyRepository.currency.first()

                val runeFiatValue = createFiatValue(totalInRune, Coins.ThorChain.RUNE, currency)
                val rujiFiatValue = createFiatValue(totalInRuji, Coins.ThorChain.RUJI, currency)
                val tcyFiatValue = createFiatValue(totalInTCY, Coins.ThorChain.TCY, currency)

                val defaultStakingFiatValues =
                    totalValue.defaultStakeValues.stakeElements.map { position ->
                        val decimalAmount = CoinType.THORCHAIN.toValue(position.amount)
                        createFiatValue(decimalAmount, position.coin, currency)
                    }

                val totalFiatValue = listOf(runeFiatValue, rujiFiatValue, tcyFiatValue)
                    .plus(defaultStakingFiatValues)
                    .fold(FiatValue(BigDecimal.ZERO, currency.ticker)) { acc, fiatValue ->
                        acc + fiatValue
                    }

                val currencyFormat = withContext(Dispatchers.IO) {
                    appCurrencyRepository.getCurrencyFormat()
                }

                state.update {
                    it.copy(
                        totalAmountPrice = currencyFormat.format(totalFiatValue.value),
                        isTotalAmountLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate total fiat value")

                state.update {
                    it.copy(
                        isTotalAmountLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun createFiatValue(
        amount: BigDecimal,
        coin: Coin,
        currency: AppCurrency
    ): FiatValue {
        try {
            if (amount == BigDecimal.ZERO) {
                return FiatValue(BigDecimal.ZERO, currency.ticker)
            }

            val price = tokenPriceRepository.getCachedPrice(
                tokenId = coin.id,
                appCurrency = currency
            ) ?: tokenPriceRepository.getPriceByContactAddress(coin.chain.id, coin.contractAddress)

            return FiatValue(
                value = amount.multiply(price).setScale(2, RoundingMode.DOWN),
                currency = currency.ticker
            )
        } catch (t: Throwable) {
            Timber.e(t)

            return FiatValue(
                value = BigDecimal.ZERO,
                currency = currency.ticker
            )
        }
    }

    private fun loadSavedPositions() {
        viewModelScope.launch {
            val savedPositions = defiPositionsRepository.getSelectedPositions(vaultId).first()
            state.update {
                it.copy(
                    selectedPositions = savedPositions.toList(),
                    tempSelectedPositions = savedPositions.toList()
                )
            }

            launch {
                loadBondedNodes()
            }

            launch {
                loadStakingPositions()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBondedNodes() {
        loadedTabs.add(DeFiTab.BONDED.displayNameRes)

        viewModelScope.launch {
            if (!state.value.selectedPositions.hasBondPositions()) {
                _totalValueBond.value = BigInteger.ZERO

                state.update {
                    it.copy(
                        bonded = emptyBondedTabUiModel(),
                    )
                }
                return@launch
            }

            state.update {
                it.copy(
                    bonded = it.bonded.copy(isLoading = true)
                )
            }

            // Load selected positions, if disabled then show nothing
            try {
                val vault = withContext(Dispatchers.IO) {
                    vaultRepository.get(vaultId)
                }
                val runeCoin = vault?.coins?.find { it.chain.id == Chain.ThorChain.id }

                if (runeCoin == null) {
                    Timber.e("Vault does not have RUNE coin")
                    state.update {
                        it.copy(
                            bonded = it.bonded.copy(isLoading = false)
                        )
                    }
                    return@launch
                }

                val address = runeCoin.address

                bondedNodesRefreshTrigger
                    .flatMapLatest {
                        bondUseCase.getActiveNodes(vaultId, address)
                    }
                    .catch { it ->
                        Timber.e(it)
                        state.update {
                            it.copy(
                                bonded = it.bonded.copy(isLoading = false)
                            )
                        }
                    }
                    .collect { activeNodes ->
                        // Format UI data and show
                        val nodeUiModels = activeNodes.map { it.toUiModel() }
                        val totalBonded = calculateTotalBonded(activeNodes)

                        state.update {
                            it.copy(
                                isTotalAmountLoading = false,
                                bonded = BondedTabUiModel(
                                    isLoading = false,
                                    totalBondedAmount = totalBonded,
                                    nodes = nodeUiModels
                                )
                            )
                        }

                        activeNodes.fold(BigInteger.ZERO) { acc, node ->
                            acc + node.amount
                        }.let {
                            updateTotalValueStatus(it, false)
                        }
                    }
            } catch (t: Throwable) {
                Timber.e(t)
                state.update {
                    it.copy(
                        isTotalAmountLoading = false,
                        bonded = it.bonded.copy(isLoading = false)
                    )
                }
            }
        }
    }

    private fun updateTotalValueStatus(amount: BigInteger, loading: Boolean) {
        _totalValueBond.update {
            amount
        }
        _isLoadingTotalAmount.update {
            loading
        }
    }

    private fun calculateTotalBonded(nodes: List<BondedNodePosition>): String {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return total.formatAmount(CoinType.THORCHAIN)
    }

    private fun loadStakingPositions() {
        loadedTabs.add(DeFiTab.STAKED.displayNameRes)

        viewModelScope.launch {
            val selectedPositions = state.value.selectedPositions

            // Initial Loading Status
            if (!selectedPositions.hasStakingPositions()) {
                _totalValueDefaultStake.update { StakeDefaultValues() }
                _totalValueRujiStake.update { BigInteger.ZERO }
                _totalValueTCYStake.update { BigInteger.ZERO }
                _isLoadingTotalAmount.update { false }

                state.update {
                    it.copy(
                        staking = emptyStakingTabUiModel()
                    )
                }
                return@launch
            }
            val defaultLoadingPositions = loadDefaultStakingPositions().filter { coin ->
                selectedPositions.contains(coin.stakeAmount)
            }.map { positionUiModel ->
                positionUiModel.copy(isLoading = true)
            }
            state.update {
                it.copy(
                    staking = StakingTabUiModel(
                        positions = defaultLoadingPositions,
                    )
                )
            }

            try {
                val vault = withContext(Dispatchers.IO) {
                    vaultRepository.get(vaultId)
                }

                val runeCoin = vault?.coins?.find { it.chain.id == Chain.ThorChain.id }

                if (runeCoin == null) {
                    Timber.e("Vault does not have RUNE coin")

                    state.update {
                        it.copy(
                            staking = it.staking.copy(
                                positions = it.staking.positions.map { position ->
                                    position.copy(isLoading = false)
                                }
                            )
                        )
                    }
                    return@launch
                }

                val address = runeCoin.address
                val coinsToLoad = thorchainSupportStakingDeFi.filter { coin ->
                    selectedPositions.contains(coin.ticker)
                }.map { coin -> coin.id }

                if (coinsToLoad.contains(Coins.ThorChain.RUJI.id)) {
                    createRujiStakePosition(address, vaultId)
                }
                if (coinsToLoad.contains(Coins.ThorChain.TCY.id)) {
                    createTCYStakePosition(address, vaultId)
                }

                createGenericStakePosition(address, vaultId, coinsToLoad)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to load staking positions")
                state.update {
                    it.copy(
                        staking = it.staking.copy(
                            positions = it.staking.positions.map { position ->
                                position.copy(isLoading = false)
                            }
                        )
                    )
                }
            }
        }
    }

    private fun createRujiStakePosition(
        address: String,
        vaultId: String
    ) {
        viewModelScope.launch {
            rujiStakingService.getStakingDetails(address, vaultId)
                .catch { t ->
                    Timber.e(t, "Failed to load staking positions RUJI")
                    state.update {
                        it.copy(
                            staking = it.staking.copy(
                                positions = it.staking.positions.map { position ->
                                    if (position.coin.id == Coins.ThorChain.RUJI.id) {
                                        position.copy(isLoading = false)
                                    } else {
                                        position
                                    }
                                }
                            )
                        )
                    }
                }
                .collect { details ->
                    val stakedAmount = Chain.ThorChain.coinType.toValue(details.stakeAmount)
                    val formattedAmount = "${stakedAmount.toPlainString()} $RUJI_SYMBOL"

                    val rewards = details.rewards?.let { rewardAmount ->
                        val rewardAmountFormatted = Chain.ThorChain.coinType.toValue(rewardAmount)
                        val rewardValue = rewardAmountFormatted.setScale(6, RoundingMode.DOWN)
                        "${rewardValue.toPlainString()} ${details.rewardsCoin?.ticker ?: RUJI_REWARDS_SYMBOL}"
                    }

                    val stakePosition = StakePositionUiModel(
                        coin = details.coin,
                        stakeAssetHeader = "Staked $RUJI_SYMBOL",
                        stakeAmount = formattedAmount,
                        apy = details.apr?.formatPercentage(),
                        canWithdraw = details.rewards?.let { it > BigDecimal.ZERO } == true,
                        canStake = true,
                        canUnstake = details.stakeAmount > BigInteger.ZERO,
                        rewards = rewards,
                        nextReward = null,
                        nextPayout = null
                    )

                    updateExistingPosition(stakePosition)

                    _totalValueRujiStake.update { details.stakeAmount }
                    _isLoadingTotalAmount.update { false }
                }
        }
    }

    private fun createTCYStakePosition(address: String, vaultId: String) {
        viewModelScope.launch {
            tcyStakingService.getStakingDetails(
                address = address,
                vaultId = vaultId,
            ).catch { t ->
                Timber.e(t, "Failed to load staking positions TCY Stake")
                state.update {
                    it.copy(
                        staking = it.staking.copy(
                            positions = it.staking.positions.map { position ->
                                if (position.coin.id == Coins.ThorChain.TCY.id) {
                                    position.copy(isLoading = false)
                                } else {
                                    position
                                }
                            }
                        )
                    )
                }
            }.collect { position ->
                val stakedAmount = Chain.ThorChain.coinType.toValue(position.stakeAmount)
                val formattedAmount = "${stakedAmount.toPlainString()} TCY"

                // Create and return the UI model
                val stakePosition = StakePositionUiModel(
                    coin = position.coin,
                    stakeAssetHeader = "Staked TCY",
                    stakeAmount = formattedAmount,
                    apy = position.apr?.formatPercentage(),
                    canWithdraw = false, // TCY auto-distributes rewards
                    canStake = true,
                    canUnstake = true,
                    rewards = null,
                    nextReward = position.estimatedRewards?.toDouble()?.formatToString(),
                    nextPayout = position.nextPayoutDate?.formatDate()
                )

                updateExistingPosition(stakePosition)

                _totalValueTCYStake.update { position.stakeAmount }
                _isLoadingTotalAmount.update { false }
            }
        }
    }

    private fun createGenericStakePosition(
        address: String,
        vaultId: String,
        coinsToLoad: List<String>,
    ) {
        viewModelScope.launch {
            defaultStakingPositionService.getStakingDetails(address, vaultId)
                .catch { t ->
                    Timber.e(t, "Failed to load staking positions")
                    state.update {
                        it.copy(
                            staking = it.staking.copy(
                                positions = it.staking.positions.map { position ->
                                    if (position.coin.id == Coins.ThorChain.yRUNE.id
                                        || position.coin.id == Coins.ThorChain.yTCY.id
                                        || position.coin.id == Coins.ThorChain.sTCY.id) {
                                        position.copy(isLoading = false)
                                    } else {
                                        position
                                    }
                                }
                            )
                        )
                    }
                }
                .collect { defaultPositions ->
                    val positions = defaultPositions
                        .filter { it.coin.id in coinsToLoad }
                        .map { defaultPosition ->
                            val stakeAmount =
                                Chain.ThorChain.coinType.toValue(defaultPosition.stakeAmount)
                            val coin =
                                defaultPosition.coin
                            val supportsMint = coin.ticker.contains("yrune", ignoreCase = true) ||
                                    coin.ticker.contains("ytcy", ignoreCase = true)

                            val header = if (supportsMint) {
                                "Minted"
                            } else if(defaultPosition.coin.id.equals(Coins.ThorChain.sTCY.id, true)) {
                                "Compounded"
                            }
                            else {
                                "Staked"
                            }
                            val position = StakePositionUiModel(
                                coin = defaultPosition.coin,
                                stakeAssetHeader = "$header ${coin.ticker}",
                                stakeAmount = "${stakeAmount.toPlainString()} ${coin.ticker}",
                                apy = null,
                                supportsMint = supportsMint,
                                canWithdraw = false, // TCY auto-distributes rewards
                                canStake = true,
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
                            stakeElements = positions.map { position ->
                                StakeDefaultValues.StakingElement(
                                    coin = position.first.coin,
                                    amount = position.second
                                )
                            }
                        )
                    }

                    _isLoadingTotalAmount.update { false }
                }
        }
    }

    fun updateExistingPosition(stakePosition: StakePositionUiModel) {
        // Ensure only RUJI can have withdraw enabled
        val correctedPosition = if (stakePosition.coin.id != Coins.ThorChain.RUJI.id) {
            stakePosition.copy(canWithdraw = false)
        } else {
            stakePosition
        }

        state.update { currentState ->
            val existingPositions = currentState.staking.positions
            val positionExists = existingPositions.any {
                it.coin.id == correctedPosition.coin.id
            }

            if (positionExists) {
                currentState.copy(
                    staking = currentState.staking.copy(
                        positions = existingPositions.map {
                            if (it.coin.id == correctedPosition.coin.id) correctedPosition else it
                        }
                    )
                )
            } else {
                currentState.copy(
                    staking = currentState.staking.copy(
                        positions = existingPositions + correctedPosition
                    )
                )
            }
        }
    }

    fun onTabSelected(tab: DeFiTab) {
        state.update { currentState ->
            currentState.copy(selectedTab = tab.displayNameRes)
        }
    }

    // Dummy Loading, remove after real LP logic
    private fun loadLpPositions() {
        viewModelScope.launch {
            // Create two dummy loading positions immediately
            val loadingPositions = listOf(
                LpPositionUiModel(
                    titleLp = "Loading...",
                    totalPriceLp = "$0.00",
                    icon = R.drawable.ethereum,
                    apr = null,
                    position = "Loading position..."
                ),
                LpPositionUiModel(
                    titleLp = "Loading...",
                    totalPriceLp = "$0.00",
                    apr = null,
                    icon = R.drawable.bitcoin,
                    position = "Loading position..."
                )
            )

            // Set loading state with dummy positions showing
            state.update {
                it.copy(
                    lp = LpTabUiModel(
                        isLoading = true,
                        positions = loadingPositions
                    )
                )
            }

            // Simulate loading delay
            delay(2000)

            // Create actual LP positions
            val actualPositions = listOf(
                LpPositionUiModel(
                    titleLp = "ETH/USDC",
                    totalPriceLp = "$12,450.00",
                    icon = R.drawable.ethereum,
                    apr = "8.5%",
                    position = "0.5 ETH / 1,500 USDC"
                ),
                LpPositionUiModel(
                    titleLp = "BTC/USDT",
                    totalPriceLp = "$45,200.00",
                    icon = R.drawable.bitcoin,
                    apr = "12.3%",
                    position = "0.8 BTC / 35,000 USDT"
                ),
            )

            // Update state with actual positions and remove loading state
            state.update {
                it.copy(
                    lp = LpTabUiModel(
                        isLoading = false,
                        positions = actualPositions
                    )
                )
            }
        }
    }

    fun setPositionSelectionDialogVisibility(show: Boolean) {
        viewModelScope.launch {
            if (show) {
                state.update {
                    it.copy(
                        showPositionSelectionDialog = true,
                        tempSelectedPositions = it.selectedPositions
                    )
                }
            } else {
                state.update {
                    it.copy(
                        showPositionSelectionDialog = false,
                        tempSelectedPositions = it.selectedPositions
                    )
                }
            }
        }
    }

    fun onPositionSelectionChange(positionTitle: String, isSelected: Boolean) {
        viewModelScope.launch {
            state.update { currentState ->
                val updatedPositions = if (isSelected) {
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

            launch {
                withContext(Dispatchers.IO) {
                    defiPositionsRepository.saveSelectedPositions(vaultId, selectedPositions)
                }
            }

            state.update {
                it.copy(
                    showPositionSelectionDialog = false,
                    selectedPositions = selectedPositions
                )
            }

            loadedTabs.clear()

            bondedNodesRefreshTrigger.value++

            launch {
                loadBondedNodes()
            }

            launch {
                loadStakingPositions()
            }
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
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        type = DeFiNavActions.UNBOND.type,
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
            val tokenId = when (defiNavAction) {
                DeFiNavActions.STAKE_RUJI -> Coins.ThorChain.RUJI.id
                DeFiNavActions.UNSTAKE_RUJI -> Coins.ThorChain.RUJI.id
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

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    companion object {
        internal const val DEFAULT_ZERO_BALANCE = "$0.00"
        private const val RUJI_SYMBOL = "RUJI"
        private const val RUJI_REWARDS_SYMBOL = "USDC"

        private fun loadDefaultStakingPositions(): List<StakePositionUiModel> {
            val rujiCoin = Coins.ThorChain.RUJI
            val tcy = Coins.ThorChain.TCY
            val stcy = Coins.ThorChain.sTCY
            val ytcy = Coins.ThorChain.yTCY
            val yrune = Coins.ThorChain.yRUNE

            return listOf(
                StakePositionUiModel(
                    coin = rujiCoin,
                    stakeAssetHeader = "Staked ${rujiCoin.ticker}",
                    stakeAmount = rujiCoin.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                ),
                StakePositionUiModel(
                    coin = tcy,
                    stakeAssetHeader = "Staked ${tcy.ticker}",
                    stakeAmount = tcy.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                ),
                StakePositionUiModel(
                    coin = stcy,
                    stakeAssetHeader = "Compounded TCY",
                    stakeAmount = stcy.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                ),
                StakePositionUiModel(
                    coin = ytcy,
                    stakeAssetHeader = "Staked ${ytcy.ticker}",
                    stakeAmount = ytcy.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                ),
                StakePositionUiModel(
                    coin = yrune,
                    stakeAssetHeader = "Staked ${yrune.ticker}",
                    stakeAmount = yrune.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                ),
            )
        }
    }
}

data class StakeDefaultValues(
    val stakeElements: List<StakingElement> = emptyList()
) {
    data class StakingElement(
        val coin: Coin,
        val amount: BigInteger,
    )
}