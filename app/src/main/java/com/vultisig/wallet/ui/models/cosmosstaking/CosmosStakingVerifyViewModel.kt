package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

/** A single validator row in the verify summary — label + resolved "moniker (N% commission)". */
internal data class CosmosStakingVerifyValidatorRow(val labelRes: Int, val value: String)

internal data class CosmosStakingVerifyUiState(
    val headlineRes: Int = R.string.cosmos_staking_youre_staking,
    val amount: String = "",
    val ticker: String = "",
    val coinLogo: String = "",
    val vaultName: String = "",
    val fromAddress: String = "",
    val validatorRows: List<CosmosStakingVerifyValidatorRow> = emptyList(),
    val networkName: String = "",
    val feeCrypto: String = "",
    val feeFiat: String = "",
    val hasFastSign: Boolean = false,
    val isLoading: Boolean = true,
    val errorText: UiText? = null,
)

/**
 * Backs the staking-specific verify summary. Loads the persisted [DepositTransaction], resolves the
 * validator moniker + commission via [CosmosStakingService] (cached, shared with the picker), and
 * launches keysign through the same [LaunchKeysignUseCase] / `TxType.Deposit` path as the generic
 * verify-deposit screen (the tx already carries the `signDirect` bytes).
 *
 * Port of iOS `CosmosStakingVerifySummaryView.swift` (vultisig-ios PR #4432).
 */
