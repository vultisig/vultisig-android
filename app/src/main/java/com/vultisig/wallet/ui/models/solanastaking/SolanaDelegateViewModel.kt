package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingConfig
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadataProvider
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import timber.log.Timber

/** A pickable validator in the delegate screen. */
@Immutable
internal data class SolanaValidatorOption(
    val votePubkey: String,
    val name: String,
    val commissionDisplay: String,
    val apyDisplay: String?,
)

@Immutable
internal data class SolanaDelegateUiState(
    val validators: List<SolanaValidatorOption> = emptyList(),
    val selectedVotePubkey: String? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)

/**
 * Delegate (stake) flow for Solana native staking: pick a validator + amount, then build a
 * create-and-delegate transaction in one tx. Funding = entered amount + the live rent-exempt
 * reserve (the active stake equals the entered amount). The byte-parity `SignSolana` is built via
 * [BuildSolanaStakingKeysignPayloadUseCase] and carried on the [DepositTransaction] so the generic
 * VerifyDeposit -> keysign flow signs it. Mirrors the iOS delegate flow (vultisig-ios #4661).
 */
@HiltViewModel
internal class SolanaDelegateViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val solanaStakingService: SolanaStakingService,
    private val validatorMetadataProvider: ValidatorMetadataProvider,
    private val solanaApi: SolanaApi,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<com.vultisig.wallet.ui.navigation.Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.SolanaDelegate>()

    val amountFieldState = TextFieldState()

    private val _state = MutableStateFlow(SolanaDelegateUiState())
    val state: StateFlow<SolanaDelegateUiState> = _state.asStateFlow()

    init {
        loadValidators()
    }

    fun onValidatorSelected(votePubkey: String) {
        _state.update { it.copy(selectedVotePubkey = votePubkey) }
    }

    private fun loadValidators() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load Solana validators")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error =
                            com.vultisig.wallet.R.string.error_view_default_description.asUiText(),
                    )
                }
            }
        ) {
            val validators =
                solanaStakingService
                    .fetchValidators()
                    .filter { !it.delinquent }
                    .sortedByDescending { it.activatedStake }
            val metadata = validatorMetadataProvider.metadata(validators.map { it.votePubkey })
            val options =
                validators.map { v ->
                    val md = metadata[v.votePubkey]
                    SolanaValidatorOption(
                        votePubkey = v.votePubkey,
                        name = md?.name?.takeIf { it.isNotBlank() } ?: shortAddress(v.votePubkey),
                        commissionDisplay = "${v.commission}%",
                        apyDisplay =
                            md?.apyEstimate?.let {
                                it.multiply(BigDecimal(100))
                                    .setScale(2, java.math.RoundingMode.HALF_UP)
                                    .toPlainString() + "%"
                            },
                    )
                }
            _state.update {
                it.copy(
                    validators = options,
                    selectedVotePubkey = options.firstOrNull()?.votePubkey,
                    isLoading = false,
                )
            }
        }
    }

    fun submit() {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Solana delegate submit failed")
                _state.update {
                    it.copy(isSubmitting = false, error = (e.message ?: "").asUiText())
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val coin =
                vault.coins.firstOrNull { it.chain == Chain.Solana && it.isNativeToken }
                    ?: error("SOL not in this vault")

            val votePubkey =
                _state.value.selectedVotePubkey ?: error("Select a validator to stake with")

            val amountSol =
                amountFieldState.text.toString().trim().toBigDecimalOrNull()
                    ?: error("Enter a valid amount")
            val amountLamports =
                amountSol.movePointRight(coin.decimal).toBigIntegerExact().also {
                    require(it.signum() > 0) { "Amount must be greater than zero" }
                }
            require(amountLamports >= SolanaStakingConfig.MINIMUM_DELEGATION_LAMPORTS) {
                "Minimum delegation is 1 SOL"
            }

            val rentReserve =
                solanaApi
                    .getMinimumBalanceForRentExemption(SolanaStakingConfig.STAKE_ACCOUNT_SPACE)
                    .takeIf { it.signum() > 0 }
                    ?: SolanaStakingConfig.RENT_EXEMPT_RESERVE_FALLBACK_LAMPORTS
            // Funding = active delegated stake (entered amount) + rent-exempt reserve.
            val funding = amountLamports + rentReserve

            val balance = balanceRepository.getTokenValue(coin.address, coin).first().value
            require(funding <= balance) { "Insufficient balance for this stake + rent reserve" }

            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = coin)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = coin.address,
                    token = coin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )

            val payload = SolanaStakingPayload.delegate(votePubkey = votePubkey, lamports = funding)
            val keysignPayload =
                buildKeysignPayload(
                    coin = coin,
                    payload = payload,
                    blockChainSpecific = specific.blockChainSpecific,
                    balanceLamports = balance,
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
                    srcTokenValue = TokenValue(value = funding, token = coin),
                    memo = "",
                    dstAddress = votePubkey,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    solanaStakingPayload = payload,
                    signSolana = keysignPayload.signSolana,
                )
            depositTransactionRepository.addTransaction(depositTx)

            amountFieldState.clearText()
            _state.update { it.copy(isSubmitting = false) }
            navigator.route(
                Route.VerifyDeposit(vaultId = route.vaultId, transactionId = depositTx.id)
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }

    private fun shortAddress(address: String): String =
        if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address
}
