package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.BlowfishRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.chainType
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.SendDst.Companion.ARG_TRANSACTION_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class TransactionUiModel(
    val srcAddress: String = "",
    val dstAddress: String = "",
    val memo: String? = null,
    val tokenValue: String = "",
    val fiatValue: String = "",
    val fiatCurrency: String = "",
    val gasValue: String = "",
    val showGasField: Boolean = true,
)

@Immutable
data class VerifyTransactionUiModel(
    val transaction: TransactionUiModel = TransactionUiModel(),
    val consentAddress: Boolean = false,
    val consentAmount: Boolean = false,
    val consentDst: Boolean = false,
    val errorText: UiText? = null,
    val blowfishShow: Boolean = false,
    val blowfishWarnings: List<String> = emptyList(),
)

@HiltViewModel
internal class VerifyTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<SendDst>,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,

    private val transactionRepository: TransactionRepository,
    private val vaultRepository: VaultRepository,
    private val blowfishRepository: BlowfishRepository,
) : ViewModel() {

    private val transactionId: TransactionId = requireNotNull(savedStateHandle[ARG_TRANSACTION_ID])
    private val vaultId: String? = savedStateHandle["vault_id"]

    private val transaction = transactionRepository.getTransaction(transactionId)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val uiState = MutableStateFlow(VerifyTransactionUiModel())

    init {
        loadTransaction()
        blowfishTransactionScan()
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

    fun joinKeysign() {
        val hasAllConsents = uiState.value.let {
            it.consentAddress && it.consentAmount && it.consentDst
        }

        if (hasAllConsents) {
            viewModelScope.launch {
                val transaction = transaction.filterNotNull().first()

                navigator.navigate(
                    SendDst.Keysign(
                        transactionId = transaction.id,
                    )
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

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
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

    private fun blowfishTransactionScan() {
        viewModelScope.launch {
            val transaction = transaction.filterNotNull().first()
            if (vaultId == null) return@launch
            val vault = requireNotNull(vaultRepository.get(vaultId))
            try {
                val result = blowfishRepository.scanBlowfishTransaction(vault, transaction)
                uiState.update { state ->
                    state.copy(
                        blowfishShow = result.first,
                        blowfishWarnings = result.second
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}


