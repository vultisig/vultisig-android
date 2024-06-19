package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class VerifyApproveUiModel(
    val spenderAddress: String = "",
    val estimatedFees: String = "",
    val consentAllowance: Boolean = false,
    val errorText: UiText? = null,
)


@HiltViewModel
internal class VerifyApproveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,

    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ViewModel() {

    val state = MutableStateFlow(VerifyApproveUiModel())

    private val transactionId: String = requireNotNull(savedStateHandle[SendDst.ARG_TRANSACTION_ID])

    init {
        viewModelScope.launch {
            val currency = appCurrencyRepository.currency.first()
            val transaction = swapTransactionRepository.getTransaction(transactionId)

            val fiatFees = convertTokenValueToFiat(
                transaction.dstToken,
                transaction.estimatedFees, currency
            )

            state.update {
                it.copy(
                    spenderAddress = transaction.dstAddress,
                    estimatedFees = fiatValueToStringMapper.map(fiatFees),
                )
            }
        }
    }

    fun consentAllowance(consent: Boolean) {
        state.update { it.copy(consentAllowance = consent) }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun confirm() {
        val hasAllConsents = state.value.consentAllowance

        if (hasAllConsents) {
            viewModelScope.launch {
                sendNavigator.navigate(
                    SendDst.KeysignApproval(
                        transactionId = transactionId,
                    )
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

}