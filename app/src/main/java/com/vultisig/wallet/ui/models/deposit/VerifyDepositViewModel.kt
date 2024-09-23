package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
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
    val hasFastSign: Boolean = false,
)

@HiltViewModel
internal class VerifyDepositViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val vaultRepository: VaultRepository,
    private val vultiSignerRepository: VultiSignerRepository,
) : ViewModel() {

    val state = MutableStateFlow(VerifyDepositUiModel())

    private val transactionId: String = requireNotNull(savedStateHandle[SendDst.ARG_TRANSACTION_ID])
    private val vaultId: String? = savedStateHandle["vault_id"]

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

        loadFastSign()
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun confirm() {
        keysign(false)
    }

    fun fastSign() {
        keysign(true)
    }

    private fun keysign(
        hasFastSign: Boolean,
    ) {
        viewModelScope.launch {
            val transaction = depositTransactionRepository.getTransaction(transactionId)

            if (hasFastSign) {
                sendNavigator.navigate(
                    SendDst.Password(
                        transactionId = transaction.id,
                    )
                )
            } else {
                sendNavigator.navigate(
                    SendDst.Keysign(
                        transactionId = transaction.id,
                        password = null,
                    )
                )
            }
        }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            if (vaultId == null) return@launch
            val vault = requireNotNull(vaultRepository.get(vaultId))
            val hasFastSign = vultiSignerRepository.hasFastSign(vault.pubKeyECDSA)
            state.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }

}