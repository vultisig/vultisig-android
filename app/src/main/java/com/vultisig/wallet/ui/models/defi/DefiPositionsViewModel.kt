package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.DefiTab
import com.vultisig.wallet.ui.screens.v2.defi.defaultPositionsBondDialog
import com.vultisig.wallet.ui.screens.v2.defi.defaultPositionsStakingDialog
import com.vultisig.wallet.ui.screens.v2.defi.defaultSelectedPositionsDialog
import com.vultisig.wallet.ui.screens.v2.defi.emptyBondedTabUiModel
import com.vultisig.wallet.ui.screens.v2.defi.emptyStakingTabUiModel
import com.vultisig.wallet.ui.screens.v2.defi.formatAmount
import com.vultisig.wallet.ui.screens.v2.defi.formatDate
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
import com.vultisig.wallet.ui.screens.v2.defi.formatToString
import com.vultisig.wallet.ui.screens.v2.defi.hasBondPositions
import com.vultisig.wallet.ui.screens.v2.defi.hasStakingPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.screens.v2.defi.supportStakingDeFi
import com.vultisig.wallet.ui.screens.v2.defi.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
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

internal data class DefiPositionsUiModel(
    // tabs info
    val totalAmountPrice: String = DefiPositionsViewModel.DEFAULT_ZERO_BALANCE,
    val selectedTab: String = DefiTab.BONDED.displayName,
    val bonded: BondedTabUiModel = BondedTabUiModel(),
    val staking: StakingTabUiModel = StakingTabUiModel(),
    val lp: LpTabUiModel = LpTabUiModel(),

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
    val isLoading: Boolean = false,
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
    val stakeAssetHeader: String,
    val stakeAmount: String,
    val apy: String?,
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
    val status: BondNodeState,
    val apy: String,
    val bondedAmount: String,
    val nextAward: String,
    val nextChurn: String,
)

