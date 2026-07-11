package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadataProvider
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
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
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * View-model for the Solana move-stake step 2 ("Finish Move") screen. Reached once the moved stake
 * account has fully cooled down (Inactive). Shows the source account read-only and a validator
 * picker for the destination; Continue re-delegates the existing account to the chosen validator
 * via [SolanaStakingPayload.finishMove] (a delegate instruction that targets the existing account
 * rather than creating a fresh one). Mirrors Windows `SolanaFinishMoveSpecific`.
 */
@HiltViewModel
internal class SolanaFinishMoveViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val solanaStakingService: SolanaStakingService,
    private val validatorMetadataProvider: ValidatorMetadataProvider,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.SolanaFinishMove>()

    private val _state =
        MutableStateFlow(SolanaFinishMoveUiState(stakePubkey = shortAddress(route.stakePubkey)))
    val state: StateFlow<SolanaFinishMoveUiState> = _state.asStateFlow()

    private var coin: Coin? = null
    private val stakeFormat = DecimalFormat("#,###")

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

    fun visibleValidators(state: SolanaFinishMoveUiState): List<SolanaValidatorOption> {
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
                Timber.e(e, "Failed to load Solana finish-move data")
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
                        logoUrl = md?.logoUrl,
                        activatedStakeDisplay =
                            "${stakeFormat.format(v.activatedStake.toBigDecimal().movePointLeft(solCoin.decimal).toBigInteger())} ${solCoin.ticker}",
                        commissionDisplay = "${v.commission}%",
                        apyDisplay =
                            md?.apyEstimate?.let {
                                it.multiply(BigDecimal(100))
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .toPlainString() + "%"
                            },
                    )
                }
            _state.update { it.copy(validators = options, isLoading = false) }
        }
    }

    fun submit() {
        if (_state.value.isSubmitting) return
        val votePubkey = _state.value.selectedValidator?.votePubkey ?: return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Solana finish-move submit failed")
                _state.update {
                    it.copy(isSubmitting = false, error = (e.message ?: "").asUiText())
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val solCoin = coin ?: error("SOL not in this vault")
            val lamports = route.lamports.toBigIntegerOrNull() ?: error("Missing move amount")

            val payload =
                SolanaStakingPayload.finishMove(
                    stakeAccount = route.stakePubkey,
                    votePubkey = votePubkey,
                    lamports = lamports,
                )
            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = solCoin)
            val balance = balanceRepository.getTokenValue(solCoin.address, solCoin).first().value
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
                    balanceLamports = balance,
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
                    srcTokenValue = TokenValue(value = lamports, token = solCoin),
                    memo = "",
                    dstAddress = votePubkey,
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
internal data class SolanaFinishMoveUiState(
    val stakePubkey: String = "",
    val isLoading: Boolean = true,
    val validators: List<SolanaValidatorOption> = emptyList(),
    val selectedValidator: SolanaValidatorOption? = null,
    val validatorSearchQuery: String = "",
    val isShowingPicker: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)
