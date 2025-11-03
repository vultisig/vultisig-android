package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.BONDED_TAB
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_TAB
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState.Companion.fromApiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

internal data class DefiPositionsUiModel(
    val totalAmountPrice: String = "$0.00",
    val selectedTab: String = BONDED_TAB,
    val bonded: BondedTabUiModel = BondedTabUiModel(),
    val staking: StakingTabUiModel = StakingTabUiModel()
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

internal data class StakePositionUiModel(
    val stakeAssetHeader: String,
    val stakeAmount: String,
    val apy: String,
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
) : ViewModel() {

    private var vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val state = MutableStateFlow(DefiPositionsUiModel())
    
    private val loadedTabs = mutableSetOf<String>()

    init {
        loadBondedNodes()
    }

    private fun loadBondedNodes() {
        loadedTabs.add(BONDED_TAB)

        viewModelScope.launch {
            state.update {
                it.copy(
                    bonded = it.bonded.copy(isLoading = true)
                )
            }

            try {
                val vault = vaultRepository.get(vaultId)
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


                val activeNodes = withContext(Dispatchers.IO) {
                    val address = runeCoin.address
                    bondUseCase.getActiveNodes(address)
                }

                val nodeUiModels = activeNodes.map { it.toUiModel() }
                val totalBonded = calculateTotalBonded(activeNodes)
                val totalBondedRaw = calculateTotalBondedRaw(activeNodes)
                val totalValue = calculateTotalValue(totalBondedRaw)

                state.update {
                    it.copy(
                        totalAmountPrice = if (it.selectedTab == BONDED_TAB) totalValue else it.totalAmountPrice,
                        bonded = BondedTabUiModel(
                            isLoading = false,
                            totalBondedAmount = totalBonded,
                            nodes = nodeUiModels
                        )
                    )
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

    private fun ActiveBondedNode.toUiModel(): BondedNodeUiModel {
        return BondedNodeUiModel(
            address = formatAddress(node.address),
            status = node.state.fromApiStatus(),
            apy = formatApy(apy),
            bondedAmount = formatRuneAmount(amount),
            nextAward = formatRuneReward(nextReward),
            nextChurn = formatChurnDate(nextChurn)
        )
    }

    private fun formatAddress(address: String): String {
        return if (address.length > 13) {
            "${address.take(9)}...${address.takeLast(3)}"
        } else {
            address
        }
    }

    private fun calculateTotalBonded(nodes: List<ActiveBondedNode>): String {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return formatRuneAmount(total)
    }

    private fun calculateTotalBondedRaw(nodes: List<ActiveBondedNode>): BigDecimal {
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
            "$0.00"
        }
    }

    private fun formatRuneAmount(amount: BigInteger): String {
        val runeAmount = Chain.ThorChain.coinType.toValue(amount)
        val rounded = runeAmount.setScale(2, RoundingMode.HALF_UP)
        return "${rounded.toPlainString()} ${Chain.ThorChain.coinType.symbol}"
    }

    private fun formatRuneReward(reward: Double): String {
        val rewardBase = BigDecimal.valueOf(reward).setScale(0, RoundingMode.HALF_UP).toBigInteger()
        val runeAmount = Chain.ThorChain.coinType.toValue(rewardBase).setScale(2, RoundingMode.HALF_UP)
        return "${runeAmount.toPlainString()} ${Chain.ThorChain.coinType.symbol}"
    }

    private fun formatApy(apy: Double): String {
        return "%.2f%%".format(Locale.US, apy * 100)
    }

    private fun formatChurnDate(date: Date?): String {
        return date?.let {
            val formatter = SimpleDateFormat("MMM dd, yy", Locale.US)
            formatter.format(it)
        } ?: "N/A"
    }

    private fun loadStakingPositions() {
        viewModelScope.launch {
            state.update {
                it.copy(
                    staking = it.staking.copy(isLoading = true)
                )
            }

            try {
                val vault = vaultRepository.get(vaultId)
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

                val positions = mutableListOf<StakePositionUiModel>()
                val address = runeCoin.address
                
                // Load RUJI staking details
                try {
                    val rujiDetails = withContext(Dispatchers.IO) {
                        rujiStakingService.getStakingDetails(address)
                    }
                    
                    if (rujiDetails.stakeAmount > BigInteger.ZERO) {
                        val rujiPosition = createRujiStakePosition(rujiDetails)
                        positions.add(rujiPosition)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load RUJI staking details")
                }
                
                val totalStakingValue = calculateTotalStakingValue(positions)
                
                state.update {
                    it.copy(
                        staking = StakingTabUiModel(
                            isLoading = false,
                            positions = positions
                        ),
                        totalAmountPrice = if (it.selectedTab == STAKING_TAB) {
                            totalStakingValue
                        } else {
                            it.totalAmountPrice
                        }
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
    
    private fun createRujiStakePosition(
        details: com.vultisig.wallet.data.blockchain.model.StakingDetails
    ): StakePositionUiModel {
        val stakedAmount = Chain.ThorChain.coinType.toValue(details.stakeAmount)
        val formattedAmount = "${stakedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()} RUJI"
        
        val rewards = details.rewards?.let { rewardAmount ->
            val rewardValue = rewardAmount.setScale(2, RoundingMode.HALF_UP)
            "${rewardValue.toPlainString()} ${details.rewardsCoin?.ticker ?: "USDC"}"
        }
        
        return StakePositionUiModel(
            stakeAssetHeader = "Staked RUJI",
            stakeAmount = formattedAmount,
            apy = formatApr(details.apr ?: 0.0),
            canWithdraw = rewards != null && details.rewards!! > BigDecimal.ZERO,
            canStake = true,
            canUnstake = true,
            rewards = rewards,
            nextReward = null,
            nextPayout = null
        )
    }
    
    private fun formatApr(apr: Double): String {
        return "%.2f%%".format(Locale.US, apr * 100)
    }
    
    private suspend fun calculateTotalStakingValue(positions: List<StakePositionUiModel>): String {
        // TODO: Calculate total value based on token prices
        // For now, return a placeholder
        return "$0.00"
    }

    fun onTabSelected(tab: String) {
        state.update { currentState ->
            currentState.copy(selectedTab = tab)
        }

        // Only load data if it hasn't been loaded yet
        if (!loadedTabs.contains(tab)) {
            when (tab) {
                STAKING_TAB -> {
                    loadStakingPositions()
                    loadedTabs.add(STAKING_TAB)
                }
                BONDED_TAB -> {
                    loadBondedNodes()
                    loadedTabs.add(BONDED_TAB)
                }
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
}