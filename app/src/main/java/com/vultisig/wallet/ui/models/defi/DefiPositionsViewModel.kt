package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.DefiTab
import com.vultisig.wallet.ui.screens.v2.defi.formatAmount
import com.vultisig.wallet.ui.screens.v2.defi.formatDate
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
import com.vultisig.wallet.ui.screens.v2.defi.formatToString
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.supportDeFiCoins
import com.vultisig.wallet.ui.screens.v2.defi.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val totalAmountPrice: String = "$0.00",
    val selectedTab: String = DefiTab.BONDED.displayName,
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
) : ViewModel() {

    private var vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val state = MutableStateFlow(DefiPositionsUiModel())

    private val loadedTabs = mutableSetOf<String>()

    init {
        loadBondedNodes()
    }

    private fun loadBondedNodes() {
        loadedTabs.add(DefiTab.BONDED.displayName)

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
                        totalAmountPrice = if (it.selectedTab == DefiTab.BONDED.displayName) totalValue else it.totalAmountPrice,
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

    private fun calculateTotalBonded(nodes: List<ActiveBondedNode>): String {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return total.formatAmount(CoinType.THORCHAIN)
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

    private fun loadStakingPositions() {
        viewModelScope.launch {
            state.update {
                it.copy(
                    staking = StakingTabUiModel(
                        isLoading = true,
                        positions = listOf(createLoadingRujiPosition(), createTCYLoadingPosition())
                    )
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

                val address = runeCoin.address

                // Decouple loading upcoming PR
                val positions = supportDeFiCoins.map { coin ->
                    async(Dispatchers.IO) {
                        when {
                            coin.ticker.equals("ruji", ignoreCase = true) ->
                                createRujiStakePosition(address)
                            coin.ticker.equals("tcy", ignoreCase = true) ->
                                createTCYStakePosition(address)
                            else ->
                                createGenericStakePosition(coin, address)
                        }
                    }
                }.awaitAll().filterNotNull()

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
        val details = withContext(Dispatchers.IO) {
            rujiStakingService.getStakingDetails(address)
        }

        val stakedAmount = Chain.ThorChain.coinType.toValue(details.stakeAmount)
        val formattedAmount =
            "${stakedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()} $RUJI_SYMBOL"

        val rewards = details.rewards?.let { rewardAmount ->
            val rewardValue = rewardAmount.setScale(2, RoundingMode.HALF_UP)
            "${rewardValue.toPlainString()} ${details.rewardsCoin?.ticker ?: RUJI_REWARDS_SYMBOL}"
        }

        return StakePositionUiModel(
            stakeAssetHeader = "Staked RUJI",
            stakeAmount = formattedAmount,
            apy = details.apr?.formatPercentage(),
            canWithdraw = rewards != null && details.rewards!! > BigDecimal.ZERO,
            canStake = true,
            canUnstake = details.stakeAmount > BigInteger.ZERO,
            rewards = rewards,
            nextReward = null,
            nextPayout = null
        )
    }

    private suspend fun createTCYStakePosition(address: String): StakePositionUiModel? {
        return try {
            // Get staking details from service
            val result = tcyStakingService.getStakingDetails(
                address = address,
            )
            
            // Convert stake amount to readable format
            val stakedAmount = Chain.ThorChain.coinType.toValue(result.stakeAmount)
            val formattedAmount = "${stakedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()} TCY"
            
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

    private suspend fun createGenericStakePosition(coin: Coin, address: String): StakePositionUiModel? {
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
    }

    fun onTabSelected(tab: String) {
        state.update { currentState ->
            currentState.copy(selectedTab = tab)
        }

        if (!loadedTabs.contains(tab)) {
            when (tab) {
                DefiTab.STAKING.displayName -> {
                    loadStakingPositions()
                    loadedTabs.add(DefiTab.STAKING.displayName)
                }

                DefiTab.BONDED.displayName -> {
                    loadBondedNodes()
                    loadedTabs.add(DefiTab.BONDED.displayName)
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

    companion object {
        // Ruji Constants
        private const val RUJI_SYMBOL = "RUJI"
        private const val RUJI_REWARDS_SYMBOL = "USDC"

        private fun createLoadingRujiPosition() = StakePositionUiModel(
            stakeAssetHeader = "Staked RUJI",
            stakeAmount = "0 RUJI",
            apy = null, // Does not support APY for now
            canWithdraw = false,
            canStake = true,
            canUnstake = false,
            rewards = null,
            nextReward = null,
            nextPayout = null
        )

        private fun createTCYLoadingPosition() = StakePositionUiModel(
            stakeAssetHeader = "Staked TCY",
            stakeAmount = "0 TCY",
            apy = null,
            canWithdraw = false,
            canStake = true,
            canUnstake = false,
            rewards = null,
            nextReward = null,
            nextPayout = null
        )
    }
}