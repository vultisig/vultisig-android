package com.vultisig.wallet.ui.screens.util.password

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class InputPasswordViewModelDelegate(
    private val vaultId: VaultId,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : ViewModel() {

    val state = MutableStateFlow(KeysignPasswordUiModel())

    val passwordFieldState = TextFieldState()

    val password: String
        get() = passwordFieldState.text.toString()


    init {
        viewModelScope.launch {
            val passwordHint = getPasswordHint(vaultId)
            state.update {
                it.copy(
                    passwordHint = passwordHint
                )
            }
        }
    }

    fun togglePasswordVisibility() {
        state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    suspend fun checkIfPasswordIsValid(): Boolean {
        val password = password
        if (!isPasswordEmpty()) {
            val vault = vaultRepository.get(vaultId)
                ?: error("No vault with id $vaultId exists")

            val isPasswordValid = vultiSignerRepository.isPasswordValid(
                publicKeyEcdsa = vault.pubKeyECDSA,
                password = password,
            )

            if (isPasswordValid) {
                return true
            } else {
                state.update {
                    it.copy(
                        passwordError = UiText.StringResource(
                            R.string.keysign_password_incorrect_password
                        ),
                    )
                }
            }
        } else {
            verifyPassword()
        }

        return false
    }

    private fun verifyPassword() {
        val error = if (isPasswordEmpty()) {
            UiText.StringResource(R.string.password_should_not_be_empty)
        } else null
        state.update {
            it.copy(passwordError = error)
        }
    }

    private suspend fun getPasswordHint(vaultId: String): UiText? {
        val passwordHintString =
            vaultDataStoreRepository.readFastSignHint(vaultId = vaultId).first()

        if (passwordHintString.isEmpty()) return null

        return UiText.FormattedText(
            R.string.import_file_password_hint_text,
            listOf(passwordHintString)
        )
    }

    private fun isPasswordEmpty() = password.isEmpty()

}