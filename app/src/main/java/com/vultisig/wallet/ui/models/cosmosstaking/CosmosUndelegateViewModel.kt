package com.vultisig.wallet.ui.models.cosmosstaking

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingAmountFormatter
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.ValidatorBech32Preflight
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
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosUndelegateUiState(
    val ticker: String = "",
    val validatorAddress: String = "",
    val validatorMoniker: String = "",
    /**
     * Currently-staked human-decimal balance at this validator. Caps the amount field —
     * undelegating more than staked is rejected by the chain post-broadcast, so we fail closed at
     * form-validate time. Mirrors iOS `CosmosUndelegateTransactionViewModel.stakedBalance`.
     */
    val stakedBalance: BigDecimal = BigDecimal.ZERO,
    /**
     * 21-day unbonding-lock microcopy — surfaced inline so the user accepts the lock before
     * confirming. Computed from `CosmosStakingConfig.unbondingDays` + today's date.
     */
    val unbondingLockMessage: String? = null,
    val percentageSelected: Int = 100,
    /** Liquid bond-denom balance (human decimal). Drives [hasSufficientBalanceForFee]. */
    val spendableBalance: BigDecimal = BigDecimal.ZERO,
    /**
     * Per-chain fixed fee in human decimal units. Used by the fee-preflight check so the user is
     * never sent into an MPC ceremony for a tx the chain will reject with `code:11 insufficient
     * fees`.
     */
    val estimatedFee: BigDecimal = BigDecimal.ZERO,
    /**
     * Result of the spec Risk 3 preflight: `spendableBalance >= estimatedFee`. When false, the
     * inline "insufficient liquid balance for fee" microcopy appears and Continue is disabled. A
     * staking-heavy user often has 100% of their LUNA bonded; without this check they would burn an
     * MPC signing round on a tx the chain immediately rejects.
     */
    val hasSufficientBalanceForFee: Boolean = true,
    /**
     * Count of non-expired unbonding entries already pending at this validator. cosmos-sdk caps
     * concurrent entries at [CosmosStakingConfig.MAX_ENTRIES]; submitting past the cap is rejected
     * on-chain with `ErrMaxUnbondingDelegationEntries`, so we preflight it.
     */
    val unbondingEntryCount: Int = 0,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val maxUnbondingEntriesReached: Boolean
        get() = unbondingEntryCount >= CosmosStakingConfig.MAX_ENTRIES

    val validForm: Boolean
        get() = hasSufficientBalanceForFee && !maxUnbondingEntriesReached
}

/**
 * View-model for the LUNA / LUNC undelegate flow. Validator is pre-selected by the caller (always
 * launched from a position card on the DeFi tab); there's no validator picker. Amount is bounded by
 * the currently-staked balance at that validator.
 *
 * Port of iOS `CosmosUndelegateTransactionViewModel.swift` (vultisig-ios PR #4432).
 */
