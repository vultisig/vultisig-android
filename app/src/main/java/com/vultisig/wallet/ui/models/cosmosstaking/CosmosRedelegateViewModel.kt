package com.vultisig.wallet.ui.models.cosmosstaking

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationCooldownGate
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationCooldownState
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingAmountFormatter
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.data.blockchain.cosmos.staking.KeybaseAvatarService
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
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.BigInteger
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosRedelegateUiState(
    val ticker: String = "",
    val srcValidatorAddress: String = "",
    val srcValidatorMoniker: String = "",
    val stakedBalance: BigDecimal = BigDecimal.ZERO,
    val dstValidators: List<CosmosValidator> = emptyList(),
    val selectedDstValidator: CosmosValidator? = null,
    val validatorSearchQuery: String = "",
    val isShowingPicker: Boolean = false,
    val isLoadingValidators: Boolean = false,
    val isLoadingCooldown: Boolean = false,
    val cooldownState: CosmosRedelegationCooldownState = CosmosRedelegationCooldownState.Available,
    val cooldownBlockedMessage: String? = null,
    val percentageSelected: Int = 100,
    /** Liquid bond-denom balance (human decimal). Drives [hasSufficientBalanceForFee]. */
    val spendableBalance: BigDecimal = BigDecimal.ZERO,
    /** Per-chain fixed fee in human decimal units, used by the fee preflight. */
    val estimatedFee: BigDecimal = BigDecimal.ZERO,
    /**
     * Result of the spec Risk 3 preflight: `spendableBalance >= estimatedFee`. When false the
     * inline "insufficient liquid balance for fee" microcopy appears and Continue is disabled.
     * Without this, a 100%-bonded user is led into an MPC ceremony for a tx the chain immediately
     * rejects with `code:11 insufficient fees`.
     */
    val hasSufficientBalanceForFee: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val validForm: Boolean
        get() =
            cooldownState is CosmosRedelegationCooldownState.Available && hasSufficientBalanceForFee
}

/**
 * View-model for the LUNA / LUNC redelegate flow. Source validator is pre-selected from the
 * position card; destination validator is selected via a picker sheet (with the source excluded).
 *
 * Pre-flight check against `/cosmos/staking/v1beta1/delegators/{addr}/redelegations` runs on init —
 * if the source validator is under cooldown, the screen surfaces the unlock date inline and submit
 * is blocked regardless of input. Spec Risk 4.
 *
 * Port of iOS `CosmosRedelegateTransactionViewModel.swift` (vultisig-ios PR #4432).
 */
