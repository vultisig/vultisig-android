@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models.keysign

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.SendDst.Companion.ARG_TRANSACTION_ID
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class KeysignPasswordUiModel(
    val isPasswordVisible: Boolean = false,
    val passwordError: UiText? = null,
)

@HiltViewModel
internal class KeysignPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<SendDst>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,

    private val transactionRepository: TransactionRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val depositTransaction: DepositTransactionRepository,
) : ViewModel() {

    private val transactionId: TransactionId = requireNotNull(savedStateHandle[ARG_TRANSACTION_ID])

    val state = MutableStateFlow(KeysignPasswordUiModel())

    val passwordFieldState = TextFieldState()

    private val password: String
        get() = passwordFieldState.text.toString()

    fun verifyPassword() {
        val error = if (isPasswordEmpty()) {
            UiText.StringResource(R.string.password_should_not_be_empty)
        } else null
        state.update {
            it.copy(passwordError = error)
        }
    }

    fun togglePasswordVisibility() {
        state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun proceed() {
        val password = password
        if (!isPasswordEmpty()) {
            viewModelScope.launch {
                val vaultId = getTransactionVaultId(transactionId)
                val vault = vaultRepository.get(vaultId)
                    ?: error("No vault with id $vaultId exists")

                val isPasswordValid = vultiSignerRepository.isPasswordValid(
                    publicKeyEcdsa = vault.pubKeyECDSA,
                    password = password,
                )

                if (isPasswordValid) {
                    navigator.navigate(
                        SendDst.Keysign(
                            transactionId = transactionId,
                            password = password,
                        )
                    )
                } else {
                    state.update {
                        it.copy(
                            passwordError = UiText.StringResource(
                                R.string.keysign_password_incorrect_password
                            )
                        )
                    }
                }
            }
        } else {
            verifyPassword()
        }
    }

    // FIXME forgive me god, this is terrible, but i need this asap;
    private suspend fun getTransactionVaultId(transactionId: String): String {
        return try {
            transactionRepository.getTransaction(transactionId)
                .first()
                .vaultId
        } catch (e: Exception) {
            try {
                swapTransactionRepository.getTransaction(transactionId)
                    .vaultId
            } catch (e: Exception) {
                depositTransaction.getTransaction(transactionId)
                    .vaultId
            }
        }
    }

    private fun isPasswordEmpty() = password.isEmpty()

}