package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorRewards
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakePositionRow
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingAPYResolver
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosUnbondingDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.data.blockchain.cosmos.staking.KeybaseAvatarService
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
import kotlinx.coroutines.CoroutineDispatcher
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
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val apyResolver: CosmosStakingAPYResolver,
    private val keybaseAvatarService: KeybaseAvatarService,
    private val navigator: Navigator<Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Set by [setData] when the screen mounts (Maya/Tron DeFi-positions pattern). The positions
     * view is reached through the DeFi-tab `ChainDashboard` route, so vaultId + chainId arrive as
     * screen params rather than a navigation Route.
     */
    private var vaultId: String? = null
    private var chainId: String? = null

    private val _state = MutableStateFlow(CosmosStakingPositionsUiState())
    val state: StateFlow<CosmosStakingPositionsUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    /** Idempotent — re-invoking with the same args is a no-op so recomposition doesn't re-fetch. */
    fun setData(vaultId: String, chainId: String) {
        if (this.vaultId == vaultId && this.chainId == chainId) return
        this.vaultId = vaultId
        this.chainId = chainId
        loadCoin()
        refresh()
    }

    fun refresh() {
        val chainId = chainId ?: return
        val chain =
            Chain.entries.firstOrNull { it.raw.equals(chainId, ignoreCase = true) }
                ?: return setError("Unsupported chain: $chainId")

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
                withContext(ioDispatcher) {
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

            // Resolve chain APY in parallel with the validator metadata join. Failure collapses to
            // null and the rows degrade to baseline (LUNA) or hide (LUNC) APY.
            val chainApy =
                withContext(ioDispatcher) { apyResolver.chainApy(chain, entry.bondDenom) }

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
                        chainApy = chainApy,
                        chain = chain,
                    )
                }

            // Fire-and-forget avatar resolution — each emission updates the row in-place. The
            // initial render uses the monogram fallback; avatars swap in as the network catches up.
            resolveAvatarsAsync(positions)

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
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(Route.CosmosStakingDelegate(vaultId = vaultId, chainId = chainId))
        }
    }

    fun unstake(position: CosmosStakePositionRow) {
        if (!canActOn(position)) return
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingUndelegate(
                    vaultId = vaultId,
                    chainId = chainId,
                    validatorAddress = position.validatorAddress,
                )
            )
        }
    }

    fun move(position: CosmosStakePositionRow) {
        if (!canActOn(position)) return
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingRedelegate(
                    vaultId = vaultId,
                    chainId = chainId,
                    validatorSrcAddress = position.validatorAddress,
                )
            )
        }
    }

    fun claimAll() {
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingWithdrawRewards(vaultId = vaultId, chainId = chainId)
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
        chainApy: com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosChainApyData?,
        chain: Chain,
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

        // iOS computeAPY: if chainApy is available compute it; otherwise drop to baseline (LUNA
        // only); both paths require validator metadata to apply commission.
        val commission = validator?.commission ?: BigDecimal.ZERO
        val apyPercent =
            when {
                chainApy != null ->
                    CosmosStakingAPYResolver.computeValidatorAPY(chainApy, commission)
                validator != null -> {
                    val baseline = apyResolver.baselineFallback(chain)
                    if (baseline != null) {
                        val candidate =
                            baseline.multiply(BigDecimal.ONE.subtract(clamp01(commission)))
                        if (candidate > BigDecimal.ZERO) candidate else null
                    } else null
                }
                else -> null
            }

        return CosmosStakePositionRow(
            validatorAddress = delegation.validatorAddress,
            validatorMoniker = validator?.moniker.orEmpty(),
            validatorIdentity = validator?.identity,
            stakedAmount = raw,
            pendingReward = pendingReward,
            apyPercent = apyPercent,
            validatorAvatarUrl = null,
            validatorStatus = status,
            pendingUnbondingUnlockDate = pendingUnlock,
        )
    }

    private fun clamp01(value: BigDecimal): BigDecimal =
        when {
            value < BigDecimal.ZERO -> BigDecimal.ZERO
            value > BigDecimal.ONE -> BigDecimal.ONE
            else -> value
        }

    /**
     * Fires off Keybase avatar lookups for any row that has a non-empty identity. Lookups are
     * deduped per-identity by the [KeybaseAvatarService] cache, so concurrent rows with the same
     * identity share a single network call.
     */
    private fun resolveAvatarsAsync(rows: List<CosmosStakePositionRow>) {
        rows
            .filter { !it.validatorIdentity.isNullOrEmpty() }
            .forEach { row ->
                val identity = row.validatorIdentity ?: return@forEach
                viewModelScope.safeLaunch(
                    onError = { e -> Timber.w(e, "Keybase avatar resolve failed for %s", identity) }
                ) {
                    val url = keybaseAvatarService.avatarUrl(identity)
                    if (url != null) {
                        _state.update { current ->
                            current.copy(
                                positions =
                                    current.positions.map { p ->
                                        if (p.validatorAddress == row.validatorAddress)
                                            p.copy(validatorAvatarUrl = url)
                                        else p
                                    }
                            )
                        }
                    }
                }
            }
    }

    private fun canActOn(position: CosmosStakePositionRow): Boolean =
        position.validatorStatus == CosmosStakePositionRow.ValidatorStatus.Active &&
            position.pendingUnbondingUnlockDate == null

    private fun loadCoin() {
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load coin for cosmos positions view")
                setError("Failed to load wallet")
            }
        ) {
            val chain =
                Chain.entries.firstOrNull { it.raw.equals(chainId, ignoreCase = true) }
                    ?: return@safeLaunch setError("Unsupported chain: $chainId")
            if (!CosmosStakingConfig.isStakingSupported(chain)) {
                return@safeLaunch setError("Staking is not supported on ${chain.raw}")
            }
            val vault =
                withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                    ?: return@safeLaunch setError("Vault not found: $vaultId")
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
