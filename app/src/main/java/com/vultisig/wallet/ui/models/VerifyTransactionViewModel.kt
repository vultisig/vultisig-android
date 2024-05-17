package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_TRANSACTION_ID
import com.vultisig.wallet.ui.navigation.Navigator
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
data class TransactionUiModel(
    val srcAddress: String = "",
    val dstAddress: String = "",
    val tokenValue: String = "",
    val fiatValue: String = "",
    val fiatCurrency: String = "",
    val gasValue: String = "",
)

@Immutable
data class VerifyTransactionUiModel(
    val transaction: TransactionUiModel = TransactionUiModel(),
    val consentAddress: Boolean = false,
    val consentAmount: Boolean = false,
    val consentDst: Boolean = false,
    val errorText: UiText? = null,
)

@HiltViewModel
internal class VerifyTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToString: TokenValueToStringMapper,

    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val transactionId: TransactionId = requireNotNull(savedStateHandle[ARG_TRANSACTION_ID])

    private val transaction = transactionRepository.getTransaction(transactionId)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val uiState = MutableStateFlow(VerifyTransactionUiModel())

    init {
        loadTransaction()
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
                    Destination.Keysign(
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

            val tokenValue = transaction.tokenValue
            val fiatValue = transaction.fiatValue
            val gasFee = transaction.gasFee

            val tokenValueString = mapTokenValueToString(tokenValue)
            val fiatValueString = fiatValueToStringMapper.map(transaction.fiatValue)
            val gasFeeString = mapTokenValueToString(gasFee)

            val transactionUiModel = TransactionUiModel(
                srcAddress = transaction.srcAddress,
                dstAddress = transaction.dstAddress,
                tokenValue = tokenValueString,
                fiatValue = fiatValueString,
                fiatCurrency = fiatValue.currency,
                gasValue = gasFeeString,
            )

            uiState.update {
                it.copy(transaction = transactionUiModel)
            }
        }
    }

}