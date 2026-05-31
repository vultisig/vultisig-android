package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorReward
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingSignDataResolver
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
    val rewards: List<CosmosDelegatorReward> = emptyList(),
    val selectedValidators: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val maxBatchSize: Int
        get() = CosmosStakingSignDataResolver.MAX_BATCH_WITHDRAW_VALIDATORS

    val isAtCap: Boolean
        get() = selectedValidators.size >= maxBatchSize
}

/**
 * View-model for the LUNA / LUNC claim-rewards flow. Multi-select up to 8 validators per
 * transaction (LUNC `columbus-5` gas math floor — a 9-validator batch would exceed block gas).
 * Toggling beyond the cap is rejected; "select all" stops at the cap.
 *
 * On submit, encodes one `MsgWithdrawDelegatorReward` per selected validator into a single TxBody
 * (the resolver enforces the cap again as defense-in-depth).
 */
@HiltViewModel
internal class CosmosWithdrawRewardsViewModel
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

    private val route: Route.CosmosStakingWithdrawRewards = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(CosmosWithdrawRewardsUiState())
    val state: StateFlow<CosmosWithdrawRewardsUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        loadCoin()
        loadRewards()
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun toggleValidator(validatorAddress: String) {
        _state.update { s ->
            val current = s.selectedValidators
            val updated =
                when {
                    current.contains(validatorAddress) -> current - validatorAddress
                    current.size >= s.maxBatchSize -> current
                    else -> current + validatorAddress
                }
            s.copy(selectedValidators = updated, errorMessage = null)
        }
    }

    fun selectAll() {
        _state.update { s ->
            val cap = s.maxBatchSize
            // Select-all respects the cap — first N validators with non-zero rewards.
            val candidates = s.rewards.map { it.validatorAddress }.take(cap).toSet()
            s.copy(selectedValidators = candidates, errorMessage = null)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedValidators = emptySet()) }
    }

    fun submit() {
        val currentState = _state.value
        if (currentState.isSubmitting) return
        if (currentState.selectedValidators.isEmpty()) {
            return setError("Select at least one validator to claim from")
        }

        viewModelScope.safeLaunch(
            onError = { e -> setError(e.message ?: "Failed to build claim transaction") }
        ) {
            val coin = coin ?: return@safeLaunch setError("Wallet not loaded yet")
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError("Vault not found: ${route.vaultId}")

            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val entry = CosmosStakingConfig.entryFor(coin.chain)
            val selected = currentState.selectedValidators.toList()
            val msgCount = selected.size.toLong().coerceAtLeast(1)
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
                CosmosStakingPayload.WithdrawRewards(validators = selected, denom = entry.bondDenom)

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
                    dstAddress = selected.first(),
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
                Timber.e(e, "Failed to load coin for cosmos withdrawRewards flow")
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

    private fun loadRewards() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to fetch rewards")
                _state.update { it.copy(isLoading = false) }
                setError("Failed to load delegator rewards")
            }
        ) {
            val chain =
                Chain.entries.firstOrNull { it.raw.equals(route.chainId, ignoreCase = true) }
                    ?: return@safeLaunch
            val vault =
                withContext(Dispatchers.IO) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch
            val nativeCoin =
                vault.coins.firstOrNull { it.chain == chain && it.isNativeToken }
                    ?: return@safeLaunch
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val rewards =
                withContext(Dispatchers.IO) {
                    cosmosStakingService.fetchDelegatorRewards(chain, nativeCoin.address)
                }
            _state.update { it.copy(rewards = rewards.rewards, isLoading = false) }
        }
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
