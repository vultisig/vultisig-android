package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.usecases.GetSendDstByKeysignInitType
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class DepositTransactionUiModel(
    val fromAddress: String = "",
    val srcTokenValue: String = "",
    val estimatedFees: String = "",
    val memo: String = "",
    val nodeAddress: String = "",
)

internal data class VerifyDepositUiModel(
    val depositTransactionUiModel: DepositTransactionUiModel = DepositTransactionUiModel(),
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
)

@HiltViewModel
internal class VerifyDepositViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTransactionToUiModel: DepositTransactionToUiModelMapper,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val getSendDstByKeysignInitType: GetSendDstByKeysignInitType,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    ) : ViewModel() {

    val state = MutableStateFlow(VerifyDepositUiModel())
    private val password = MutableStateFlow<String?>(null)

    private val transactionId: String = requireNotNull(savedStateHandle[SendDst.ARG_TRANSACTION_ID])
    private val vaultId: String? = savedStateHandle["vault_id"]

    init {
        viewModelScope.launch {
            val transaction = depositTransactionRepository.getTransaction(transactionId)

            state.update {
                it.copy(
                    depositTransactionUiModel = mapTransactionToUiModel(transaction)
                )
            }
        }

        loadFastSign()
        loadPassword()
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun confirm() {
        keysign(KeysignInitType.QR_CODE)
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

    private fun keysign(
        keysignInitType: KeysignInitType,
    ) {
        viewModelScope.launch {

            sendNavigator.navigate (
                getSendDstByKeysignInitType(keysignInitType, transactionId, password.value)
            )
        }
    }

    private fun loadPassword() {
        viewModelScope.launch {
            password.value = if (vaultId == null)
                null
            else
                vaultPasswordRepository.getPassword(vaultId)
        }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            if (vaultId == null) return@launch
            val hasFastSign = isVaultHasFastSignById(vaultId)
            state.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }

}