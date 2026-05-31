package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
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

internal data class CosmosUndelegateUiState(
    val ticker: String = "",
    val validatorAddress: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * View-model for the LUNA / LUNC undelegate flow. Validator is pre-selected at route construction
 * (from the user's active-delegation card) — no picker needed. Same submit shape as
 * [CosmosDelegateViewModel] but encodes a [CosmosStakingPayload.Undelegate].
 */
@HiltViewModel
internal class CosmosUndelegateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
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
        loadCoin()
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

    private fun loadCoin() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load coin for cosmos undelegate flow")
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
