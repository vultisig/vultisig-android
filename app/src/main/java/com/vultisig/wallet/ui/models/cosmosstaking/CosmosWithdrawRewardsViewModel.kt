package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingSignDataResolver
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosWithdrawRewardsCandidate
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosWithdrawRewardsUiState(
    val ticker: String = "",
    val candidates: List<CosmosWithdrawRewardsCandidate> = emptyList(),
    val selectedValidators: Set<String> = emptySet(),
    /**
     * True once the user has tried to add a validator beyond the 8-validator soft cap (or when
     * `candidates.size > 8` at load). Surfaces an inline "max 8 per batch" microcopy. Cleared by
     * any successful deselect.
     */
    val hitBatchCapWarning: Boolean = false,
    /** Sum of pending rewards across the currently-selected validators (human decimal). */
    val totalSelectedReward: BigDecimal = BigDecimal.ZERO,
    /** Per-msg fee × selected count, in human decimal. Scales linearly. */
    val estimatedFee: BigDecimal = BigDecimal.ZERO,
    /**
     * Result of the Spec Risk 3 pre-flight: `coin.balance >= feeAmount × N`. When false, the inline
     * "insufficient balance for fee" microcopy appears and Continue is disabled.
     */
    val hasSufficientBalanceForFee: Boolean = true,
    /** Spendable bond-denom balance (human decimal). Drives [hasSufficientBalanceForFee]. */
    val spendableBalance: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Soft UI cap per Spec Decision 9. Matches the resolver hard cap. */
    val maxBatchSize: Int
        get() = CosmosStakingSignDataResolver.MAX_BATCH_WITHDRAW_VALIDATORS

    val validForm: Boolean
        get() =
            selectedValidators.isNotEmpty() &&
                selectedValidators.size <= maxBatchSize &&
                hasSufficientBalanceForFee
}

/**
 * Selection-driven claim-rewards flow VM for LUNA / LUNC. The user picks *which* validators to
 * claim from (not *how much*); the per-validator pending reward is what's claimed.
 *
 * Three load-bearing rules per the spec risk register (Port of iOS
 * `CosmosWithdrawRewardsTransactionViewModel.swift` — vultisig-ios PR #4432):
 * - Multi-validator batch is signed in ONE MPC ceremony via a single multi-msg TxBody (D-2).
 * - Soft UI cap of 8 validators per batch (D-9) — beyond 8 the gas budget on LUNC (8 × 1.5M = 12M
 *   units → 800M uluna) becomes user-hostile.
 * - Balance pre-flight (Risk 3): `coin.balance >= feeAmount × N`. Fail closed at form-validate time
 *   so the user never burns MPC on an insufficient-fees rejection.
 *
 * On load, candidates are fetched by joining `fetchDelegatorRewards` against `fetchValidators` so
 * each candidate carries the validator moniker (not just the truncated valoper). Default selection
 * is the first N candidates (cap-respecting).
 */
