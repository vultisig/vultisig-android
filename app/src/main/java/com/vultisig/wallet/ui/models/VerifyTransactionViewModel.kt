package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.isChainSupported
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
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
import vultisig.keysign.v1.SignAmino
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
    val signAmino: String? = null,
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
    val isLoadingFees: Boolean = false,
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

    private val transactionRepository: TransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val securityScannerService: SecurityScannerContract,
    private val vaultRepository: VaultRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCase,
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VerifySend>()

    private val transactionId: TransactionId = args.transactionId
    private val vaultId: String = args.vaultId

    private lateinit var transaction: Transaction

    val uiState = MutableStateFlow(VerifyTransactionUiModel())
    private val password = MutableStateFlow<String?>(null)

    private val _fastSignFlow = Channel<Boolean>()
    val fastSignFlow = _fastSignFlow.receiveAsFlow()

    private var _fastSign = false

    init {
        loadFastSign()
        loadTransaction()
        loadPassword()
    }

    private suspend fun calculateFees(transactionUiModel: SendTxUiModel) {
        val tx = transaction
        val chain = tx.token.chain
        val vault = withContext(Dispatchers.IO) {
            vaultRepository.get(vaultId)
        } ?: return

        val blockchainTransaction = Transfer(
            coin = tx.token,
            vault = VaultData(
                vaultHexChainCode = vault.hexChainCode,
                vaultHexPublicKey = vault.getPubKeyByChain(chain),
            ),
            amount = tx.tokenValue.value,
            to = tx.dstAddress,
            memo = tx.memo,

            isMax = false,
        )

        try {
            val fees = withContext(Dispatchers.IO) {
                feeServiceComposite.calculateFees(blockchainTransaction)
            }
            val nativeCoin = withContext(Dispatchers.IO) {
                tokenRepository.getNativeToken(chain.id)
            }
            val fromGas = GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee = TokenValue(
                    value = fees.amount,
                    token = nativeCoin,
                ),
                selectedToken = tx.token,
            )
            val uiFeeModel = gasFeeToEstimate(fromGas)
            val updateTx = transactionUiModel.copy(
                networkFeeTokenValue = uiFeeModel.formattedTokenValue,
                networkFeeFiatValue = uiFeeModel.formattedFiatValue,
            )

            uiState.update {
                it.copy(
                    isLoadingFees = false,
                    transaction = updateTx
                )
            }
        } catch (t: Throwable) {
            Timber.e(
                t,
                "Error calculating fees"
            )

            uiState.update {
                it.copy(isLoadingFees = false)
            }
        }
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

    fun joinKeySign() {
        _fastSign = false
        handleSigningFlowCommon(
            txScanStatus = uiState.value.txScanStatus,
            showWarning = { uiState.update { it.copy(showScanningWarning = true) } },
            onSign = { keysign(KeysignInitType.QR_CODE) },
        )
    }

    private fun joinKeySignAndSkipWarnings() {
        uiState.update { it.copy(showScanningWarning = false) }
        keysign(KeysignInitType.QR_CODE)
    }

    fun fastSign() {
        _fastSign = true
        handleSigningFlowCommon(
            txScanStatus = uiState.value.txScanStatus,
            showWarning = { uiState.update { it.copy(showScanningWarning = true) } },
            onSign = { fastSignAndSkipWarnings() },
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
            transaction = runCatching {
                transactionRepository.getTransaction(transactionId)
            }.getOrElse {
                Timber.e(
                    it,
                    "Failed to load transaction"
                )
                navigator.back()
                return@launch
            }
            val transactionUiModel = mapTransactionToUiModel(transaction)

            uiState.update {
                it.copy(transaction = transactionUiModel, isLoadingFees = true)
            }

            calculateFees(transactionUiModel)
            scanTransaction()
        }
    }

    private suspend fun scanTransaction() {
        try {
            val transaction = transaction
            val chain = transaction.token.chain

            val isSupported = securityScannerService
                .getSupportedChainsByFeature()
                .isChainSupported(chain) && securityScannerService.isSecurityServiceEnabled()

            if (!isSupported) return

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
            val errorMessage = "Security scan failed ${t.message}"
            Timber.e(
                t,
                errorMessage
            )

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


