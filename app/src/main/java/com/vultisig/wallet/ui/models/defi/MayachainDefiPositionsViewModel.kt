package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.blockchain.maya.MayaCacaoStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.DefiPositionsRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.MayachainBondUseCase
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.formatAddress
import com.vultisig.wallet.ui.screens.v2.defi.formatAmount
import com.vultisig.wallet.ui.screens.v2.defi.formatDate
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
import com.vultisig.wallet.ui.screens.v2.defi.hasMayaStakingPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState.Companion.fromApiStatus
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType

private val MAYA_BOND_POSITIONS_DIALOG: List<PositionUiModelDialog>
    get() =
        listOf(
            PositionUiModelDialog(
                logo = getCoinLogo(Coins.MayaChain.CACAO.logo),
                ticker = Coins.MayaChain.CACAO.ticker,
                isSelected = true,
            )
        )

private val MAYA_STAKE_POSITIONS_DIALOG: List<PositionUiModelDialog>
    get() =
        listOf(
            PositionUiModelDialog(
                logo = getCoinLogo(Coins.MayaChain.CACAO.logo),
                ticker = Coins.MayaChain.CACAO.ticker,
                isSelected = true,
            )
        )

private val MAYA_DEFAULT_SELECTED_POSITIONS = listOf(Coins.MayaChain.CACAO.ticker)

