package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
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
    val password: String? = null,
)

@HiltViewModel
internal class VerifyDepositViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTransactionToUiModel: DepositTransactionToUiModelMapper,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val vaultRepository: VaultRepository,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    ) : ViewModel() {

    val state = MutableStateFlow(VerifyDepositUiModel())

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
        if (state.value.password != null) {
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

            when (keysignInitType) {
                KeysignInitType.BIOMETRY -> {
                    sendNavigator.navigate(
                        SendDst.Keysign(
                            transactionId = transactionId,
                            password = state.value.password,
                        )
                    )
                }
                KeysignInitType.PASSWORD -> {
                    sendNavigator.navigate(
                        SendDst.Password(
                            transactionId = transactionId,
                        )
                    )
                }
                KeysignInitType.QR_CODE -> {
                    sendNavigator.navigate(
                        SendDst.Keysign(
                            transactionId = transactionId,
                            password = null,
                        )
                    )
                }
            }
        }
    }

    private fun loadPassword() {
        viewModelScope.launch {

            val password = if (vaultId == null)
                null
            else
                vaultPasswordRepository.getPassword(vaultId)

            state.update {
                it.copy(
                    password = password
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