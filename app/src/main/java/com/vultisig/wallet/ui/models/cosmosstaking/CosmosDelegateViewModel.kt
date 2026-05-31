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

internal data class CosmosDelegateUiState(
    val ticker: String = "",
    val validators: List<CosmosValidator> = emptyList(),
    val selectedValidator: CosmosValidator? = null,
    val validatorSearchQuery: String = "",
    val isShowingPicker: Boolean = false,
    val isLoadingValidators: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * View-model for the LUNA / LUNC delegate flow. Owns the form state (amount + validator) and
 * orchestrates the submit path:
 * 1. Fetches validators via [CosmosStakingService].
 * 2. On `submit`, builds a [CosmosStakingPayload.Delegate], resolves
 *    [com.vultisig.wallet.data.models.payload.BlockChainSpecific.Cosmos] (account number +
 *    sequence) via [BlockChainSpecificRepository], builds a complete
 *    [com.vultisig.wallet.data.models.payload.KeysignPayload] via
 *    [BuildCosmosStakingKeysignPayloadUseCase], packs the result into a [DepositTransaction] with
 *    `signDirect` populated, persists via [DepositTransactionRepository], and navigates to the
 *    existing verify-deposit screen.
 *
 * The keysign side reads `DepositTransaction.signDirect` (added in this PR) and forwards the bytes
 * to the existing `CosmosHelper.buildSignDirectSigningInput` path — no changes to the MPC pipeline.
 */
@HiltViewModel
internal class CosmosDelegateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildCosmosStakingKeysignPayload: BuildCosmosStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<com.vultisig.wallet.ui.navigation.Destination>,
) : ViewModel() {

    private val route: Route.CosmosStakingDelegate = savedStateHandle.toRoute()

    val amountFieldState: TextFieldState = TextFieldState()

    private val _state = MutableStateFlow(CosmosDelegateUiState())
    val state: StateFlow<CosmosDelegateUiState> = _state.asStateFlow()

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

    fun selectValidator(validator: CosmosValidator) {
        _state.update {
            it.copy(selectedValidator = validator, isShowingPicker = false, errorMessage = null)
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(validatorSearchQuery = query) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Filtered + sorted validator list driven by the current state. Sorted by voting power desc;
     * jailed + non-bonded validators are filtered out (the staking module would reject a delegate
     * to them anyway).
     */
    fun visibleValidators(state: CosmosDelegateUiState): List<CosmosValidator> {
        val query = state.validatorSearchQuery.trim().lowercase()
        return state.validators
            .asSequence()
            .filter { !it.jailed && it.status == CosmosValidator.Status.Bonded }
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

        val validator =
            currentState.selectedValidator
                ?: return setError("Please pick a validator before continuing")

        val amountText = amountFieldState.text.toString().trim()
        val amountDecimal = amountText.toBigDecimalOrNull()
        if (amountDecimal == null || amountDecimal <= BigDecimal.ZERO) {
            return setError("Enter a positive amount to delegate")
        }

        viewModelScope.safeLaunch(
            onError = { e -> setError(e.message ?: "Failed to build delegate transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            // Sanity-check at the boundary — the resolver runs the full bech32 preflight again
            // before producing SignDoc bytes, but failing fast here gives the user a clearer error.
            try {
                ValidatorBech32Preflight.validate(validator.operatorAddress, coin.chain)
            } catch (e: ValidatorBech32Preflight.ValidatorBech32Exception) {
                return@safeLaunch setError(
                    "Selected validator is not a valid Terra valoper address"
                )
            }

            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val entry = CosmosStakingConfig.entryFor(coin.chain)
            // Use the shared formatter — guarantees the same RoundingMode.DOWN truncation across
            // all four staking flows so the user can never silently over-stake.
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
                        isMaxAmountEnabled = false,
                        isDeposit = true,
                    )
                }

            val payload =
                CosmosStakingPayload.Delegate(
                    validatorAddress = validator.operatorAddress,
                    denom = entry.bondDenom,
                    amount = amountBaseUnits,
                )

            // Builds the KeysignPayload with `signDirect` populated by the resolver. The use case
            // is pure logic — the resolver inside it runs the validator preflight + pubkey-shape
            // checks and will throw before any bytes are produced if the inputs are invalid.
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
                    dstAddress = validator.operatorAddress,
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
                Timber.e(e, "Failed to load coin for cosmos delegate flow")
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
            _state.update { it.copy(validators = validators, isLoadingValidators = false) }
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(errorMessage = message, isSubmitting = false) }
    }
}

private fun SavedStateHandle.toRoute(): Route.CosmosStakingDelegate =
    Route.CosmosStakingDelegate(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        chainId = checkNotNull(get<String>("chainId")) { "chainId is required" },
    )
