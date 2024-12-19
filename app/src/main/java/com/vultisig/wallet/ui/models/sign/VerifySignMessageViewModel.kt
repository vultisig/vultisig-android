package com.vultisig.wallet.ui.models.sign

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.CustomMessagePayloadRepo
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.usecases.GetSendDstByKeysignInitType
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SignMessageTransactionUiModel(
    val method: String = "",
    val message: String = "",
)

internal data class VerifySignMessageUiModel(
    val model: SignMessageTransactionUiModel = SignMessageTransactionUiModel(),
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
)

@HiltViewModel
internal class VerifySignMessageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val customMessagePayloadRepo: CustomMessagePayloadRepo,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val getSendDstByKeysignInitType: GetSendDstByKeysignInitType,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
) : ViewModel() {

    val state = MutableStateFlow(VerifySignMessageUiModel())
    private val password = MutableStateFlow<String?>(null)

    private val transactionId: String = requireNotNull(savedStateHandle[SendDst.ARG_TRANSACTION_ID])
    private val vaultId: String? = savedStateHandle[SendDst.ARG_VAULT_ID]

    init {
        viewModelScope.launch {
            val payload = customMessagePayloadRepo.get(transactionId)!!.payload

            state.update {
                it.copy(
                    model = SignMessageTransactionUiModel(
                        method = payload.method,
                        message = payload.message,
                    )
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

            sendNavigator.navigate(
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