package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.isChainSupported
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.handleSigningFlowCommon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class SwapTransactionUiModel(
    val src: ValuedToken = ValuedToken.Empty,
    val dst: ValuedToken = ValuedToken.Empty,
    val networkFee: ValuedToken = ValuedToken.Empty,
    val providerFee: ValuedToken = ValuedToken.Empty,
    // Outbound-fee portion of the fee breakdown, already formatted as fiat. Non-null only for
    // THORChain / MayaChain swaps; rendered as its own row so the overview reconciles to the swap
    // form's breakdown instead of folding the outbound fee into "Swap Fee" (#5061).
    val outboundFee: String? = null,
    val totalFee: String = "",
    val networkFeeFormatted: String = "",
    val providerFeeFormatted: String = "",
    val hasConsentAllowance: Boolean = false,
    // Canonical provider id (e.g. `SwapKit`). Behavioral key: copied onto the tx-history row and
    // matched against `SwapProvider.SWAPKIT.getSwapProviderId()` to gate SwapKit `/track`
    // settlement — never a human label. Use [providerLabel] for display.
    val provider: String = "",
    // Display-only provider label (e.g. `SwapKit (NEAR)`). Falls back to [provider] when blank.
    // Kept separate from [provider] so the sub-provider shown in the UI never leaks into the
    // gating key.
    val providerLabel: String = "",
    // SwapKit `/v3/swap` swap id — correlation key persisted on the tx-history row so a cross-chain
    // SwapKit swap's Success can be gated on the destination-leg `/track` settlement. Null for
    // non-SwapKit providers and for SwapKit rows rebuilt on a cosigning peer without it.
    val swapId: String? = null,
    // Expected destination output as a raw, machine-parseable plain decimal (e.g. `12.5`), unlike
    // [dst]'s display-formatted value. Persisted onto the tx-history row as the native-destination
    // fill threshold when resolving a TON (Omniston) swap's settlement on-chain.
    val expectedDstDecimal: String = "",
    // External recipient the swap output is routed to, or null when it goes to the vault's own
    // address. Shown on the verify screen so the destination is never a silent default (#4858).
    // For native THORChain/MayaChain swaps this is resolved from the signed memo's destination
    // segment rather than form state, so it reflects what's actually signed (#4972).
    val externalRecipient: String? = null,
    // Display name of the vault the source funds leave from, or null when unresolved. Rendered on
    // the transaction-complete screen as `VaultName (Address)` so the source account is
    // identifiable rather than a bare address (#5333).
    val srcVaultName: String? = null,
    // Display name of the local vault that owns the destination address, or null when the
    // destination is external / unresolved (falls back to the bare address). Rendered as
    // `VaultName (Address)` on the transaction-complete screen (#5333).
    val dstVaultName: String? = null,
)

internal data class ValuedToken(
    val token: Coin,
    val value: String, // value as string e.g. 1.0
    val fiatValue: String, // e.g. $100
) {
    companion object {
        val Empty = ValuedToken(token = Coins.Base.OM, value = "0", fiatValue = "0")
    }
}

internal data class VerifySwapUiModel(
    val tx: SwapTransactionUiModel = SwapTransactionUiModel(),
    val consentAmount: Boolean = false,
    val consentReceiveAmount: Boolean = false,
    val consentAllowance: Boolean = false,
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
    val txScanStatus: TransactionScanStatus = TransactionScanStatus.NotStarted,
    val showScanningWarning: Boolean = false,
    val vaultName: String = "",
    val isSigning: Boolean = false,
) {
    val hasAllConsents: Boolean
        get() =
            consentAmount && consentReceiveAmount && (consentAllowance || !tx.hasConsentAllowance)
}

