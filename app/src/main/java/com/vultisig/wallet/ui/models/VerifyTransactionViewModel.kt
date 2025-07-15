package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.SecurityScannerTransaction
import com.vultisig.wallet.data.securityscanner.SecurityTransactionType
import com.vultisig.wallet.data.securityscanner.isChainSupported
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.Base58
import java.math.BigInteger
import javax.inject.Inject

@Immutable
internal data class SendTxUiModel(
    val token: ValuedToken = ValuedToken.Empty,

    val networkFeeFiatValue: String = "",
    val networkFeeTokenValue: String = "",

    val srcAddress: String = "",
    val dstAddress: String = "",

    val memo: String? = null,
)

@Immutable
internal data class VerifyTransactionUiModel(
    val transaction: SendTxUiModel = SendTxUiModel(),
    val consentAddress: Boolean = false,
    val consentAmount: Boolean = false,
    val consentDst: Boolean = false,
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
    val functionSignature: String? = null,
    val functionInputs: String? = null,
    val txScanStatus: TransactionScanStatus = TransactionScanStatus.NotStarted,
    val showScanningWarning: Boolean = false,
) {
    val hasAllConsents: Boolean
        get() = consentAddress && consentAmount && consentDst
}

sealed class TransactionScanStatus {
    data object NotStarted : TransactionScanStatus()
    data object Scanning : TransactionScanStatus()
    data class Scanned(val result: SecurityScannerResult) : TransactionScanStatus()
    data class Error(val message: String, val provider: String) : TransactionScanStatus()
}

@HiltViewModel
internal class VerifyTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,

    transactionRepository: TransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val securityScannerService: SecurityScannerContract,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VerifySend>()

    private val transactionId: TransactionId = args.transactionId
    private val vaultId: String = args.vaultId

    private val transaction = transactionRepository.getTransaction(transactionId)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val uiState = MutableStateFlow(VerifyTransactionUiModel())
    private val password = MutableStateFlow<String?>(null)

    private val _fastSignFlow = Channel<Boolean>()
    val fastSignFlow = _fastSignFlow.receiveAsFlow()

    private var _fastSign = false

    init {
        loadFastSign()
        loadTransaction()
        loadPassword()
        scanTransaction()
    }

    fun checkConsentAddress(checked: Boolean) {
        viewModelScope.launch {
            uiState.update { it.copy(consentAddress = checked) }
        }
    }

    fun checkConsentAmount(checked: Boolean) {
        viewModelScope.launch {
            uiState.update { it.copy(consentAmount = checked) }
        }
    }

    fun checkConsentDst(checked: Boolean) {
        viewModelScope.launch {
            uiState.update { it.copy(consentDst = checked) }
        }
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

    private fun handleSigningFlow(
        onSign: () -> Unit,
        onSignAndSkipWarnings: () -> Unit
    ) {
        when (val status = uiState.value.txScanStatus) {
            is TransactionScanStatus.Scanned -> {
                if (!status.result.isSecure) {
                    uiState.update { it.copy(showScanningWarning = true) }
                } else {
                    onSignAndSkipWarnings()
                }
            }
            is TransactionScanStatus.Error,
            TransactionScanStatus.NotStarted,
            TransactionScanStatus.Scanning -> onSign()
        }
    }

    fun joinKeySign() {
        _fastSign = false
        handleSigningFlow(
            onSign = { keysign(KeysignInitType.QR_CODE) },
            onSignAndSkipWarnings = { keysign(KeysignInitType.QR_CODE) }
        )
    }

    private fun joinKeySignAndSkipWarnings() {
        uiState.update { it.copy(showScanningWarning = false) }
        keysign(KeysignInitType.QR_CODE)
    }

    fun fastSign() {
        _fastSign = true
        handleSigningFlow(
            onSign = { fastSignAndSkipWarnings() },
            onSignAndSkipWarnings = { fastSignAndSkipWarnings() }
        )
    }

    private fun fastSignAndSkipWarnings() {
        uiState.update { it.copy(showScanningWarning = false) }

        if (!tryToFastSignWithPassword()) {
            viewModelScope.launch {
                _fastSignFlow.send(true)
            }
        }
    }

    fun onConfirmScanning() {
        if (!_fastSign) {
            joinKeySignAndSkipWarnings()
        } else {
            fastSignAndSkipWarnings()
        }
    }

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    fun dismissScanningWarning() {
        uiState.update { it.copy(showScanningWarning = false) }
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }

    private fun keysign(
        keysignInitType: KeysignInitType,
    ) {
        if (uiState.value.hasAllConsents) {
            viewModelScope.launch {
                launchKeysign(
                    keysignInitType, transactionId, password.value,
                    Route.Keysign.Keysign.TxType.Send,
                    vaultId
                )
            }
        } else {
            uiState.update {
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
            uiState.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            val transaction = transaction.filterNotNull().first()

            val transactionUiModel = mapTransactionToUiModel(transaction)

            uiState.update {
                it.copy(transaction = transactionUiModel)
            }
        }
    }

    private fun scanTransaction() {
        viewModelScope.launch {
            try {
                val transaction = transaction.filterNotNull().firstOrNull() ?: return@launch
                val chain = transaction.token.chain

                val isSupported = securityScannerService
                    .getSupportedChainsByFeature()
                    .isChainSupported(chain) && securityScannerService.isSecurityServiceEnabled()

                if (!isSupported) return@launch

                uiState.update {
                    it.copy(txScanStatus = TransactionScanStatus.Scanning)
                }

                val securityScannerTransaction =
                    securityScannerService.createSecurityScannerTransaction(transaction)

                val result = withContext(Dispatchers.IO) {
                    securityScannerService.scanTransaction(securityScannerTransaction)
                }

                uiState.update {
                    it.copy(
                        txScanStatus = TransactionScanStatus.Scanned(result)
                    )
                }
            } catch (t: Throwable) {
                val errorMessage = "Security Scanner Failed"
                Timber.e(t, errorMessage)

                uiState.update {
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
}