@HiltViewModel
internal class CosmosWithdrawRewardsViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildCosmosStakingKeysignPayload: BuildCosmosStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route: Route.CosmosStakingWithdrawRewards = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(CosmosWithdrawRewardsUiState())
    val state: StateFlow<CosmosWithdrawRewardsUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        loadCandidates()
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun toggle(validatorAddress: String) {
        _state.update { s ->
            val cap = s.maxBatchSize
            val current = s.selectedValidators
            val updated: Set<String>
            val hitCap: Boolean
            if (current.contains(validatorAddress)) {
                updated = current - validatorAddress
                hitCap = false
            } else if (current.size >= cap) {
                // Reject the add — fire the cap warning so the UI can flash a microcopy.
                return@update s.copy(hitBatchCapWarning = true)
            } else {
                updated = current + validatorAddress
                hitCap = s.hitBatchCapWarning && updated.size >= cap
            }
            recomputeTotals(
                s.copy(
                    selectedValidators = updated,
                    hitBatchCapWarning = hitCap,
                    errorMessage = null,
                )
            )
        }
    }

    /**
     * Mirrors iOS `toggleSelectAll()`. If everything (up to the cap) is already selected, deselect
     * all. Otherwise select the first cap candidates and raise the cap warning if there are more.
     */
    fun toggleSelectAll() {
        _state.update { s ->
            val cap = s.maxBatchSize
            val targetSize = minOf(s.candidates.size, cap)
            val isAllSelected = s.selectedValidators.size == targetSize
            if (isAllSelected) {
                recomputeTotals(s.copy(selectedValidators = emptySet(), hitBatchCapWarning = false))
            } else {
                val target = s.candidates.take(cap).map { it.validatorAddress }.toSet()
                recomputeTotals(
                    s.copy(
                        selectedValidators = target,
                        hitBatchCapWarning = s.candidates.size > cap,
                    )
                )
            }
        }
    }

    fun submit() {
        val currentState = _state.value
        if (currentState.isSubmitting || !currentState.validForm) return

        viewModelScope.safeLaunch(
            onError = { e -> setError(e.message ?: "Failed to build claim transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val entry = CosmosStakingConfig.entryFor(coin.chain)
            // Preserve LCD return order for byte-equality with the SDK reference (iOS comment).
            val orderedValidators =
                currentState.candidates
                    .map { it.validatorAddress }
                    .filter { currentState.selectedValidators.contains(it) }
            val msgCount = orderedValidators.size.toLong().coerceAtLeast(1)
            val feeForBatch = entry.feeAmount * msgCount
            val gasFee = TokenValue(value = BigInteger.valueOf(feeForBatch), token = coin)

            val specific =
                withContext(Dispatchers.IO) {
                    blockChainSpecificRepository.getSpecific(
                        chain = coin.chain,
                        address = coin.address,
                        token = coin,
                        gasFee = gasFee,
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = true,
                    )
                }

            val payload =
                CosmosStakingPayload.WithdrawRewards(
                    validators = orderedValidators,
                    denom = entry.bondDenom,
                )

            val keysignPayload =
                buildCosmosStakingKeysignPayload(
                    coin = coin,
                    payload = payload,
                    blockChainSpecific = specific.blockChainSpecific,
                    vaultPublicKeyECDSA = vault.pubKeyECDSA,
                    vaultLocalPartyID = vault.localPartyID,
                    libType = vault.libType,
                )

            val depositTx =
                DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = route.vaultId,
                    srcToken = coin,
                    srcAddress = coin.address,
                    srcTokenValue = TokenValue(value = BigInteger.ZERO, token = coin),
                    memo = "",
                    dstAddress = orderedValidators.first(),
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    signDirect = keysignPayload.signDirect,
                )

            depositTransactionRepository.addTransaction(depositTx)

            navigator.route(
                Route.VerifyDeposit(transactionId = depositTx.id, vaultId = route.vaultId)
            )
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun loadCandidates() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to fetch claim candidates")
                _state.update { it.copy(isLoading = false) }
                setError("Failed to load rewards")
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

            _state.update { it.copy(isLoading = true, ticker = nativeCoin.ticker) }

            val entry = CosmosStakingConfig.entryFor(chain)
            // Sequential fetches — the LCD is fast enough that parallel fan-out's complexity
            // isn't worth it here; the positions view (which the user lands on first) already
            // hits these endpoints in parallel.
            val rewards =
                withContext(Dispatchers.IO) {
                    runCatching {
                            cosmosStakingService.fetchDelegatorRewards(chain, nativeCoin.address)
                        }
                        .getOrDefault(
                            com.vultisig.wallet.data.blockchain.cosmos.staking
                                .CosmosDelegatorRewards(emptyList(), emptyList())
                        )
                }
            val validators =
                withContext(Dispatchers.IO) {
                    runCatching { cosmosStakingService.fetchValidators(chain) }
                        .getOrDefault(emptyList())
                }
            val spendableBalance = withContext(Dispatchers.IO) { fetchSpendableBalance(nativeCoin) }

            val monikerByAddress = validators.associateBy({ it.operatorAddress }, { it.moniker })
            val candidates =
                rewards.rewards
                    .map { reward ->
                        val bondDenomTotal =
                            reward.reward
                                .filter { it.denom == entry.bondDenom }
                                .mapNotNull { it.amount.toBigDecimalOrNull() }
                                .fold(BigDecimal.ZERO) { acc, v -> acc + v }
                                .movePointLeft(nativeCoin.decimal)
                        CosmosWithdrawRewardsCandidate(
                            validatorAddress = reward.validatorAddress,
                            validatorMoniker = monikerByAddress[reward.validatorAddress].orEmpty(),
                            pendingReward = bondDenomTotal,
                        )
                    }
                    .filter { it.pendingReward > BigDecimal.ZERO }

            // Default-select all but cap at the soft batch size — mirrors iOS init behavior.
            val cap = CosmosStakingSignDataResolver.MAX_BATCH_WITHDRAW_VALIDATORS
            val preselected = candidates.take(cap).map { it.validatorAddress }.toSet()

            val initialState =
                _state.value.copy(
                    candidates = candidates,
                    selectedValidators = preselected,
                    hitBatchCapWarning = candidates.size > cap,
                    spendableBalance = spendableBalance,
                    isLoading = false,
                )
            _state.update { recomputeTotals(initialState) }
        }
    }

    private suspend fun fetchSpendableBalance(coin: Coin): BigDecimal {
        // Use the cached balance pair off the BalanceRepository. Native-coin spendable balance for
        // the bond-denom — the same path the send-form path uses. Falls back to zero on cache
        // miss, which conservatively trips the insufficient-fee warning until the next refresh.
        return runCatching {
                val pair = balanceRepository.getCachedTokenBalanceAndPrice(coin.address, coin)
                val tokenValue = pair.tokenBalance.tokenValue ?: return@runCatching BigDecimal.ZERO
                BigDecimal(tokenValue.value).movePointLeft(coin.decimal)
            }
            .getOrDefault(BigDecimal.ZERO)
    }

    private fun recomputeTotals(s: CosmosWithdrawRewardsUiState): CosmosWithdrawRewardsUiState {
        val totalSelectedReward =
            s.candidates
                .filter { s.selectedValidators.contains(it.validatorAddress) }
                .fold(BigDecimal.ZERO) { acc, c -> acc + c.pendingReward }
        val coin = coin
        val entryFeeBaseUnits =
            coin?.let {
                runCatching { CosmosStakingConfig.feeAmountFor(it.chain) }.getOrDefault(0L)
            } ?: 0L
        val estimatedFee =
            coin?.let {
                BigDecimal(entryFeeBaseUnits)
                    .multiply(BigDecimal(s.selectedValidators.size))
                    .movePointLeft(it.decimal)
            } ?: BigDecimal.ZERO
        val hasSufficientBalanceForFee =
            if (s.selectedValidators.isEmpty()) true else s.spendableBalance >= estimatedFee
        return s.copy(
            totalSelectedReward = totalSelectedReward,
            estimatedFee = estimatedFee,
            hasSufficientBalanceForFee = hasSufficientBalanceForFee,
        )
    }

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, isSubmitting = false) }
    }
}

private fun SavedStateHandle.toRoute(): Route.CosmosStakingWithdrawRewards =
    Route.CosmosStakingWithdrawRewards(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        chainId = checkNotNull(get<String>("chainId")) { "chainId is required" },
    )
