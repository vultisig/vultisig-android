package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorRewards
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakePositionRow
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
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
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosStakingPositionsUiState(
    val ticker: String = "",
    val coinLogo: String = "",
    val positions: List<CosmosStakePositionRow> = emptyList(),
    val pendingUnbondings: List<CosmosUnbondingDelegation> = emptyList(),
    val totalStaked: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * View-model for the LUNA / LUNC active-delegations view. Fans out four LCD reads (delegations,
 * unbondings, rewards, bonded validators) and folds them into per-validator
 * [CosmosStakePositionRow] objects keyed by valoper address. Per-call failures degrade individually
 * — a failed rewards fetch renders the position with zero pending rewards rather than dropping the
 * row, matching iOS / THOR / Maya behavior under transient LCD outages.
 *
 * Port of iOS `CosmosStakeDefiViewModel.swift` (vultisig-ios PR #4432). Renamed for Android
 * navigation alignment (the route is `Route.CosmosStakingPositions`); the logic is verbatim port.
 *
 * **APY is deferred** — iOS computes a per-validator APY from a 4-call LCD fan-out (mint inflation,
 * staking pool, bank supply, distribution params). The `CosmosStakingAPYResolver` is not yet
 * ported; rows expose `apyPercent = null` and the view hides the APY row when null (matching iOS
 * behavior under chain-APY fan-out failure).
 *
 * **Validator status logic** matches iOS verbatim:
 * - `/cosmos/staking/v1beta1/validators?status=BOND_STATUS_BONDED` only returns active set members.
 * - A delegated validator missing from the response is either jailed or unbonded — either way,
 *   "Churned Out" is the right user-facing label and Unstake is the only sensible action.
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

            // Parallel fan-out — same shape as iOS `refresh(address:decimals:)`.
            val (delegations, unbondings, rewards, validators) =
                withContext(Dispatchers.IO) {
                    val delegationsDeferred = async {
                        runCatching { cosmosStakingService.fetchDelegations(chain, coin.address) }
                            .also { it.exceptionOrNull()?.let { Timber.w(it, "delegations") } }
                            .getOrDefault(emptyList())
                    }
                    val unbondingsDeferred = async {
                        runCatching {
                                cosmosStakingService.fetchUnbondingDelegations(chain, coin.address)
                            }
                            .also { it.exceptionOrNull()?.let { Timber.w(it, "unbondings") } }
                            .getOrDefault(emptyList())
                    }
                    val rewardsDeferred = async {
                        runCatching {
                                cosmosStakingService.fetchDelegatorRewards(chain, coin.address)
                            }
                            .also { it.exceptionOrNull()?.let { Timber.w(it, "rewards") } }
                            .getOrDefault(CosmosDelegatorRewards(emptyList(), emptyList()))
                    }
                    val validatorsDeferred = async {
                        runCatching { cosmosStakingService.fetchValidators(chain) }
                            .also { it.exceptionOrNull()?.let { Timber.w(it, "validators") } }
                            .getOrDefault(emptyList())
                    }
                    Quad(
                        delegationsDeferred.await(),
                        unbondingsDeferred.await(),
                        rewardsDeferred.await(),
                        validatorsDeferred.await(),
                    )
                }

            val entry = CosmosStakingConfig.entryFor(chain)
            val bondDenom = entry.bondDenom

            // Filter rewards to the bond denom — Terra Classic LCDs occasionally return reward
            // entries in non-staking denoms (legacy stability-tax pool); aggregating them as if
            // they were `uluna` would overstate the user's claimable native rewards. iOS verbatim.
            val rewardsByValidator: Map<String, BigDecimal> =
                rewards.rewards.associate { reward ->
                    reward.validatorAddress to
                        reward.reward
                            .filter { it.denom == bondDenom }
                            .mapNotNull { it.amount.toBigDecimalOrNull() }
                            .fold(BigDecimal.ZERO) { acc, v -> acc + v }
                }

            val validatorsByAddress = validators.associateBy { it.operatorAddress }
            val unbondingsByValidator = unbondings.groupBy { it.validatorAddress }

            val now = Instant.now()
            val decimals = coin.decimal

            val positions =
                delegations.map { delegation ->
                    buildRow(
                        delegation = delegation,
                        validator = validatorsByAddress[delegation.validatorAddress],
                        pendingRewardRaw =
                            rewardsByValidator[delegation.validatorAddress] ?: BigDecimal.ZERO,
                        unbondings = unbondingsByValidator[delegation.validatorAddress].orEmpty(),
                        decimals = decimals,
                        now = now,
                    )
                }

            val totalStaked = positions.fold(BigDecimal.ZERO) { acc, p -> acc + p.stakedAmount }

            _state.update {
                it.copy(
                    positions = positions,
                    pendingUnbondings = unbondings,
                    totalStaked = totalStaked,
                    isLoading = false,
                )
            }
        }
    }

    fun stakeMore() {
        navigateSafely {
            navigator.route(
                Route.CosmosStakingDelegate(vaultId = route.vaultId, chainId = route.chainId)
            )
        }
    }

    fun unstake(position: CosmosStakePositionRow) {
        if (!canActOn(position)) return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingUndelegate(
                    vaultId = route.vaultId,
                    chainId = route.chainId,
                    validatorAddress = position.validatorAddress,
                )
            )
        }
    }

    fun move(position: CosmosStakePositionRow) {
        if (!canActOn(position)) return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingRedelegate(
                    vaultId = route.vaultId,
                    chainId = route.chainId,
                    validatorSrcAddress = position.validatorAddress,
                )
            )
        }
    }

    fun claimAll() {
        navigateSafely {
            navigator.route(
                Route.CosmosStakingWithdrawRewards(vaultId = route.vaultId, chainId = route.chainId)
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun buildRow(
        delegation: CosmosDelegation,
        validator: CosmosValidator?,
        pendingRewardRaw: BigDecimal,
        unbondings: List<CosmosUnbondingDelegation>,
        decimals: Int,
        now: Instant,
    ): CosmosStakePositionRow {
        val raw = BigInteger(delegation.balance.amount).toBigDecimal().movePointLeft(decimals)
        val pendingReward = pendingRewardRaw.movePointLeft(decimals)

        val status =
            when {
                validator != null &&
                    !validator.jailed &&
                    validator.status == CosmosValidator.Status.Bonded ->
                    CosmosStakePositionRow.ValidatorStatus.Active
                else -> CosmosStakePositionRow.ValidatorStatus.ChurnedOut
            }

        val pendingUnlock =
            unbondings
                .flatMap { it.entries }
                .filter { it.completionTime.isAfter(now) }
                .minByOrNull { it.completionTime }
                ?.completionTime

        return CosmosStakePositionRow(
            validatorAddress = delegation.validatorAddress,
            validatorMoniker = validator?.moniker.orEmpty(),
            validatorIdentity = validator?.identity,
            stakedAmount = raw,
            pendingReward = pendingReward,
            apyPercent = null,
            validatorStatus = status,
            pendingUnbondingUnlockDate = pendingUnlock,
        )
    }

    private fun canActOn(position: CosmosStakePositionRow): Boolean =
        position.validatorStatus == CosmosStakePositionRow.ValidatorStatus.Active &&
            position.pendingUnbondingUnlockDate == null

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
            _state.update { it.copy(ticker = nativeCoin.ticker, coinLogo = nativeCoin.logo) }
        }
    }

    private fun navigateSafely(block: suspend () -> Unit) {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to navigate from cosmos positions view") }
        ) {
            block()
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, isLoading = false) }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

private fun SavedStateHandle.toRoute(): Route.CosmosStakingPositions =
    Route.CosmosStakingPositions(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        chainId = checkNotNull(get<String>("chainId")) { "chainId is required" },
    )
