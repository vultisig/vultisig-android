package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SolanaMoveIntentRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * View-model for the Solana move-stake step 1 ("Move SOL") screen. Solana has no native redelegate,
 * so moving a stake account to another validator is a guided cross-epoch flow: the user picks the
 * destination validator here, then Continue deactivates the source account (starting the ~1-epoch
 * cooldown). The chosen destination is persisted (via [SolanaMoveIntentRepository]) so the later
 * "Finish Move" step pre-fills it. The whole account moves (wallet-core has no split instruction).
 */
@HiltViewModel
internal class SolanaMoveStakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val loadValidatorOptions: LoadSolanaValidatorOptionsUseCase,
    private val moveIntentRepository: SolanaMoveIntentRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.SolanaMoveStake>()

    private val _state =
        MutableStateFlow(SolanaMoveStakeUiState(stakePubkey = shortAddress(route.stakePubkey)))
    val state: StateFlow<SolanaMoveStakeUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        load()
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(validatorSearchQuery = query) }
    }

    fun openValidatorPicker() {
        _state.update { it.copy(isShowingPicker = true, validatorSearchQuery = "") }
    }

    fun closeValidatorPicker() {
        _state.update { it.copy(isShowingPicker = false) }
    }

    fun selectValidator(validator: SolanaValidatorOption) {
        _state.update { it.copy(selectedValidator = validator, isShowingPicker = false) }
    }

    fun visibleValidators(state: SolanaMoveStakeUiState): List<SolanaValidatorOption> {
        val query = state.validatorSearchQuery.trim()
        if (query.isEmpty()) return state.validators
        return state.validators.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.votePubkey.contains(query, ignoreCase = true)
        }
    }

    private fun load() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load Solana move-stake data")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error =
                            com.vultisig.wallet.R.string.error_view_default_description.asUiText(),
                    )
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val solCoin =
                vault.coins.firstOrNull { it.chain == Chain.Solana && it.isNativeToken }
                    ?: error("SOL not in this vault")
            coin = solCoin

            val options = loadValidatorOptions(solCoin)
            // Pre-select any destination the user chose on a prior visit to this move.
            val remembered =
                moveIntentRepository.getDestination(route.stakePubkey)?.let { saved ->
                    options.firstOrNull { it.votePubkey == saved }
                }
            _state.update {
                it.copy(validators = options, selectedValidator = remembered, isLoading = false)
            }
        }
    }

    fun onContinue() {
        if (_state.value.isSubmitting) return
        val validator = _state.value.selectedValidator ?: return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to build Solana move-stake tx")
                _state.update {
                    it.copy(isSubmitting = false, error = (e.message ?: "").asUiText())
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val solCoin = coin ?: error("SOL not in this vault")

            // Remember the destination so "Finish Move" pre-fills it after the cooldown.
            moveIntentRepository.setDestination(route.stakePubkey, validator.votePubkey)

            val payload = SolanaStakingPayload.unstake(stakeAccount = route.stakePubkey)
            val movedStake = route.delegatedStake.toBigIntegerOrNull() ?: BigInteger.ZERO
            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = solCoin)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = solCoin.address,
                    token = solCoin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )
            val keysignPayload =
                buildKeysignPayload(
                    coin = solCoin,
                    payload = payload,
                    blockChainSpecific = specific.blockChainSpecific,
                    balanceLamports = BigInteger.ZERO,
                    vaultPublicKeyECDSA = vault.pubKeyECDSA,
                    vaultLocalPartyID = vault.localPartyID,
                    libType = vault.libType,
                )
            val depositTx =
                DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = route.vaultId,
                    srcToken = solCoin,
                    srcAddress = solCoin.address,
                    // A move re-delegates the whole stake to the chosen validator, so the verify
                    // screen surfaces the moved amount and the destination validator (To). The
                    // underlying step-1 tx is a deactivate — unlike a plain Unstake (which shows
                    // 0),
                    // a move is confirming "move <amount> to <validator>", so both must be visible.
                    srcTokenValue = TokenValue(value = movedStake, token = solCoin),
                    memo = "",
                    dstAddress = validator.votePubkey,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    solanaStakingPayload = payload,
                    signSolana = keysignPayload.signSolana,
                )
            depositTransactionRepository.addTransaction(depositTx)
            navigator.route(
                Route.VerifyDeposit(vaultId = route.vaultId, transactionId = depositTx.id)
            )
        }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }

    private fun shortAddress(address: String): String =
        if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address
}

@Immutable
internal data class SolanaMoveStakeUiState(
    val stakePubkey: String = "",
    val isLoading: Boolean = true,
    val validators: List<SolanaValidatorOption> = emptyList(),
    val selectedValidator: SolanaValidatorOption? = null,
    val validatorSearchQuery: String = "",
    val isShowingPicker: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)