@HiltViewModel
internal class CosmosStakingVerifyViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val vaultRepository: VaultRepository,
    private val cosmosStakingService: CosmosStakingService,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val launchKeysign: LaunchKeysignUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.CosmosStakingVerify>()
    private val transactionId = route.transactionId
    private val vaultId = route.vaultId

    private val _state = MutableStateFlow(CosmosStakingVerifyUiState())
    val state: StateFlow<CosmosStakingVerifyUiState> = _state.asStateFlow()

    private var password: String? = null

    init {
        load()
        loadFastSign()
        loadPassword()
    }

    private fun load() {
        viewModelScope.safeLaunch(
            onError = { t ->
                Timber.e(t, "Failed to load staking verify summary")
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorText = UiText.StringResource(R.string.try_again),
                    )
                }
            }
        ) {
            val tx =
                withContext(ioDispatcher) {
                    depositTransactionRepository.getTransaction(transactionId)
                }
            val vault = withContext(ioDispatcher) { vaultRepository.get(vaultId) }
            val coin = tx.srcToken
            val payload = tx.cosmosStakingPayload

            val headlineRes =
                when (payload) {
                    is CosmosStakingPayload.Delegate -> R.string.cosmos_staking_youre_staking
                    is CosmosStakingPayload.Undelegate -> R.string.cosmos_staking_youre_unstaking
                    is CosmosStakingPayload.Redelegate -> R.string.cosmos_staking_youre_moving
                    is CosmosStakingPayload.WithdrawRewards ->
                        R.string.cosmos_staking_youre_claiming
                    null -> R.string.cosmos_staking_youre_staking
                }

            val amount =
                BigDecimal(tx.srcTokenValue.value)
                    .movePointLeft(coin.decimal)
                    .stripTrailingZeros()
                    .toPlainString()
            val feeCrypto =
                "${BigDecimal(tx.estimatedFees.value).movePointLeft(coin.decimal).stripTrailingZeros().toPlainString()} ${coin.ticker}"

            // Render with truncated valopers first, then enrich with monikers + commission.
            _state.update {
                it.copy(
                    headlineRes = headlineRes,
                    amount = amount,
                    ticker = coin.ticker,
                    coinLogo = coin.logo,
                    vaultName = vault?.name.orEmpty(),
                    fromAddress = tx.srcAddress,
                    validatorRows = buildValidatorRows(payload, emptyMap()),
                    networkName = coin.chain.raw,
                    feeCrypto = feeCrypto,
                    feeFiat = tx.estimateFeesFiat,
                    isLoading = false,
                )
            }

            val byAddress =
                withContext(ioDispatcher) {
                    runCatching { cosmosStakingService.fetchValidators(coin.chain) }
                        .getOrDefault(emptyList())
                        .associateBy { it.operatorAddress }
                }
            if (byAddress.isNotEmpty()) {
                _state.update { it.copy(validatorRows = buildValidatorRows(payload, byAddress)) }
            }
        }
    }

    private fun buildValidatorRows(
        payload: CosmosStakingPayload?,
        byAddress: Map<String, CosmosValidator>,
    ): List<CosmosStakingVerifyValidatorRow> =
        when (payload) {
            is CosmosStakingPayload.Delegate ->
                listOf(
                    CosmosStakingVerifyValidatorRow(
                        R.string.cosmos_staking_validator_picker,
                        resolve(payload.validatorAddress, byAddress),
                    )
                )
            is CosmosStakingPayload.Undelegate ->
                listOf(
                    CosmosStakingVerifyValidatorRow(
                        R.string.cosmos_staking_validator_picker,
                        resolve(payload.validatorAddress, byAddress),
                    )
                )
            is CosmosStakingPayload.Redelegate ->
                listOf(
                    CosmosStakingVerifyValidatorRow(
                        R.string.cosmos_staking_source_validator,
                        resolve(payload.validatorSrcAddress, byAddress),
                    ),
                    CosmosStakingVerifyValidatorRow(
                        R.string.cosmos_staking_destination_validator,
                        resolve(payload.validatorDstAddress, byAddress),
                    ),
                )
            is CosmosStakingPayload.WithdrawRewards ->
                when (payload.validators.size) {
                    0 -> emptyList()
                    1 ->
                        listOf(
                            CosmosStakingVerifyValidatorRow(
                                R.string.cosmos_staking_validator_picker,
                                resolve(payload.validators.first(), byAddress),
                            )
                        )
                    // A batched claim signs N withdraw msgs in one ceremony — show the validator
                    // count so the user isn't signing a multi-msg batch blind (iOS renders a
                    // "Validators: N" count row rather than dropping the row entirely).
                    else ->
                        listOf(
                            CosmosStakingVerifyValidatorRow(
                                R.string.cosmos_staking_validators,
                                payload.validators.size.toString(),
                            )
                        )
                }
            null -> emptyList()
        }

    /** "moniker (N% commission)" when resolved, truncated valoper otherwise. */
    private fun resolve(valoper: String, byAddress: Map<String, CosmosValidator>): String {
        val v = byAddress[valoper] ?: return truncated(valoper)
        val display = v.moniker.ifEmpty { truncated(valoper) }
        val pct = v.commission.movePointRight(2).stripTrailingZeros().toPlainString()
        return "$display ($pct% commission)"
    }

    private fun truncated(value: String): String =
        if (value.length > 14) "${value.substring(0, 8)}…${value.substring(value.length - 4)}"
        else value

    fun confirm() = keysign(KeysignInitType.QR_CODE)

    fun authFastSign() = keysign(KeysignInitType.BIOMETRY)

    fun tryToFastSignWithPassword(): Boolean {
        return if (password != null) {
            false
        } else {
            keysign(KeysignInitType.PASSWORD)
            true
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorText = null) }
    }

    private fun keysign(initType: KeysignInitType) {
        viewModelScope.safeLaunch(
            onError = { e -> _state.update { it.copy(errorText = (e.message ?: "").asUiText()) } }
        ) {
            launchKeysign(
                initType,
                transactionId,
                password,
                Route.Keysign.Keysign.TxType.Deposit,
                vaultId,
            )
        }
    }

    private fun loadPassword() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "load password failed") }) {
            password = withContext(ioDispatcher) { vaultPasswordRepository.getPassword(vaultId) }
        }
    }

    private fun loadFastSign() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "load fast sign failed") }) {
            val hasFastSign = withContext(ioDispatcher) { isVaultHasFastSignById(vaultId) }
            _state.update { it.copy(hasFastSign = hasFastSign) }
        }
    }
}
