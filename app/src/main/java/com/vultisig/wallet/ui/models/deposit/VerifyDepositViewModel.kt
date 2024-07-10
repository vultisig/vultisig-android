package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.mappers.DurationToUiStringMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VerifyDepositUiModel(
    val fromAddress: String = "",
    val srcTokenValue: String = "",
    val estimatedFees: String = "",
    val memo: String = "",
    val nodeAddress: String = "",
    val errorText: UiText? = null,
)

@HiltViewModel
internal class VerifyDepositViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val depositTransactionRepository: DepositTransactionRepository,
) : ViewModel() {

    val state = MutableStateFlow(VerifyDepositUiModel())

    private val transactionId: String = requireNotNull(savedStateHandle[SendDst.ARG_TRANSACTION_ID])

    init {
        viewModelScope.launch {
            val transaction = depositTransactionRepository.getTransaction(transactionId)

            state.update {
                it.copy(
                    fromAddress = transaction.srcAddress,
                    srcTokenValue = mapTokenValueToStringWithUnit(transaction.srcTokenValue),
                    estimatedFees = mapTokenValueToStringWithUnit(transaction.estimatedFees),
                    memo = transaction.memo,
                    nodeAddress = transaction.dstAddress,
                )
            }
        }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun confirm() {
        viewModelScope.launch {
            sendNavigator.navigate(
                SendDst.Keysign(
                    transactionId = transactionId,
                )
            )
        }
    }

}