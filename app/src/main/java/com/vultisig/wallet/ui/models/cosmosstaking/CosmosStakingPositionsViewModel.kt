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
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.CosmosStakingSnapshot
import com.vultisig.wallet.data.repositories.CosmosStakingSnapshotCache
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosStakingPositionsUiState(
    val ticker: String = "",
    val coinLogo: String = "",
    val positions: List<CosmosStakePositionRow> = emptyList(),
    /**
     * True only when at least one validator has accrued a whole base unit of rewards. Rewards
     * accrue as fractional base units (`cosmos.Dec`) but withdrawal floors to whole base units, so
     * a sub-1-base-unit reward shows a non-zero tile yet is unclaimable — gating Claim on it would
     * let the user pay a fee to withdraw nothing.
     */
    val hasClaimableRewards: Boolean = false,
    val pendingUnbondings: List<CosmosUnbondingDelegation> = emptyList(),
    val totalStaked: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // DeFi-chrome state (balance banner + Staked tab + Manage Positions), mirroring Tron/Maya.
    val isBalanceVisible: Boolean = true,
    /**
     * Fiat shown in the hero banner. Mirrors iOS `DefiBalanceService.cosmosStakingTotalBalance` —
     * the chain's DeFi balance is the *staked* total in fiat (`stakedBalance × spot price`), not
     * the liquid wallet balance — so it carries the same value as [totalStakedFiat].
     */
    val totalAmountPrice: String = "$0.00",
    /** Staked-total fiat shown on the Total Staked card — `totalStaked × spot price`. */
    val totalStakedFiat: String = "$0.00",
    val selectedTab: DeFiTab = DeFiTab.STAKED,
    val showPositionSelectionDialog: Boolean = false,
    /** Single stake-position tile (LUNA / LUNC) shown in the Manage Positions sheet. */
    val stakePositionsDialog: List<PositionUiModelDialog> = emptyList(),
    /** Enabled position keys. Defaults to [ticker] so the user's stake shows immediately. */
    val selectedPositions: List<String> = emptyList(),
    val tempSelectedPositions: List<String> = emptyList(),
) {
    val isPositionEnabled: Boolean
        get() = ticker.isNotEmpty() && selectedPositions.contains(ticker)
}

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
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val snapshotCache: CosmosStakingSnapshotCache,
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

    /**
     * Pull-to-refresh spinner state, owned by the VM so the screen never has to infer it from
     * [CosmosStakingPositionsUiState.isLoading]. A cache-seeded refresh keeps `isLoading` false, so
     * a screen-side `isLoading`-driven reset would leave the spinner spinning forever after the
     * first warm load. The VM flips this true at the start of every [refresh] and false when that
     * run finishes (issue #4773 review).
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * The in-flight [refresh] fan-out. Cancelled before each new run so a slow older response can't
     * land last and pin the screen (and the cached snapshot) to stale data.
     */
    private var loadJob: Job? = null

    private var coin: Coin? = null

    /** Idempotent — re-invoking with the same args is a no-op so recomposition doesn't re-fetch. */
    fun setData(vaultId: String, chainId: String) {
        if (this.vaultId == vaultId && this.chainId == chainId) return
        this.vaultId = vaultId
        this.chainId = chainId
        // [loadCoin] resolves the native coin then chains into [refresh] from the SAME coroutine.
        // We must NOT call refresh() here in parallel: loadCoin only assigns `coin` after it
        // suspends on the vault read, so a parallel refresh() would observe `coin == null`, bail at
        // its `coin ?: return` guard, and the delegations list would never load on first open.
        loadCoin()
    }

    fun refresh() {
        val chainId = chainId ?: return
        val chain =
            Chain.entries.firstOrNull { it.raw.equals(chainId, ignoreCase = true) }
                ?: return setError("Unsupported chain: $chainId")

        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load staking positions")
                    onRefreshFailed("Failed to load staking positions")
                }
            ) {
                _isRefreshing.value = true
                try {
                    val coin = coin ?: return@safeLaunch
                    val cacheKey = snapshotKey(chain, coin.address)

                    // Seed from the last-known snapshot so reopening renders immediately instead of
                    // flashing the empty/zero state; only show the loading state when nothing is
                    // cached
                    // (issue #4764). The live fan-out below refreshes in the background and
                    // overwrites it.
                    val cached = snapshotCache.read(cacheKey)
                    if (cached != null) {
                        _state.update {
                            it.copy(
                                positions = cached.positions,
                                hasClaimableRewards =
                                    hasClaimableRewards(cached.positions, coin.decimal),
                                pendingUnbondings = cached.pendingUnbondings,
                                totalStaked = cached.totalStaked,
                                totalStakedFiat = cached.totalStakedFiat,
                                // Banner fiat == staked-total fiat; the snapshot stores it once.
                                totalAmountPrice = cached.totalStakedFiat,
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                        // Re-resolve avatars for the cached rows so monograms upgrade while we
                        // refresh.
                        resolveAvatarsAsync(cached.positions)
                    } else {
                        _state.update { it.copy(isLoading = true, errorMessage = null) }
                    }

                    // Parallel fan-out — same shape as iOS `refresh(address:decimals:)`.
                    // Delegations is the
                    // load-bearing read and keeps its [Result] so a failure surfaces an error
                    // banner (iOS
                    // sets `self.error` on this branch alone). Validators also keeps its [Result]:
                    // a failed
                    // validators read collapses every delegation to "Churned Out", so we must know
                    // it failed
                    // to avoid freezing that degraded view as the cached snapshot. Unbondings /
                    // rewards
                    // degrade silently to empty so a transient outage on either just hides that
                    // sub-row.
                    val (delegationsResult, unbondings, rewards, validatorsResult) =
                        withContext(ioDispatcher) {
                            val delegationsDeferred = async {
                                runCatching {
                                        cosmosStakingService.fetchDelegations(chain, coin.address)
                                    }
                                    .also {
                                        it.exceptionOrNull()?.let { Timber.w(it, "delegations") }
                                    }
                            }
                            val unbondingsDeferred = async {
                                runCatching {
                                        cosmosStakingService.fetchUnbondingDelegations(
                                            chain,
                                            coin.address,
                                        )
                                    }
                                    .also {
                                        it.exceptionOrNull()?.let { Timber.w(it, "unbondings") }
                                    }
                                    .getOrDefault(emptyList())
                            }
                            val rewardsDeferred = async {
                                runCatching {
                                        cosmosStakingService.fetchDelegatorRewards(
                                            chain,
                                            coin.address,
                                        )
                                    }
                                    .also { it.exceptionOrNull()?.let { Timber.w(it, "rewards") } }
                                    .getOrDefault(CosmosDelegatorRewards(emptyList(), emptyList()))
                            }
                            val validatorsDeferred = async {
                                runCatching { cosmosStakingService.fetchValidators(chain) }
                                    .also {
                                        it.exceptionOrNull()?.let { Timber.w(it, "validators") }
                                    }
                            }
                            Quad(
                                delegationsDeferred.await(),
                                unbondingsDeferred.await(),
                                rewardsDeferred.await(),
                                validatorsDeferred.await(),
                            )
                        }

                    // A failed delegations read must not be swallowed into an empty positions view
                    // — a
                    // vault
                    // with active stake would falsely render the empty state on a transient LCD
                    // blip.
                    // Surface
                    // the error so the user sees a retry instead of a silent "no positions".
                    val delegations =
                        delegationsResult.getOrElse {
                            return@safeLaunch onRefreshFailed("Failed to load staking positions")
                        }
                    val validators = validatorsResult.getOrDefault(emptyList())

                    val entry = CosmosStakingConfig.entryFor(chain)
                    val bondDenom = entry.bondDenom

                    // Filter rewards to the bond denom — Terra Classic LCDs occasionally return
                    // reward
                    // entries in non-staking denoms (legacy stability-tax pool); aggregating them
                    // as if
                    // they were `uluna` would overstate the user's claimable native rewards. iOS
                    // verbatim.
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

                    // Resolve chain APY in parallel with the validator metadata join. Failure
                    // collapses to
                    // null and the rows degrade to baseline (LUNA) or hide (LUNC) APY.
                    val chainApy =
                        withContext(ioDispatcher) { apyResolver.chainApy(chain, entry.bondDenom) }

                    // Resolve the spot price so the banner, the Total Staked card, and each row can
                    // show
                    // fiat (iOS parity — the screen previously hardcoded $0.00). Price is cosmetic:
                    // a fetch
                    // failure degrades every fiat slot to a zero-formatted value rather than
                    // erroring.
                    val currency = appCurrencyRepository.currency.first()
                    val currencyFormat = appCurrencyRepository.getCurrencyFormat()
                    val resolvedPrice =
                        withContext(ioDispatcher) {
                            runCatching { tokenPriceRepository.refresh(listOf(coin)) }
                            runCatching { tokenPriceRepository.getCachedPrice(coin.id, currency) }
                                .getOrNull()
                        }
                    val price = resolvedPrice ?: BigDecimal.ZERO

                    val positions =
                        delegations.map { delegation ->
                            buildRow(
                                delegation = delegation,
                                validator = validatorsByAddress[delegation.validatorAddress],
                                pendingRewardRaw =
                                    rewardsByValidator[delegation.validatorAddress]
                                        ?: BigDecimal.ZERO,
                                unbondings =
                                    unbondingsByValidator[delegation.validatorAddress].orEmpty(),
                                decimals = decimals,
                                now = now,
                                chainApy = chainApy,
                                chain = chain,
                                price = price,
                                currencyFormat = currencyFormat,
                            )
                        }

                    val totalStaked =
                        positions.fold(BigDecimal.ZERO) { acc, p -> acc + p.stakedAmount }
                    // iOS parity: the DeFi banner shows the staked total in fiat (not the liquid
                    // wallet
                    // balance), so the banner and the Total Staked card carry the same value.
                    val totalStakedFiat = currencyFormat.format(totalStaked.multiply(price))

                    _state.update {
                        it.copy(
                            positions = positions,
                            hasClaimableRewards = hasClaimableRewards(positions, decimals),
                            pendingUnbondings = unbondings,
                            totalStaked = totalStaked,
                            totalStakedFiat = totalStakedFiat,
                            totalAmountPrice = totalStakedFiat,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }

                    // Cache the display-ready result so the next open within this session renders
                    // instantly — but only when the snapshot is actually trustworthy. A degraded
                    // validators
                    // read folds every delegation to "Churned Out", and a missing price renders
                    // every fiat
                    // slot as $0.00; freezing either as the "known good" snapshot would reseed that
                    // alarming
                    // view on the next reopen until the live refresh repairs it. Skip the write so
                    // a
                    // transient LCD blip is never persisted as truth (issue #4773 review).
                    val priceOk = resolvedPrice != null && resolvedPrice.signum() > 0
                    if (validatorsResult.isSuccess && priceOk) {
                        snapshotCache.write(
                            cacheKey,
                            CosmosStakingSnapshot(
                                positions = positions,
                                pendingUnbondings = unbondings,
                                totalStaked = totalStaked,
                                totalStakedFiat = totalStakedFiat,
                            ),
                        )
                    }

                    // Fire-and-forget avatar resolution — each emission updates the row in-place.
                    // Published
                    // after the list is in `_state` so a cache-hit patch can't land on an empty
                    // `positions`
                    // and then be overwritten. The initial render uses the monogram fallback;
                    // avatars swap
                    // in as the network catches up.
                    resolveAvatarsAsync(positions)
                } finally {
                    // Only the still-current run clears the spinner. A superseded run reaches this
                    // block via cancellation (isActive == false) and must leave the flag for
                    // whichever
                    // refresh replaced it; a normally-completing run is still active here and
                    // resets it.
                    if (isActive) _isRefreshing.value = false
                }
            }
    }

    fun stakeMore() {
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingDelegate(vaultId = vaultId, chainId = chainId),
                popOptionsForStaking(Route.CosmosStakingDelegate::class.java),
            )
        }
    }

    fun unstake(position: CosmosStakePositionRow) {
        if (!canUnstake(position)) return
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingUndelegate(
                    vaultId = vaultId,
                    chainId = chainId,
                    validatorAddress = position.validatorAddress,
                ),
                popOptionsForStaking(Route.CosmosStakingUndelegate::class.java),
            )
        }
    }

    fun move(position: CosmosStakePositionRow) {
        if (!canMove(position)) return
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingRedelegate(
                    vaultId = vaultId,
                    chainId = chainId,
                    validatorSrcAddress = position.validatorAddress,
                ),
                popOptionsForStaking(Route.CosmosStakingRedelegate::class.java),
            )
        }
    }

    fun claimAll() {
        val vaultId = vaultId ?: return
        val chainId = chainId ?: return
        navigateSafely {
            navigator.route(
                Route.CosmosStakingWithdrawRewards(vaultId = vaultId, chainId = chainId),
                popOptionsForStaking(Route.CosmosStakingWithdrawRewards::class.java),
            )
        }
    }

    /**
     * Forces any prior backstack entry of the SAME staking destination class to be popped before
     * the new one lands. Required because `NavController.buildOptions` sets `launchSingleTop =
     * true` — without `popUpTo` the navigator reuses the existing entry (and its Hilt-scoped
     * ViewModel, whose `route` field was set once at init via `savedStateHandle.toRoute()`).
     * Symptom (mainnet tx 6E4615C1…B379D): tapping Claim from the TerraClassic positions screen
     * rebroadcast against the PRIOR phoenix-1 chain context when the user had visited the LUNA
     * claim earlier in the session. Forcing a fresh entry resets the savedStateHandle to the new
     * chain's args.
     */
    private fun popOptionsForStaking(routeClass: Class<*>): NavigationOptions =
        NavigationOptions(popUpToRoute = routeClass.kotlin, inclusive = true)

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
        price: BigDecimal,
        currencyFormat: NumberFormat,
    ): CosmosStakePositionRow {
        val raw = BigInteger(delegation.balance.amount).toBigDecimal().movePointLeft(decimals)
        val pendingReward = pendingRewardRaw.movePointLeft(decimals)
        val stakedFiatDisplay = currencyFormat.format(raw.multiply(price))

        val status =
            when {
                validator != null &&
                    !validator.jailed &&
                    validator.status == CosmosValidator.Status.Bonded ->
                    CosmosStakePositionRow.ValidatorStatus.Active
                else -> CosmosStakePositionRow.ValidatorStatus.ChurnedOut
            }

        val activeUnbondingEntries =
            unbondings.flatMap { it.entries }.filter { it.completionTime.isAfter(now) }
        val pendingUnlock = activeUnbondingEntries.minByOrNull { it.completionTime }?.completionTime

        // iOS computeAPY: if chainApy is available compute it; otherwise drop to baseline (LUNA
        // only). Both paths require validator metadata — a churned-out validator (validator ==
        // null)
        // is absent from the bonded set and earns nothing, so it must fall through to a null APY
        // rather than rendering the full uncut chain rate (commission defaults to 0) beneath its
        // own
        // "Churned Out" badge.
        val commission = validator?.commission ?: BigDecimal.ZERO
        val apyPercent =
            when {
                chainApy != null && validator != null ->
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
            stakedFiatDisplay = stakedFiatDisplay,
            pendingReward = pendingReward,
            apyPercent = apyPercent,
            validatorAvatarUrl = null,
            validatorStatus = status,
            pendingUnbondingUnlockDate = pendingUnlock,
            pendingUnbondingEntryCount = activeUnbondingEntries.size,
        )
    }

    private fun clamp01(value: BigDecimal): BigDecimal =
        when {
            value < BigDecimal.ZERO -> BigDecimal.ZERO
            value > BigDecimal.ONE -> BigDecimal.ONE
            else -> value
        }

    // A reward is claimable only once it reaches a whole base unit, since withdrawal floors the
    // fractional cosmos.Dec accrual. `pendingReward` is in coin units, so one base unit is 10^-dp.
    private fun hasClaimableRewards(
        positions: List<CosmosStakePositionRow>,
        decimals: Int,
    ): Boolean {
        val oneBaseUnit = BigDecimal.ONE.movePointLeft(decimals)
        return positions.any { it.pendingReward >= oneBaseUnit }
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

    // Unstake is legal until the validator hits cosmos-sdk's MAX_ENTRIES (7) concurrent unbonding
    // entries — a partial unstake with fewer than 7 pending entries must NOT be blocked, otherwise
    // the remaining bonded stake is trapped for 21 days after a single partial unbond.
    private fun canUnstake(position: CosmosStakePositionRow): Boolean =
        !position.maxUnbondingEntriesReached

    // Redelegating AWAY from a validator places no bonded-status requirement on the source
    // (cosmos-sdk MsgBeginRedelegate). So Move must stay enabled for a churned-out (jailed/slashed)
    // validator — it is the only instant escape, since undelegate forces the 21-day unbonding wait.
    private fun canMove(position: CosmosStakePositionRow): Boolean =
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
            val ticker = nativeCoin.ticker
            _state.update {
                it.copy(
                    ticker = ticker,
                    coinLogo = nativeCoin.logo,
                    stakePositionsDialog =
                        listOf(
                            PositionUiModelDialog(
                                logo = getCoinLogo(nativeCoin.logo),
                                ticker = ticker,
                                positionKey = ticker,
                            )
                        ),
                    // Default-enabled so the user sees their delegations on first open (Tron
                    // pattern); the Manage Positions sheet can toggle it off.
                    selectedPositions = listOf(ticker),
                    tempSelectedPositions = listOf(ticker),
                )
            }
            // Now that `coin` is resolved, load the delegations / unbondings / rewards fan-out.
            refresh()
        }
    }

    fun onTabSelected(tab: DeFiTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun setPositionSelectionDialogVisibility(visible: Boolean) {
        _state.update {
            it.copy(
                showPositionSelectionDialog = visible,
                tempSelectedPositions = it.selectedPositions,
            )
        }
    }

    fun onPositionSelectionChange(ticker: String, selected: Boolean) {
        _state.update {
            val updated =
                if (selected) it.tempSelectedPositions + ticker
                else it.tempSelectedPositions - ticker
            it.copy(tempSelectedPositions = updated)
        }
    }

    fun onPositionSelectionDone() {
        _state.update {
            it.copy(
                showPositionSelectionDialog = false,
                selectedPositions = it.tempSelectedPositions,
            )
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

    /**
     * A background refresh failed. If positions are already on screen (seeded from cache or a prior
     * load), keep showing them rather than wiping the view with an error — a transient LCD blip
     * shouldn't blank a screen the user can already read. Only surface the error when nothing is
     * rendered yet.
     */
    private fun onRefreshFailed(message: String) {
        _state.update { current ->
            if (current.positions.isNotEmpty()) current.copy(isLoading = false)
            else current.copy(errorMessage = message, isLoading = false)
        }
    }

    /** Per-address, per-chain cache key so LUNA and LUNC snapshots never collide. */
    private fun snapshotKey(chain: Chain, address: String): String = "${chain.raw}:$address"

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
