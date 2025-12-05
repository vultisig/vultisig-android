package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal data class SwapTransactionUiModel(
    val src: ValuedToken = ValuedToken.Empty,
    val dst: ValuedToken = ValuedToken.Empty,

    val networkFee: ValuedToken = ValuedToken.Empty,
    val providerFee: ValuedToken = ValuedToken.Empty,

    val totalFee: String = "",
    val networkFeeFormatted: String = "",
    val providerFeeFormatted: String = "",

    val hasConsentAllowance: Boolean = false,
)

internal data class ValuedToken(
    val token: Coin,
    val value: String, // value as string e.g. 1.0
    val fiatValue: String, // e.g. $100
) {
    companion object {
        val Empty = ValuedToken(
            token = Coins.Base.WEWE,
            value = "0",
            fiatValue = "0",
        )
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
) {
    val hasAllConsents: Boolean
        get() = consentAmount && consentReceiveAmount && (consentAllowance || !tx.hasConsentAllowance)
}

@HiltViewModel
internal class VerifySwapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: SwapTransactionToUiModelMapper,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val securityScannerService: SecurityScannerContract,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    val state = MutableStateFlow(VerifySwapUiModel())
    private val password = MutableStateFlow<String?>(null)
    private val args = savedStateHandle.toRoute<Route.VerifySwap>()
    private val vaultId: VaultId = args.vaultId
    private val transactionId: String = args.transactionId
    private var _fastSign = false

    private val _fastSignFlow = Channel<Boolean>()
    val fastSignFlow = _fastSignFlow.receiveAsFlow()

    init {
        viewModelScope.launch {
            val transaction = runCatching {
                swapTransactionRepository.getTransaction(transactionId)
            }.getOrElse {
                navigator.back()
                return@launch
            }
            val vaultName = vaultRepository.get(vaultId)?.name
            if (vaultName == null) {
                state.update {
                    it.copy(
                        errorText = UiText.StringResource(
                            R.string.swap_screen_invalid_vault
                        )
                    )
                }
            }

            val consentAllowance = !transaction.isApprovalRequired
            state.update {
                it.copy(
                    consentAllowance = consentAllowance,
                    tx = mapTransactionToUiModel(transaction),
                    vaultName = vaultName?.takeIf { name -> name.isNotEmpty() } ?: "Main Vault",
                )
            }
            scanTransaction(transaction)
        }
        loadFastSign()
        loadPassword()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
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

    private fun keysign(
        keysignInitType: KeysignInitType,
    ) {
        val hasAllConsents = state.value.let {
            it.consentReceiveAmount && it.consentAmount && it.consentAllowance
        }

        if (hasAllConsents) {
            viewModelScope.launch {
                launchKeysign(
                    keysignInitType,
                    transactionId,
                    password.value,
                    Route.Keysign.Keysign.TxType.Swap,
                    vaultId
                )
            }
        } else {
            state.update {
                it.copy(
                    errorText = UiText.StringResource(
                        R.string.verify_transaction_error_not_enough_consent
                    )
                )
            }
        }
    }

    private fun loadPassword() {
        viewModelScope.launch {
            password.value = vaultPasswordRepository.getPassword(vaultId)
        }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            val hasFastSign = isVaultHasFastSignById(vaultId)
            state.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }

    private fun scanTransaction(transaction: SwapTransaction) {
        viewModelScope.launch {
            try {
                val chain = transaction.srcToken.chain
                val isThorchainOrMaya =
                    transaction.payload is SwapPayload.ThorChain || transaction.payload is SwapPayload.MayaChain

                val isSupported = !isThorchainOrMaya
                        && chain.standard == TokenStandard.EVM
                        && securityScannerService.getSupportedChainsByFeature()
                    .isChainSupported(chain)
                        && securityScannerService.isSecurityServiceEnabled()

                if (!isSupported) return@launch

                state.update {
                    it.copy(txScanStatus = TransactionScanStatus.Scanning)
                }

                val securityScannerTransaction =
                    securityScannerService.createSecurityScannerTransaction(transaction)

                val result = withContext(Dispatchers.IO) {
                    securityScannerService.scanTransaction(securityScannerTransaction)
                }

                state.update {
                    it.copy(
                        txScanStatus = TransactionScanStatus.Scanned(result)
                    )
                }
            } catch (t: Throwable) {
                val errorMessage = "Security Scanner Failed"
                Timber.e(
                    t,
                    errorMessage
                )

                state.update {
                    val message = t.message ?: errorMessage
                    it.copy(
                        txScanStatus = TransactionScanStatus.Error(
                            message = message,
                            provider = BLOCKAID_PROVIDER,
                        )
                    )
                }
            }
        }
    }

    fun onDismissSecurityScanner() {
        state.update {
            it.copy(
                showScanningWarning = false,
            )
        }
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
            viewModelScope.launch {
                _fastSignFlow.send(true)
            }
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