package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
internal data class SendTxUiModel(
    val token: ValuedToken = ValuedToken.Empty,

    val networkFee: ValuedToken = ValuedToken.Empty,

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
) {
    val hasAllConsents: Boolean
        get() = consentAddress && consentAmount && consentDst
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

    init {
        loadFastSign()
        loadTransaction()
        loadPassword()
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

    fun tryToFastSignWithPassword(): Boolean {
        if (password.value != null) {
            return false
        } else {
            keysign(KeysignInitType.PASSWORD)
            return true
        }
    }

    fun joinKeysign() {
        keysign(KeysignInitType.QR_CODE)
    }

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
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

}