@HiltViewModel
internal class DefiPositionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val bondUseCase: ThorchainBondUseCase,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val rujiStakingService: RujiStakingService,
    private val tcyStakingService: TCYStakingService,
    private val balanceRepository: BalanceRepository,
    private val defiPositionsRepository: DefiPositionsRepository,
) : ViewModel() {

    private var vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val state = MutableStateFlow(DefiPositionsUiModel())
    
    private val bondedNodesRefreshTrigger = MutableStateFlow(0)

    private val loadedTabs = mutableSetOf<String>()

    init {
        loadSavedPositions()
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
        loadedTabs.add(DefiTab.BONDED.displayName)

        viewModelScope.launch {
            // Initial Loading State
            if (!state.value.selectedPositions.hasBondPositions()) {
                state.update {
                    it.copy(
                        bonded = emptyBondedTabUiModel(),
                        totalAmountPrice = if (it.selectedTab == DefiTab.BONDED.displayName) {
                            DEFAULT_ZERO_BALANCE
                        } else {
                            it.totalAmountPrice
                        }
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
                        val totalBondedRaw = calculateTotalBondedRaw(activeNodes)
                        val totalValue = calculateTotalValue(totalBondedRaw)

                        state.update {
                            it.copy(
                                totalAmountPrice = if (it.selectedTab == DefiTab.BONDED.displayName) {
                                    totalValue
                                } else {
                                    it.totalAmountPrice
                                },
                                bonded = BondedTabUiModel(
                                    isLoading = false,
                                    totalBondedAmount = totalBonded,
                                    nodes = nodeUiModels
                                )
                            )
                        }
                    }
            } catch (t: Throwable) {
                Timber.e(t)
                state.update {
                    it.copy(
                        bonded = it.bonded.copy(isLoading = false)
                    )
                }
            }
        }
    }

    private fun calculateTotalBonded(nodes: List<BondedNodePosition>): String {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return total.formatAmount(CoinType.THORCHAIN)
    }

    private fun calculateTotalBondedRaw(nodes: List<BondedNodePosition>): BigDecimal {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return Chain.ThorChain.coinType.toValue(total)
    }

    private suspend fun calculateTotalValue(totalRuneAmount: BigDecimal): String {
        return try {
            val currency = appCurrencyRepository.currency.first()
            val runePrice = tokenPriceRepository.getCachedPrice(
                tokenId = "RUNE-THORChain",
                appCurrency = currency
            ) ?: BigDecimal.ZERO

            Timber.d("RUNE price: $runePrice, amount: $totalRuneAmount")

            val totalValue = totalRuneAmount * runePrice
            val currencyFormat = appCurrencyRepository.getCurrencyFormat()

            currencyFormat.format(totalValue.toDouble())
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate total value")
            DEFAULT_ZERO_BALANCE
        }
    }

    private fun loadStakingPositions() {
        loadedTabs.add(DefiTab.STAKING.displayName)

        viewModelScope.launch {
            val selectedPositions = state.value.selectedPositions

            // Initial Loading Status
            if (!selectedPositions.hasStakingPositions()) {
                state.update {
                    it.copy(
                        staking = emptyStakingTabUiModel()
                    )
                }
                return@launch
            }
            val defaultLoadingPositions = loadDefaultStakingPositions().filter { coin ->
                selectedPositions.contains(coin.stakeAmount)
            }
            state.update {
                it.copy(
                    staking = StakingTabUiModel(
                        isLoading = true,
                        positions = defaultLoadingPositions,
                    )
                )
            }

            // Fetch all staking positions
            try {
                val vault = withContext(Dispatchers.IO) {
                    vaultRepository.get(vaultId)
                }
                val runeCoin = vault?.coins?.find { it.chain.id == Chain.ThorChain.id }

                if (runeCoin == null) {
                    Timber.e("Vault does not have RUNE coin")

                    state.update {
                        it.copy(
                            staking = it.staking.copy(isLoading = false)
                        )
                    }
                    return@launch
                }

                val address = runeCoin.address
                val coinsToLoad = supportStakingDeFi.filter { coin ->
                    selectedPositions.contains(coin.ticker)
                }

                // Decouple loading upcoming PR
                val positions = coinsToLoad.map { coin ->
                    async(Dispatchers.IO) {
                        try {
                            when {
                                coin.ticker.equals("ruji", ignoreCase = true) ->
                                    createRujiStakePosition(address)

                                coin.ticker.equals("tcy", ignoreCase = true) ->
                                    createTCYStakePosition(address)

                                else ->
                                    createGenericStakePosition(coin, address)
                            }
                        } catch (t: Throwable) {
                            Timber.e(t)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                // Update status finally
                state.update {
                    it.copy(
                        staking = StakingTabUiModel(
                            isLoading = false,
                            positions = positions
                        ),
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t, "Failed to load staking positions")
                state.update {
                    it.copy(
                        staking = it.staking.copy(isLoading = false)
                    )
                }
            }
        }
    }

    private suspend fun createRujiStakePosition(address: String): StakePositionUiModel? {
        return try {
            val details = rujiStakingService.getStakingDetails(address)

            val stakedAmount = Chain.ThorChain.coinType.toValue(details.stakeAmount)
            val formattedAmount =
                "${stakedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()} $RUJI_SYMBOL"

            val rewards = details.rewards?.let { rewardAmount ->
                val rewardValue = rewardAmount.setScale(2, RoundingMode.HALF_UP)
                "${rewardValue.toPlainString()} ${details.rewardsCoin?.ticker ?: RUJI_REWARDS_SYMBOL}"
            }

            StakePositionUiModel(
                stakeAssetHeader = "Staked RUJI",
                stakeAmount = formattedAmount,
                apy = details.apr?.formatPercentage(),
                canWithdraw = details.rewards?.let { it > BigDecimal.ZERO } == true,
                canStake = true,
                canUnstake = details.stakeAmount > BigInteger.ZERO,
                rewards = rewards,
                nextReward = null,
                nextPayout = null
            )
        } catch (t: Throwable) {
            Timber.e(t, "Failed to create RUJI stake position")
            null
        }
    }

    private suspend fun createTCYStakePosition(address: String): StakePositionUiModel? {
        return try {
            // Get staking details from service
            val result = tcyStakingService.getStakingDetails(
                address = address,
            )

            // Convert stake amount to readable format
            val stakedAmount = Chain.ThorChain.coinType.toValue(result.stakeAmount)
            val formattedAmount =
                "${stakedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()} TCY"

            // Create and return the UI model
            StakePositionUiModel(
                stakeAssetHeader = "Staked TCY",
                stakeAmount = formattedAmount,
                apy = result.apr?.formatPercentage(),
                canWithdraw = false, // TCY auto-distributes rewards
                canStake = true,
                canUnstake = true,
                rewards = null,
                nextReward = result.estimatedRewards?.toDouble()?.formatToString(),
                nextPayout = result.nextPayoutDate?.formatDate()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create TCY stake position")
            null
        }
    }

    private suspend fun createGenericStakePosition(
        coin: Coin,
        address: String
    ): StakePositionUiModel? {
        return try {
            val addresses = listOf(address)
            val coins = listOf(coin)

            val balance =
                balanceRepository.getCachedTokenBalances(addresses, coins).firstOrNull()
                    ?.tokenBalance
                    ?.tokenValue
                    ?.value
                    ?: BigInteger.ZERO

            val supportsMint = coin.ticker.contains("yrune", ignoreCase = true) ||
                    coin.ticker.contains("ytcy", ignoreCase = true)

            return StakePositionUiModel(
                stakeAssetHeader = "Staked ${coin.ticker}",
                stakeAmount = balance.formatAmount(CoinType.THORCHAIN, coin.ticker),
                apy = null,
                supportsMint = supportsMint,
                canWithdraw = false, // TCY auto-distributes rewards
                canStake = true,
                canUnstake = true,
                rewards = null,
                nextReward = null,
                nextPayout = null,
            )
        } catch (t: Throwable) {
            Timber.e(t, "Failed to create stake position for ${coin.ticker}")
            null
        }
    }

    fun onTabSelected(tab: String) {
        state.update { currentState ->
            currentState.copy(selectedTab = tab)
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

            launch {
                loadBondedNodes()
            }

            launch {
                loadStakingPositions()
            }
        }
    }

    fun onClickBond(nodeAddress: String) {
        // TODO: Implement new navigation screen
    }

    fun onClickUnBond(nodeAddress: String) {
        // TODO: Implement new navigation screen
    }

    fun bondToNode() {
        // TODO: Implement new navigation screen
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
                    stakeAssetHeader = "Staked ${tcy.ticker}",
                    stakeAmount = tcy.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                ), StakePositionUiModel(
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
                StakePositionUiModel(
                    stakeAssetHeader = "Staked ${stcy.ticker}",
                    stakeAmount = stcy.ticker,
                    apy = null,
                    canWithdraw = false,
                    canStake = true,
                    canUnstake = false,
                    rewards = null,
                    nextReward = null,
                    nextPayout = null
                )
            )
        }
    }
}