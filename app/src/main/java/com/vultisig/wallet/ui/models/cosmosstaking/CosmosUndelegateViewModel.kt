package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

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
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route: Route.CosmosStakingUndelegate = savedStateHandle.toRoute()

    val amountFieldState: TextFieldState = TextFieldState()

    private val _state =
        MutableStateFlow(CosmosUndelegateUiState(validatorAddress = route.validatorAddress))
    val state: StateFlow<CosmosUndelegateUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        loadCoinAndStakedBalance()
    }

    fun onPercentageChange(percent: Int) {
        _state.update { it.copy(percentageSelected = percent) }
        applyPercentage(percent)
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
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

        viewModelScope.safeLaunch(
            onError = { e -> setError(e.message ?: "Failed to build undelegate transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            try {
                ValidatorBech32Preflight.validate(route.validatorAddress, coin.chain)
            } catch (e: ValidatorBech32Preflight.ValidatorBech32Exception) {
                return@safeLaunch setError("Validator address is not valid for this chain")
            }

            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val entry = CosmosStakingConfig.entryFor(coin.chain)
            val amountBaseUnits =
                CosmosStakingAmountFormatter.baseUnitsString(
                    amountDecimal.toPlainString(),
                    coin.decimal,
                )
            val gasFee = TokenValue(value = BigInteger.valueOf(entry.feeAmount), token = coin)

            val specific =
                withContext(Dispatchers.IO) {
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
                    dstAddress = route.validatorAddress,
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
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")
            val nativeCoin =
                vault.coins.firstOrNull { it.chain == chain && it.isNativeToken }
                    ?: return@safeLaunch setError(
                        "Native ${chain.raw} coin not loaded for this vault"
                    )
            coin = nativeCoin

            val entry = CosmosStakingConfig.entryFor(chain)
            val delegations =
                withContext(Dispatchers.IO) {
                    cosmosStakingService.fetchDelegations(chain, nativeCoin.address)
                }
            val matching =
                delegations.firstOrNull {
                    it.validatorAddress == route.validatorAddress &&
                        it.balance.denom == entry.bondDenom
                }
            val stakedBalance =
                matching?.balance?.amount?.toBigDecimalOrNull()?.movePointLeft(nativeCoin.decimal)
                    ?: BigDecimal.ZERO

            val (moniker, _) =
                withContext(Dispatchers.IO) {
                    validatorMonikerAndIdentity(chain, route.validatorAddress)
                }

            val unbondingMsg = buildUnbondingLockMessage(chain)

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
                    isLoading = false,
                )
            }
        }
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
    )