@HiltViewModel
internal class CosmosUndelegateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildCosmosStakingKeysignPayload: BuildCosmosStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val balanceRepository: BalanceRepository,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val route: Route.CosmosStakingUndelegate = savedStateHandle.toRoute()

    val amountFieldState: TextFieldState = TextFieldState()

    // Plain-decimal staked amount handed over from the tapped position, so the form shows the real
    // value on first frame instead of `0` until the LCD re-fetch lands (#4815). null on a direct/
    // deep-link entry, where we fall back to the network value only.
    private val prefilledStakedBalance: BigDecimal? =
        route.stakedAmount?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

    private val _state =
        MutableStateFlow(
            CosmosUndelegateUiState(
                validatorAddress = route.validatorAddress,
                stakedBalance = prefilledStakedBalance ?: BigDecimal.ZERO,
            )
        )
    val state: StateFlow<CosmosUndelegateUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        // Prefill the amount field from the carried staked balance (100% default) so the user sees
        // their stake immediately; loadCoinAndStakedBalance overwrites it once the LCD read lands.
        prefilledStakedBalance?.let { staked ->
            amountFieldState.edit {
                replace(0, length, staked.stripTrailingZeros().toPlainString())
            }
        }
        loadCoinAndStakedBalance()
    }

    fun onPercentageChange(percent: Int) {
        _state.update { it.copy(percentageSelected = percent) }
        applyPercentage(percent)
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun submit() {
        val currentState = _state.value
        if (currentState.isSubmitting) return

        val amountText = amountFieldState.text.toString().trim()
        val amountDecimal = amountText.toBigDecimalOrNull()
        if (amountDecimal == null || amountDecimal <= BigDecimal.ZERO) {
            return setError("Enter a positive amount to unstake")
        }
        if (amountDecimal > currentState.stakedBalance) {
            return setError("Amount exceeds your staked balance at this validator")
        }
        // Preflight cosmos-sdk's MaxEntries cap: a 100%-bonded user who already has 7 pending
        // unbondings at this validator would burn an MPC ceremony on a guaranteed
        // ErrMaxUnbondingDelegationEntries rejection. Also gated on the form via validForm.
        if (currentState.maxUnbondingEntriesReached) {
            return setError(
                context.getString(
                    R.string.cosmos_staking_max_entries_reached,
                    CosmosStakingConfig.MAX_ENTRIES,
                )
            )
        }
        // Defense-in-depth: the form also gates on validForm, but a scripted/programmatic submit
        // path (or a stale snapshot from a state race) could still slip through. The check is
        // cheap.
        if (!currentState.hasSufficientBalanceForFee) {
            return setError(
                context.getString(
                    R.string.cosmos_staking_insufficient_fee_balance,
                    currentState.ticker,
                )
            )
        }

        // Flip the flag before launching so two quick taps can't both pass the guard above and
        // start duplicate submit coroutines. Cleared by setError on any failure path.
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.safeLaunch(
            onError = { e -> setError(e.message ?: "Failed to build undelegate transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(ioDispatcher) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            try {
                ValidatorBech32Preflight.validate(route.validatorAddress, coin.chain)
            } catch (_: ValidatorBech32Preflight.ValidatorBech32Exception) {
                return@safeLaunch setError("Validator address is not valid for this chain")
            }

            val entry = CosmosStakingConfig.entryFor(coin.chain)
            val amountBaseUnits =
                CosmosStakingAmountFormatter.baseUnitsString(
                    amountDecimal.toPlainString(),
                    coin.decimal,
                )
            val gasFee = TokenValue(value = BigInteger.valueOf(entry.feeAmount), token = coin)

            val specific =
                withContext(ioDispatcher) {
                    blockChainSpecificRepository.getSpecific(
                        chain = coin.chain,
                        address = coin.address,
                        token = coin,
                        gasFee = gasFee,
                        isSwap = false,
                        isMaxAmountEnabled = currentState.percentageSelected == 100,
                        isDeposit = true,
                    )
                }

            val payload =
                CosmosStakingPayload.Undelegate(
                    validatorAddress = route.validatorAddress,
                    amount = amountBaseUnits,
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
                    srcTokenValue = TokenValue(value = BigInteger(amountBaseUnits), token = coin),
                    memo = "",
                    dstAddress = route.validatorAddress,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    signDirect = keysignPayload.signDirect,
                    cosmosStakingPayload = payload,
                )

            depositTransactionRepository.addTransaction(depositTx)

            navigator.route(
                Route.CosmosStakingVerify(vaultId = route.vaultId, transactionId = depositTx.id),
                NavigationOptions(popUpToRoute = Route.CosmosStakingVerify::class, inclusive = true),
            )
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun loadCoinAndStakedBalance() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load coin / staked balance for cosmos undelegate flow")
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
                withContext(ioDispatcher) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")
            val nativeCoin =
                vault.coins.firstOrNull { it.chain == chain && it.isNativeToken }
                    ?: return@safeLaunch setError(
                        "Native ${chain.raw} coin not loaded for this vault"
                    )
            coin = nativeCoin

            val entry = CosmosStakingConfig.entryFor(chain)
            val delegations =
                withContext(ioDispatcher) {
                    cosmosStakingService.fetchDelegations(chain, nativeCoin.address)
                }
            val matching =
                delegations.firstOrNull {
                    it.validatorAddress == route.validatorAddress &&
                        it.balance.denom == entry.bondDenom
                }
            val fetchedStakedBalance =
                matching?.balance?.amount?.toBigDecimalOrNull()?.movePointLeft(nativeCoin.decimal)
                    ?: BigDecimal.ZERO
            // The LCD read is authoritative when it returns a real delegation; if it yields zero
            // (no match / transient blip) keep the prefilled hint rather than wiping the field
            // back to 0 (#4815).
            val stakedBalance =
                if (fetchedStakedBalance > BigDecimal.ZERO) fetchedStakedBalance
                else prefilledStakedBalance ?: fetchedStakedBalance

            val (moniker, _) =
                withContext(ioDispatcher) {
                    validatorMonikerAndIdentity(chain, route.validatorAddress)
                }

            val unbondingMsg = buildUnbondingLockMessage(chain)

            val spendableBalance =
                withContext(ioDispatcher) { balanceRepository.cachedSpendableBalance(nativeCoin) }
            val estimatedFee = BigDecimal(entry.feeAmount).movePointLeft(nativeCoin.decimal)
            val hasSufficientBalanceForFee = spendableBalance >= estimatedFee

            // Count this validator's already-pending unbonding entries so submit can be gated on
            // the
            // MAX_ENTRIES cap before signing. A fetch failure degrades to zero (no block) — the
            // chain remains the final arbiter, matching the cooldown gate's fail-open posture.
            val unbondingEntryCount =
                withContext(ioDispatcher) {
                    activeUnbondingEntryCount(chain, nativeCoin.address, route.validatorAddress)
                }

            // Default to 100% selected (iOS pattern) — pre-fill the amount field so the user can
            // confirm with one tap if they want to unstake everything.
            amountFieldState.edit {
                replace(0, length, stakedBalance.stripTrailingZeros().toPlainString())
            }

            _state.update {
                it.copy(
                    ticker = nativeCoin.ticker,
                    validatorMoniker = moniker.orEmpty(),
                    stakedBalance = stakedBalance,
                    unbondingLockMessage = unbondingMsg,
                    percentageSelected = 100,
                    spendableBalance = spendableBalance,
                    estimatedFee = estimatedFee,
                    hasSufficientBalanceForFee = hasSufficientBalanceForFee,
                    unbondingEntryCount = unbondingEntryCount,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Counts the delegator's non-expired unbonding entries at [validatorAddress]. Used to preflight
     * cosmos-sdk's `MaxEntries` cap so an undelegate that the chain would reject with
     * `ErrMaxUnbondingDelegationEntries` never reaches the MPC ceremony. A fetch failure returns 0
     * (fail-open) — the chain stays the final arbiter.
     */
    private suspend fun activeUnbondingEntryCount(
        chain: Chain,
        delegatorAddress: String,
        validatorAddress: String,
    ): Int =
        try {
            val now = Instant.now()
            cosmosStakingService
                .fetchUnbondingDelegations(chain, delegatorAddress)
                .filter { it.validatorAddress == validatorAddress }
                .flatMap { it.entries }
                .count { it.completionTime.isAfter(now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unbonding-entry count fetch failed for %s", validatorAddress)
            0
        }

    private suspend fun validatorMonikerAndIdentity(
        chain: Chain,
        validatorAddress: String,
    ): Pair<String?, String?> =
        try {
            val list = cosmosStakingService.fetchValidators(chain)
            list
                .firstOrNull { it.operatorAddress == validatorAddress }
                ?.let { it.moniker to it.identity } ?: (null to null)
        } catch (e: Exception) {
            Timber.w(e, "Validator metadata fetch failed for $validatorAddress")
            null to null
        }

    private fun buildUnbondingLockMessage(chain: Chain): String {
        val days = CosmosStakingConfig.unbondingDaysFor(chain)
        val unlockDate = Instant.now().plusSeconds(days * 86_400L)
        val formatted =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                .withZone(ZoneId.systemDefault())
                .format(unlockDate)
        return "Funds are locked for $days days. Available on $formatted."
    }

    private fun applyPercentage(percent: Int) {
        val staked = _state.value.stakedBalance
        if (staked <= BigDecimal.ZERO) return
        val amount =
            staked
                .multiply(BigDecimal(percent))
                .divide(BigDecimal(100), 8, java.math.RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        amountFieldState.edit { replace(0, length, amount) }
    }

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, isSubmitting = false) }
    }
}

private fun SavedStateHandle.toRoute(): Route.CosmosStakingUndelegate =
    Route.CosmosStakingUndelegate(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        chainId = checkNotNull(get<String>("chainId")) { "chainId is required" },
        validatorAddress =
            checkNotNull(get<String>("validatorAddress")) { "validatorAddress is required" },
        stakedAmount = get<String>("stakedAmount"),
    )