@HiltViewModel
internal class CosmosRedelegateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildCosmosStakingKeysignPayload: BuildCosmosStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val keybaseAvatarService: KeybaseAvatarService,
    private val balanceRepository: BalanceRepository,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Lazily resolves a validator's Keybase avatar for the destination-picker rows (monogram
     * fallback when absent). [KeybaseAvatarService] caches per identity.
     */
    suspend fun resolveValidatorAvatar(identity: String?): String? {
        val id = identity?.takeIf { it.isNotEmpty() } ?: return null
        return withContext(ioDispatcher) {
            runCatching { keybaseAvatarService.avatarUrl(id) }.getOrNull()
        }
    }

    private val route: Route.CosmosStakingRedelegate = savedStateHandle.toRoute()

    val amountFieldState: TextFieldState = TextFieldState()

    private val _state =
        MutableStateFlow(CosmosRedelegateUiState(srcValidatorAddress = route.validatorSrcAddress))
    val state: StateFlow<CosmosRedelegateUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        // loadValidators() is chain-only and safe to run in parallel. loadCooldown() reads
        // `coin.address`, so it must NOT launch here alongside loadCoinAndStakedBalance(): coin is
        // only assigned after that coroutine suspends on the vault read, so a parallel cooldown
        // fetch would observe `coin == null`, bail at its guard, and silently skip the 21-day
        // redelegation gate on first open. It is chained from loadCoinAndStakedBalance() instead.
        loadCoinAndStakedBalance()
        loadValidators()
    }

    fun openValidatorPicker() {
        _state.update { it.copy(isShowingPicker = true, validatorSearchQuery = "") }
    }

    fun closeValidatorPicker() {
        _state.update { it.copy(isShowingPicker = false) }
    }

    fun selectDstValidator(validator: CosmosValidator) {
        _state.update {
            it.copy(selectedDstValidator = validator, isShowingPicker = false, errorMessage = null)
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(validatorSearchQuery = query) }
    }

    fun onPercentageChange(percent: Int) {
        _state.update { it.copy(percentageSelected = percent) }
        applyPercentage(percent)
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Excluded set passed to the picker — the source is never a valid destination for itself. */
    fun visibleValidators(state: CosmosRedelegateUiState): List<CosmosValidator> {
        val query = state.validatorSearchQuery.trim().lowercase()
        return state.dstValidators
            .asSequence()
            .filter { !it.jailed && it.status == CosmosValidator.Status.Bonded }
            .filter { it.operatorAddress != state.srcValidatorAddress }
            .filter { v ->
                if (query.isEmpty()) true
                else
                    v.moniker.lowercase().contains(query) ||
                        v.operatorAddress.lowercase().contains(query)
            }
            .sortedByDescending { it.votingPower }
            .toList()
    }

    fun submit() {
        val currentState = _state.value
        if (currentState.isSubmitting) return
        if (currentState.cooldownState is CosmosRedelegationCooldownState.Blocked) {
            return setError(
                currentState.cooldownBlockedMessage ?: "Source validator is under cooldown"
            )
        }

        val dstValidator =
            currentState.selectedDstValidator
                ?: return setError("Pick a destination validator before continuing")

        val amountText = amountFieldState.text.toString().trim()
        val amountDecimal = amountText.toBigDecimalOrNull()
        if (amountDecimal == null || amountDecimal <= BigDecimal.ZERO) {
            return setError("Enter a positive amount to move")
        }
        if (amountDecimal > currentState.stakedBalance) {
            return setError("Amount exceeds your staked balance at this validator")
        }
        // Defense-in-depth fee preflight — also gated on the form via validForm. The redelegate
        // fee is fixed per chain in CosmosStakingConfig; without this check a 100%-bonded user
        // burns an MPC signing round on a tx the chain rejects with `code:11 insufficient fees`.
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
            onError = { e -> setError(e.message ?: "Failed to build redelegate transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(ioDispatcher) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            try {
                ValidatorBech32Preflight.validate(route.validatorSrcAddress, coin.chain)
                ValidatorBech32Preflight.validate(dstValidator.operatorAddress, coin.chain)
            } catch (_: ValidatorBech32Preflight.ValidatorBech32Exception) {
                return@safeLaunch setError("One of the validator addresses is invalid")
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
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = route.validatorSrcAddress,
                    validatorDstAddress = dstValidator.operatorAddress,
                    denom = entry.bondDenom,
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
                    dstAddress = dstValidator.operatorAddress,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    signDirect = keysignPayload.signDirect,
                    cosmosStakingPayload = payload,
                )

            depositTransactionRepository.addTransaction(depositTx)

            navigator.route(
                Route.CosmosStakingVerify(vaultId = route.vaultId, transactionId = depositTx.id)
            )
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun loadCoinAndStakedBalance() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load coin / staked balance for cosmos redelegate flow")
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
                    it.validatorAddress == route.validatorSrcAddress &&
                        it.balance.denom == entry.bondDenom
                }
            val stakedBalance =
                matching?.balance?.amount?.toBigDecimalOrNull()?.movePointLeft(nativeCoin.decimal)
                    ?: BigDecimal.ZERO

            // Default amount = 100% of staked, matching iOS pattern (slider type defaults to max).
            amountFieldState.edit {
                replace(0, length, stakedBalance.stripTrailingZeros().toPlainString())
            }

            val spendableBalance = withContext(ioDispatcher) { fetchSpendableBalance(nativeCoin) }
            val estimatedFee = BigDecimal(entry.feeAmount).movePointLeft(nativeCoin.decimal)
            val hasSufficientBalanceForFee = spendableBalance >= estimatedFee

            _state.update {
                it.copy(
                    ticker = nativeCoin.ticker,
                    stakedBalance = stakedBalance,
                    percentageSelected = 100,
                    spendableBalance = spendableBalance,
                    estimatedFee = estimatedFee,
                    hasSufficientBalanceForFee = hasSufficientBalanceForFee,
                )
            }
            // `coin` is now resolved — evaluate the redelegation cooldown gate.
            loadCooldown()
        }
    }

    private suspend fun fetchSpendableBalance(coin: Coin): BigDecimal {
        // Cached native-coin balance via BalanceRepository (same path the send-form uses). Falls
        // back to zero on cache miss — conservatively trips the insufficient-fee warning until the
        // next refresh, preferring a false-positive block over a false-negative MPC burn.
        return runCatching {
                val pair = balanceRepository.getCachedTokenBalanceAndPrice(coin.address, coin)
                val tokenValue = pair.tokenBalance.tokenValue ?: return@runCatching BigDecimal.ZERO
                BigDecimal(tokenValue.value).movePointLeft(coin.decimal)
            }
            .getOrDefault(BigDecimal.ZERO)
    }

    private fun loadValidators() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to fetch validators")
                _state.update { it.copy(isLoadingValidators = false) }
                setError("Failed to load validator list")
            }
        ) {
            val chain =
                Chain.entries.firstOrNull { it.raw.equals(route.chainId, ignoreCase = true) }
                    ?: return@safeLaunch
            _state.update { it.copy(isLoadingValidators = true, errorMessage = null) }
            val validators =
                withContext(ioDispatcher) { cosmosStakingService.fetchValidators(chain) }
            val srcMoniker =
                validators
                    .firstOrNull { it.operatorAddress == route.validatorSrcAddress }
                    ?.moniker
                    .orEmpty()
            _state.update {
                it.copy(
                    dstValidators = validators,
                    srcValidatorMoniker = srcMoniker,
                    isLoadingValidators = false,
                )
            }
        }
    }

    /**
     * Mirrors iOS `loadCooldown()` — fetches the user's outstanding redelegations and runs the gate
     * against the source validator. Failure to load is treated as Available rather than Blocked
     * (the chain is the final arbiter, so we avoid spurious blocking when the LCD is unreachable).
     */
    private fun loadCooldown() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.w(e, "Redelegation cooldown fetch failed; defaulting to available")
                _state.update {
                    it.copy(
                        cooldownState = CosmosRedelegationCooldownState.Available,
                        isLoadingCooldown = false,
                    )
                }
            }
        ) {
            val chain =
                Chain.entries.firstOrNull { it.raw.equals(route.chainId, ignoreCase = true) }
                    ?: return@safeLaunch
            val coin = coin ?: return@safeLaunch
            _state.update { it.copy(isLoadingCooldown = true) }
            val redelegations =
                withContext(ioDispatcher) {
                    cosmosStakingService.fetchRedelegations(chain, coin.address)
                }
            val cooldown =
                CosmosRedelegationCooldownGate.evaluate(
                    sourceValidator = route.validatorSrcAddress,
                    redelegations = redelegations,
                )
            val message =
                when (cooldown) {
                    is CosmosRedelegationCooldownState.Available -> null
                    is CosmosRedelegationCooldownState.Blocked -> {
                        val formatted =
                            DateTimeFormatter.ofPattern("MMM d, yyyy")
                                .withZone(ZoneId.systemDefault())
                                .format(cooldown.unlocksAt)
                        val days = CosmosStakingConfig.unbondingDaysFor(chain)
                        context.getString(
                            R.string.cosmos_staking_redelegate_cooldown_message,
                            days,
                            formatted,
                        )
                    }
                }
            _state.update {
                it.copy(
                    cooldownState = cooldown,
                    cooldownBlockedMessage = message,
                    isLoadingCooldown = false,
                )
            }
        }
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

private fun SavedStateHandle.toRoute(): Route.CosmosStakingRedelegate =
    Route.CosmosStakingRedelegate(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        chainId = checkNotNull(get<String>("chainId")) { "chainId is required" },
        validatorSrcAddress =
            checkNotNull(get<String>("validatorSrcAddress")) { "validatorSrcAddress is required" },
    )
