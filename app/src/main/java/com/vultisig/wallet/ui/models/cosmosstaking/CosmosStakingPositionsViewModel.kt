package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorReward
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosStakingPositionsUiState(
    val ticker: String = "",
    val delegations: List<CosmosDelegation> = emptyList(),
    val unbondings: List<CosmosUnbondingDelegation> = emptyList(),
    val rewards: List<CosmosDelegatorReward> = emptyList(),
    val totalStakedDisplay: String = "—",
    val totalRewardsDisplay: String = "—",
    val hasAnyRewards: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * View-model for the LUNA / LUNC active-delegations view. Loads delegations, unbonding delegations,
 * and delegator rewards in parallel via [CosmosStakingService], computes per-row + total display
 * strings, and exposes navigation callbacks to the per-validator action screens
 * ([Route.CosmosStakingUndelegate] / [Route.CosmosStakingRedelegate]) and the batched-claim screen
 * ([Route.CosmosStakingWithdrawRewards]).
 *
 * Mirrors iOS `CosmosStakeDefiViewModel.swift` (vultisig-ios PR #4432). Polished UI bits (APY
 * column, Keybase avatar resolution, fiat values, churned-out badge) are deferred — those need a
 * spot-price feed and the Keybase API, both explicit out-of-scope in the cross-platform plan.
 */
@HiltViewModel
internal class CosmosStakingPositionsViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route: Route.CosmosStakingPositions = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(CosmosStakingPositionsUiState())
    val state: StateFlow<CosmosStakingPositionsUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        loadCoin()
        refresh()
    }

    fun refresh() {
        val chain =
            Chain.entries.firstOrNull { it.raw.equals(route.chainId, ignoreCase = true) }
                ?: return setError("Unsupported chain: ${route.chainId}")

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load staking positions")
                _state.update { it.copy(isLoading = false) }
                setError("Failed to load staking positions")
            }
        ) {
            val coin = coin ?: return@safeLaunch
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val (delegations, unbondings, rewards) =
                withContext(Dispatchers.IO) {
                    listOf(
                            async { cosmosStakingService.fetchDelegations(chain, coin.address) },
                            async {
                                cosmosStakingService.fetchUnbondingDelegations(chain, coin.address)
                            },
                            async {
                                cosmosStakingService.fetchDelegatorRewards(chain, coin.address)
                            },
                        )
                        .awaitAll()
                }

            @Suppress("UNCHECKED_CAST") val delegationsList = delegations as List<CosmosDelegation>

            @Suppress("UNCHECKED_CAST")
            val unbondingsList = unbondings as List<CosmosUnbondingDelegation>
            val rewardsResult =
                rewards as com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorRewards

            val entry = CosmosStakingConfig.entryFor(chain)
            val bondDenom = entry.bondDenom

            val totalStakedBaseUnits =
                delegationsList
                    .filter { it.balance.denom == bondDenom }
                    .map { BigInteger(it.balance.amount) }
                    .fold(BigInteger.ZERO) { acc, v -> acc + v }

            val totalRewardsBondDenom =
                rewardsResult.total
                    .firstOrNull { it.denom == bondDenom }
                    ?.amount
                    ?.toBigDecimalOrNull() ?: BigDecimal.ZERO

            _state.update {
                it.copy(
                    delegations = delegationsList,
                    unbondings = unbondingsList,
                    rewards = rewardsResult.rewards,
                    totalStakedDisplay = formatBaseUnits(totalStakedBaseUnits, coin.decimal),
                    totalRewardsDisplay =
                        formatDecimalBaseUnits(totalRewardsBondDenom, coin.decimal),
                    hasAnyRewards = rewardsResult.rewards.any { it.reward.isNotEmpty() },
                    isLoading = false,
                )
            }
        }
    }

    fun stakeMore() {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to navigate to delegate flow") }
        ) {
            navigator.route(
                Route.CosmosStakingDelegate(vaultId = route.vaultId, chainId = route.chainId)
            )
        }
    }

    fun unstake(validatorAddress: String) {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to navigate to undelegate flow") }
        ) {
            navigator.route(
                Route.CosmosStakingUndelegate(
                    vaultId = route.vaultId,
                    chainId = route.chainId,
                    validatorAddress = validatorAddress,
                )
            )
        }
    }

    fun move(validatorSrcAddress: String) {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to navigate to redelegate flow") }
        ) {
            navigator.route(
                Route.CosmosStakingRedelegate(
                    vaultId = route.vaultId,
                    chainId = route.chainId,
                    validatorSrcAddress = validatorSrcAddress,
                )
            )
        }
    }

    fun claimAll() {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to navigate to claim flow") }
        ) {
            navigator.route(
                Route.CosmosStakingWithdrawRewards(vaultId = route.vaultId, chainId = route.chainId)
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun loadCoin() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load coin for cosmos positions view")
                setError("Failed to load wallet")
            }
        ) {
            val chain =
                Chain.entries.firstOrNull { it.raw.equals(route.chainId, ignoreCase = true) }
                    ?: return@safeLaunch setError("Unsupported chain: ${route.chainId}")
            if (!CosmosStakingConfig.isStakingSupported(chain)) {
                return@safeLaunch setError("Staking is not supported on ${chain.raw}")
            }
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")
            val nativeCoin =
                vault.coins.firstOrNull { it.chain == chain && it.isNativeToken }
                    ?: return@safeLaunch setError(
                        "Native ${chain.raw} coin not loaded for this vault"
                    )
            coin = nativeCoin
            _state.update { it.copy(ticker = nativeCoin.ticker) }
        }
    }

    private fun formatBaseUnits(value: BigInteger, decimals: Int): String =
        BigDecimal(value).movePointLeft(decimals).stripTrailingZeros().toPlainString()

    private fun formatDecimalBaseUnits(value: BigDecimal, decimals: Int): String =
        value
            .movePointLeft(decimals)
            .setScale(6, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, isLoading = false) }
    }
}

private fun SavedStateHandle.toRoute(): Route.CosmosStakingPositions =
    Route.CosmosStakingPositions(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        chainId = checkNotNull(get<String>("chainId")) { "chainId is required" },
    )
