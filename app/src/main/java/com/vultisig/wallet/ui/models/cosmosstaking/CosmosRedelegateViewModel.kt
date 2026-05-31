package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationCooldownGate
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
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
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class CosmosRedelegateUiState(
    val ticker: String = "",
    val srcValidatorAddress: String = "",
    val dstValidators: List<CosmosValidator> = emptyList(),
    val selectedDstValidator: CosmosValidator? = null,
    val validatorSearchQuery: String = "",
    val isShowingPicker: Boolean = false,
    val isLoadingValidators: Boolean = false,
    val cooldownDaysLeft: Long? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * View-model for the LUNA / LUNC redelegate flow. Src validator comes from the route (the user taps
 * "Move" on the active-delegation card); dst is picked from a list that excludes src.
 *
 * Submit runs the [CosmosRedelegationCooldownGate] before producing SignDoc bytes — if any existing
 * in-cooldown redelegation conflicts with the selected pair, the submit is blocked and the UI
 * surfaces the days-left microcopy.
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
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route: Route.CosmosStakingRedelegate = savedStateHandle.toRoute()

    val amountFieldState: TextFieldState = TextFieldState()

    private val _state =
        MutableStateFlow(CosmosRedelegateUiState(srcValidatorAddress = route.validatorSrcAddress))
    val state: StateFlow<CosmosRedelegateUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        loadCoin()
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

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

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

        val dstValidator =
            currentState.selectedDstValidator
                ?: return setError("Pick a destination validator before continuing")

        val amountText = amountFieldState.text.toString().trim()
        val amountDecimal = amountText.toBigDecimalOrNull()
        if (amountDecimal == null || amountDecimal <= BigDecimal.ZERO) {
            return setError("Enter a positive amount to move")
        }

        viewModelScope.safeLaunch(
            onError = { e -> setError(e.message ?: "Failed to build redelegate transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            try {
                ValidatorBech32Preflight.validate(route.validatorSrcAddress, coin.chain)
                ValidatorBech32Preflight.validate(dstValidator.operatorAddress, coin.chain)
            } catch (e: ValidatorBech32Preflight.ValidatorBech32Exception) {
                return@safeLaunch setError("One of the validator addresses is invalid")
            }

            // 21-day cooldown gate. Re-fetch redelegations now so the check uses fresh on-chain
            // state — UI state can be stale if the picker has been open a while.
            val cooldownHit =
                withContext(Dispatchers.IO) {
                    val redelegations =
                        cosmosStakingService.fetchRedelegations(coin.chain, coin.address)
                    CosmosRedelegationCooldownGate.cooldownFor(
                        validatorSrcAddress = route.validatorSrcAddress,
                        validatorDstAddress = dstValidator.operatorAddress,
                        redelegations = redelegations,
                    )
                }
            if (cooldownHit != null) {
                val daysLeft = CosmosRedelegationCooldownGate.daysUntil(cooldownHit.completionTime)
                _state.update { it.copy(cooldownDaysLeft = daysLeft) }
                return@safeLaunch setError(
                    "Redelegation is locked for $daysLeft more day${if (daysLeft == 1L) "" else "s"} — Cosmos forbids overlapping moves"
                )
            }

            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val entry = CosmosStakingConfig.entryFor(coin.chain)
            val amountBaseUnits =
                amountDecimal.movePointRight(coin.decimal).toBigInteger().toString()
            val gasFee = TokenValue(value = BigInteger.valueOf(entry.feeAmount), token = coin)

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
                )

            depositTransactionRepository.addTransaction(depositTx)

            navigator.route(
                Route.VerifyDeposit(transactionId = depositTx.id, vaultId = route.vaultId)
            )
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun loadCoin() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load coin for cosmos redelegate flow")
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
                withContext(Dispatchers.IO) { cosmosStakingService.fetchValidators(chain) }
            _state.update { it.copy(dstValidators = validators, isLoadingValidators = false) }
        }
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