@HiltViewModel
internal class VerifySwapViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: SwapTransactionToUiModelMapper,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val securityScannerService: SecurityScannerContract,
    private val vaultRepository: VaultRepository,
    private val inboundHaltPreflight: SwapInboundHaltPreflight,
) : ViewModel() {

    val state = MutableStateFlow(VerifySwapUiModel())
    private val password = MutableStateFlow<String?>(null)
    private val args = savedStateHandle.toRoute<Route.VerifySwap>()
    private val vaultId: VaultId = args.vaultId
    private val transactionId: String = args.transactionId
    private var _fastSign = false
    private var transaction: SwapTransaction? = null

    private val _fastSignFlow = Channel<Boolean>()
    val fastSignFlow = _fastSignFlow.receiveAsFlow()

    init {
        viewModelScope.launch {
            val transaction =
                runCatching { swapTransactionRepository.getTransaction(transactionId) }
                    .getOrElse {
                        navigator.back()
                        return@launch
                    }
            this@VerifySwapViewModel.transaction = transaction
            val vault = vaultRepository.get(vaultId)
            val vaultName = vault?.name
            if (vaultName == null) {
                state.update {
                    it.copy(errorText = UiText.StringResource(R.string.swap_screen_invalid_vault))
                }
            }

            // Recipient parsed from the signed THORChain/MayaChain memo (#4972). Null for swaps
            // whose memo can't carry one (EVM aggregators sign calldata, not a memo) — those fall
            // back to the form-state recipient already mapped onto the UI model (#4858).
            val memoExternalRecipient =
                resolveExternalSwapRecipient(
                    memo = transaction.memo,
                    destinationChain = transaction.dstToken.chain,
                    vaultDestinationAddress =
                        vault
                            ?.coins
                            ?.firstOrNull { it.chain == transaction.dstToken.chain }
                            ?.address
                            .orEmpty(),
                )

            val mappedTx = mapTransactionToUiModel(transaction)
            val consentAllowance = !transaction.isApprovalRequired
            state.update {
                it.copy(
                    consentAllowance = consentAllowance,
                    tx =
                        mappedTx.copy(
                            externalRecipient = memoExternalRecipient ?: mappedTx.externalRecipient
                        ),
                    vaultName = vaultName?.takeIf { name -> name.isNotEmpty() } ?: "Main Vault",
                )
            }
            scanTransaction(transaction)
        }
        loadFastSign()
        loadPassword()
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun consentReceiveAmount(consent: Boolean) {
        state.update { it.copy(consentReceiveAmount = consent) }
    }

    fun consentAmount(consent: Boolean) {
        state.update { it.copy(consentAmount = consent) }
    }

    fun consentAllowance(consent: Boolean) {
        state.update { it.copy(consentAllowance = consent) }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    private fun keysign(keysignInitType: KeysignInitType) {
        if (!state.value.hasAllConsents) {
            state.update {
                it.copy(
                    errorText =
                        UiText.StringResource(R.string.verify_transaction_error_not_enough_consent)
                )
            }
            return
        }
        if (state.value.isSigning) return

        val transaction = transaction ?: return
        state.update { it.copy(isSigning = true) }
        viewModelScope.safeLaunch(
            onError = { e ->
                // Only the preflight fails closed as a halt. A keysign/navigation failure must not
                // masquerade as "trading halted", so it surfaces a generic error instead.
                val errorMessage =
                    if (e is SwapException.TradingHalted) {
                        Timber.w(e, "Swap sign-time inbound preflight blocked signing")
                        R.string.swap_error_trading_halted
                    } else {
                        Timber.e(e, "Swap keysign launch failed")
                        R.string.error_view_default_title
                    }
                state.update { it.copy(errorText = UiText.StringResource(errorMessage)) }
            }
        ) {
            try {
                inboundHaltPreflight.assertSourceChainNotHalted(transaction)
                launchKeysign(
                    keysignInitType,
                    transactionId,
                    password.value,
                    Route.Keysign.Keysign.TxType.Swap,
                    vaultId,
                )
            } finally {
                // The verify ViewModel can remain alive when navigation is cancelled or fails.
                // Always re-enable signing so a later attempt cannot be permanently dead-ended.
                state.update { it.copy(isSigning = false) }
            }
        }
    }

    private fun loadPassword() {
        viewModelScope.launch { password.value = vaultPasswordRepository.getPassword(vaultId) }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            val hasFastSign = isVaultHasFastSignById(vaultId)
            state.update { it.copy(hasFastSign = hasFastSign) }
        }
    }

    private fun scanTransaction(transaction: SwapTransaction) {
        viewModelScope.launch {
            try {
                val chain = transaction.srcToken.chain
                val isThorchainOrMaya =
                    transaction.payload is SwapPayload.ThorChain ||
                        transaction.payload is SwapPayload.MayaChain

                val isSupported =
                    !isThorchainOrMaya &&
                        chain.standard == TokenStandard.EVM &&
                        securityScannerService
                            .getSupportedChainsByFeature()
                            .isChainSupported(chain) &&
                        securityScannerService.isSecurityServiceEnabled()

                if (!isSupported) return@launch

                state.update { it.copy(txScanStatus = TransactionScanStatus.Scanning) }

                val securityScannerTransaction =
                    securityScannerService.createSecurityScannerTransaction(transaction)

                val result =
                    withContext(Dispatchers.IO) {
                        securityScannerService.scanTransaction(securityScannerTransaction)
                    }

                state.update { it.copy(txScanStatus = TransactionScanStatus.Scanned(result)) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                val errorMessage = "Security Scanner Failed"
                Timber.e(t, errorMessage)

                state.update {
                    val message = t.message ?: errorMessage
                    it.copy(
                        txScanStatus =
                            TransactionScanStatus.Error(
                                message = message,
                                provider = BLOCKAID_PROVIDER,
                            )
                    )
                }
            }
        }
    }

    fun onDismissSecurityScanner() {
        state.update { it.copy(showScanningWarning = false) }
    }

    fun joinKeySign() {
        _fastSign = false
        handleSigningFlowCommon(
            txScanStatus = state.value.txScanStatus,
            showWarning = { state.update { it.copy(showScanningWarning = true) } },
            onSign = { keysign(KeysignInitType.QR_CODE) },
        )
    }

    fun authFastSign() {
        keysign(KeysignInitType.BIOMETRY)
    }

    private fun tryToFastSignWithPassword(): Boolean {
        if (password.value != null) {
            return false
        } else {
            keysign(KeysignInitType.PASSWORD)
            return true
        }
    }

    fun fastSign() {
        _fastSign = true
        handleSigningFlowCommon(
            txScanStatus = state.value.txScanStatus,
            showWarning = { state.update { it.copy(showScanningWarning = true) } },
            onSign = { fastSignAndSkipWarnings() },
        )
    }

    private fun fastSignAndSkipWarnings() {
        state.update { it.copy(showScanningWarning = false) }

        if (!tryToFastSignWithPassword()) {
            viewModelScope.launch { _fastSignFlow.send(true) }
        }
    }

    fun joinKeySignAndSkipWarnings() {
        state.update { it.copy(showScanningWarning = false) }
        keysign(KeysignInitType.QR_CODE)
    }

    fun onConfirmScanning() {
        if (!_fastSign) {
            joinKeySignAndSkipWarnings()
        } else {
            fastSignAndSkipWarnings()
        }
    }
}
