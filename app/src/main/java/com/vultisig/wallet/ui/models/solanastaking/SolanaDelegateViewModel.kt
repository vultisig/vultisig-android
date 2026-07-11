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
import com.vultisig.wallet.data.models.Coin
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
import java.math.BigInteger
import java.math.RoundingMode
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
    val logoUrl: String?,
    /** Total activated stake in whole SOL with grouping, e.g. `"16,179,362 SOL"`. */
    val activatedStakeDisplay: String,
    /** Commission the validator takes, e.g. `"7%"`. */
    val commissionDisplay: String,
    val apyDisplay: String?,
)

@Immutable
internal data class SolanaDelegateUiState(
    val ticker: String = "SOL",
    val validators: List<SolanaValidatorOption> = emptyList(),
    val selectedValidator: SolanaValidatorOption? = null,
    /** Stakeable balance (human SOL) — total balance minus rent-exempt reserve + a fee buffer. */
    val stakeableBalance: BigDecimal = BigDecimal.ZERO,
    val percentageSelected: Int = -1,
    val isShowingPicker: Boolean = false,
    val validatorSearchQuery: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)

/**
 * Delegate (stake) flow for Solana native staking: enter an amount (with 25/50/75/Max chips + the
 * live stakeable balance) and pick a validator, then build a create-and-delegate transaction in one
 * tx. Funding = entered amount + the live rent-exempt reserve (the active stake equals the entered
 * amount). The byte-parity `SignSolana` is built via [BuildSolanaStakingKeysignPayloadUseCase] and
 * carried on the [DepositTransaction] so the generic VerifyDeposit -> keysign flow signs it.
 * Mirrors the iOS delegate flow (vultisig-ios #4661).
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

    private var coin: Coin? = null
    private var balanceLamports: BigInteger = BigInteger.ZERO
    private val stakeFormat = java.text.DecimalFormat("#,###")

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
        _state.update {
            it.copy(selectedValidator = validator, isShowingPicker = false, error = null)
        }
    }

    fun visibleValidators(state: SolanaDelegateUiState): List<SolanaValidatorOption> {
        val query = state.validatorSearchQuery.trim()
        if (query.isEmpty()) return state.validators
        return state.validators.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.votePubkey.contains(query, ignoreCase = true)
        }
    }

    /** 25/50/75/100% chip → fill the amount field from the stakeable balance. */
    fun onPercentageChange(percent: Int) {
        _state.update { it.copy(percentageSelected = percent) }
        val available = _state.value.stakeableBalance
        if (available <= BigDecimal.ZERO) return
        val amount =
            available
                .multiply(BigDecimal(percent))
                .divide(BigDecimal(100), 9, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        amountFieldState.edit { replace(0, length, amount) }
    }

    private fun load() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load Solana delegate data")
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

            balanceLamports =
                balanceRepository.getTokenValue(solCoin.address, solCoin).first().value
            // Stakeable = balance − rent-exempt reserve − a small fee buffer, so a Max stake stays
            // within balance once the rent reserve is added to the funding downstream.
            val headroom =
                SolanaStakingConfig.RENT_EXEMPT_RESERVE_FALLBACK_LAMPORTS +
                    SolanaHelper.DefaultFeeInLamports
            val stakeable =
                (balanceLamports - headroom)
                    .max(BigInteger.ZERO)
                    .toBigDecimal()
                    .movePointLeft(solCoin.decimal)

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
            _state.update {
                it.copy(
                    ticker = solCoin.ticker,
                    validators = options,
                    stakeableBalance = stakeable,
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
            val solCoin = coin ?: error("SOL not in this vault")

            val votePubkey =
                _state.value.selectedValidator?.votePubkey
                    ?: error("Select a validator to stake with")

            val amountSol =
                amountFieldState.text.toString().trim().toBigDecimalOrNull()
                    ?: error("Enter a valid amount")
            val amountLamports =
                amountSol.movePointRight(solCoin.decimal).toBigIntegerExact().also {
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

            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = solCoin)
            val balance = balanceRepository.getTokenValue(solCoin.address, solCoin).first().value
            require(funding + gasFee.value <= balance) {
                "Insufficient balance for this stake + rent reserve + network fee"
            }

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

            val payload = SolanaStakingPayload.delegate(votePubkey = votePubkey, lamports = funding)
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
                    srcTokenValue = TokenValue(value = funding, token = solCoin),
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