internal data class MayachainDefiPositionsUiModel(
    val totalAmountPrice: String = DEFAULT_ZERO_BALANCE,
    val bonded: BondedTabUiModel = BondedTabUiModel(totalBondedAmount = "0 CACAO"),
    val staking: StakingTabUiModel = StakingTabUiModel(),
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
    private val mayaCacaoStakingService: MayaCacaoStakingService,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val defiPositionsRepository: DefiPositionsRepository,
) : ViewModel() {

    private lateinit var vaultId: String

    val state = MutableStateFlow(MayachainDefiPositionsUiModel())

    private val bondedNodesRefreshTrigger = MutableStateFlow(0)

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        loadBalanceVisibility()
        loadSavedPositions()
        loadLpPositionsForDialog()
    }

    private fun loadBalanceVisibility() {
        viewModelScope.launch {
            val isVisible =
                withContext(Dispatchers.IO) { balanceVisibilityRepository.getVisibility(vaultId) }
            state.update { it.copy(isBalanceVisible = isVisible) }
        }
    }

    private fun loadSavedPositions() {
        viewModelScope.launch {
            defiPositionsRepository.getSelectedPositions(vaultId).collect { saved ->
                val positions =
                    if (saved.isNotEmpty()) {
                        saved.toList()
                    } else {
                        MAYA_DEFAULT_SELECTED_POSITIONS
                    }
                state.update {
                    it.copy(selectedPositions = positions, tempSelectedPositions = positions)
                }

                launch { loadBondedNodes() }
                launch { loadStakingPosition() }
            }
        }
    }

    private fun loadLpPositionsForDialog() {
        viewModelScope.launch {
            try {
                val pools =
                    withContext(Dispatchers.IO) { mayachainBondRepository.getMayaNodePools() }
                val lpPositions = pools.map { pool -> pool.toPositionDialogModel() }
                state.update { it.copy(lpPositionsDialog = lpPositions) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Maya LP positions for dialog")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadBondedNodes() {
        viewModelScope.launch {
            state.update { it.copy(bonded = it.bonded.copy(isLoading = true)) }

            try {
                val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
                val cacaoCoin =
                    vault?.coins?.find {
                        it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                    }

                if (cacaoCoin == null) {
                    Timber.e("Vault does not have CACAO coin")
                    state.update { it.copy(bonded = it.bonded.copy(isLoading = false)) }
                    return@launch
                }

                bondedNodesRefreshTrigger
                    .flatMapLatest { bondUseCase.getActiveNodes(vaultId, cacaoCoin.address) }
                    .catch { t ->
                        Timber.e(t)
                        state.update { it.copy(bonded = it.bonded.copy(isLoading = false)) }
                    }
                    .collect { activeNodes ->
                        val cacaoSymbol = CoinType.THORCHAIN.symbol

                        val nodeUiModels =
                            activeNodes.map { node ->
                                BondedNodeUiModel(
                                    address = node.node.address.formatAddress(),
                                    fullAddress = node.node.address,
                                    status = node.node.state.fromApiStatus(),
                                    apy = node.apy.formatPercentage(),
                                    bondedAmount =
                                        node.amount.formatAmount(CoinType.THORCHAIN, cacaoSymbol),
                                    nextAward = formatCacaoReward(node.nextReward),
                                    nextChurn = node.nextChurn.formatDate(),
                                )
                            }

                        val totalBondedRaw =
                            activeNodes.fold(BigInteger.ZERO) { acc, node -> acc + node.amount }
                        val totalBonded =
                            totalBondedRaw.formatAmount(CoinType.THORCHAIN, cacaoSymbol)

                        val bondedPrice = calculateBondedFiatPrice(totalBondedRaw)

                        state.update {
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

                        updateTotalFiatValue(totalBondedRaw)
                    }
            } catch (t: Throwable) {
                Timber.e(t)
                state.update {
                    it.copy(
                        isTotalAmountLoading = false,
                        bonded = it.bonded.copy(isLoading = false),
                    )
                }
            }
        }
    }

    private fun loadStakingPosition() {
        viewModelScope.launch {
            val selectedPositions = state.value.selectedPositions
            if (!selectedPositions.hasMayaStakingPositions()) {
                state.update { it.copy(staking = StakingTabUiModel(positions = emptyList())) }
                return@launch
            }

            val loadingPosition =
                StakePositionUiModel(
                    coin = Coins.MayaChain.CACAO,
                    stakeAssetHeader = "Cacao Pool",
                    stakedAmountDisplay = "0 CACAO",
                    apy = null,
                    isLoading = true,
                    canStake = false,
                    canUnstake = false,
                )
            state.update {
                it.copy(staking = StakingTabUiModel(positions = listOf(loadingPosition)))
            }

            try {
                val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
                val cacaoCoin =
                    vault?.coins?.find {
                        it.ticker == Coins.MayaChain.CACAO.ticker && it.chain == Chain.MayaChain
                    }
                        ?: run {
                            Timber.e("Vault does not have CACAO coin")
                            state.update {
                                it.copy(staking = StakingTabUiModel(positions = emptyList()))
                            }
                            return@launch
                        }

                mayaCacaoStakingService
                    .getStakingDetails(cacaoCoin.address)
                    .catch { t ->
                        Timber.e(t, "Failed to load CACAO staking details")
                        state.update {
                            it.copy(
                                staking =
                                    StakingTabUiModel(
                                        positions = listOf(loadingPosition.copy(isLoading = false))
                                    )
                            )
                        }
                    }
                    .collect { details ->
                        val stakeAmount = CoinType.THORCHAIN.toValue(details.stakeAmount)
                        val stakedFiat = calculateStakingFiatPrice(stakeAmount)
                        val position =
                            StakePositionUiModel(
                                coin = Coins.MayaChain.CACAO,
                                stakeAssetHeader = "Cacao Pool",
                                stakeAmount = stakeAmount,
                                stakedAmountDisplay =
                                    "${stakeAmount.setScale(4, RoundingMode.DOWN).toPlainString()} CACAO",
                                stakedFiatDisplay = stakedFiat,
                                apy = details.apr?.formatPercentage(),
                                isLoading = false,
                                canStake = true,
                                canUnstake = details.canUnstake,
                            )
                        state.update {
                            it.copy(staking = StakingTabUiModel(positions = listOf(position)))
                        }
                    }
            } catch (t: Throwable) {
                Timber.e(t, "Failed to load CACAO staking position")
                state.update {
                    it.copy(
                        staking =
                            StakingTabUiModel(
                                positions = listOf(loadingPosition.copy(isLoading = false))
                            )
                    )
                }
            }
        }
    }

    private fun updateTotalFiatValue(bondedRaw: BigInteger) {
        viewModelScope.launch {
            try {
                val currency = appCurrencyRepository.currency.first()
                val totalInCacao = CoinType.THORCHAIN.toValue(bondedRaw)
                val fiatValue = createFiatValue(totalInCacao, Coins.MayaChain.CACAO, currency)
                val currencyFormat =
                    withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }
                state.update {
                    it.copy(
                        totalAmountPrice = currencyFormat.format(fiatValue.value),
                        isTotalAmountLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate Maya total fiat value")
                state.update { it.copy(isTotalAmountLoading = false) }
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
            Timber.e(e, "Failed to calculate Maya staking fiat price")
            ""
        }
    }

    private suspend fun calculateBondedFiatPrice(totalBondedRaw: BigInteger): String {
        return try {
            val totalInCacao = CoinType.THORCHAIN.toValue(totalBondedRaw)
            val currency = appCurrencyRepository.currency.first()
            val fiatValue = createFiatValue(totalInCacao, Coins.MayaChain.CACAO, currency)
            val currencyFormat =
                withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }
            currencyFormat.format(fiatValue.value)
        } catch (e: Exception) {
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
            Timber.e(t)
            FiatValue(value = BigDecimal.ZERO, currency = currency.ticker)
        }
    }

    fun onTabSelected(tab: DeFiTab) {
        state.update { it.copy(selectedTab = tab.displayNameRes) }
    }

    fun setPositionSelectionDialogVisibility(visible: Boolean) {
        state.update {
            it.copy(
                showPositionSelectionDialog = visible,
                tempSelectedPositions = it.selectedPositions,
            )
        }
    }

    fun onPositionSelectionChange(ticker: String, selected: Boolean) {
        state.update { current ->
            val updated =
                if (selected) current.tempSelectedPositions + ticker
                else current.tempSelectedPositions - ticker
            current.copy(tempSelectedPositions = updated)
        }
    }

    fun onPositionSelectionDone() {
        viewModelScope.launch {
            val selectedPositions = state.value.tempSelectedPositions
            defiPositionsRepository.saveSelectedPositions(vaultId, selectedPositions)
            state.update {
                it.copy(showPositionSelectionDialog = false, selectedPositions = selectedPositions)
            }
            launch { loadBondedNodes() }
            launch { loadStakingPosition() }
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
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = Chain.MayaChain.id,
                    depositType = DeFiNavActions.BOND.type,
                )
            )
        }
    }

    fun onBackClick() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }
}

private fun MayaNodePool.toPositionDialogModel(): PositionUiModelDialog {
    val assetTicker = asset.substringAfter(".")
    return PositionUiModelDialog(
        logo = "https://static.vultisig.com/tokens/maya/${assetTicker.lowercase()}.png",
        ticker = "CACAO/$assetTicker",
        isSelected = false,
    )
}

private fun formatCacaoReward(reward: Double): String {
    val rewardBase = BigDecimal.valueOf(reward).setScale(0, RoundingMode.DOWN).toBigInteger()
    val cacaoAmount = CoinType.THORCHAIN.toValue(rewardBase).setScale(4, RoundingMode.DOWN)
    return "${cacaoAmount.toPlainString()} ${CoinType.THORCHAIN.symbol}"
}
