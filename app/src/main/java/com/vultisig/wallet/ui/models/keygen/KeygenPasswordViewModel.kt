@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class KeygenPasswordUiModel(
    val isPasswordVisible: Boolean = false,
    val isVerifyPasswordVisible: Boolean = false,
    val passwordError: UiText? = null,
    val verifyPasswordError: UiText? = null,
)

@HiltViewModel
internal class KeygenPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(KeygenPasswordUiModel())

    val passwordFieldState = TextFieldState()
    val verifyPasswordFieldState = TextFieldState()

    private val name: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_NAME])
    private val email: String =
        requireNotNull(savedStateHandle[Destination.ARG_EMAIL])
    private val setupType: VaultSetupType =
        VaultSetupType.fromInt(
            requireNotNull(savedStateHandle[Destination.ARG_VAULT_SETUP_TYPE])
        )


    private val password: String
        get() = passwordFieldState.text.toString()

    private val passwordDouble: String
        get() = verifyPasswordFieldState.text.toString()

    fun verifyPassword() {
        val error = if (isPasswordEmpty()) {
            UiText.StringResource(R.string.password_should_not_be_empty)
        } else null
        state.update {
            it.copy(passwordError = error)
        }
    }

    fun verifyConfirmPassword() {
        val error = if (!isPasswordsMatch()) {
            UiText.StringResource(R.string.keygen_passwords_dont_match)
        } else null
        state.update {
            it.copy(verifyPasswordError = error)
        }
    }

    fun togglePasswordVisibility() {
        state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleVerifyPasswordVisibility() {
        state.update { it.copy(isVerifyPasswordVisible = !it.isVerifyPasswordVisible) }
    }

    fun proceed() {
        val password = password
        if (!isPasswordEmpty() && isPasswordsMatch()) {
            viewModelScope.launch {
                navigator.navigate(
                    Destination.KeygenFlow(
                        vaultName = name,
                        vaultSetupType = setupType,
                        isReshare = false, // todo correct check for reshare

                        email = email,
                        password = password,
                    )
                )
            }
        } else {
            verifyPassword()
            verifyConfirmPassword()
        }
    }

    private fun isPasswordEmpty() = password.isEmpty()

    private fun isPasswordsMatch() = password == passwordDouble

}