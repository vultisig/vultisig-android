package com.vultisig.wallet.ui.screens.backup

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.ui.utils.textAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

sealed interface PasswordState {
    data object Valid : PasswordState

    data object Mismatch : PasswordState

    data object Empty : PasswordState

    data class TooShort(val minLength: Int) : PasswordState
}

class PasswordViewModelDelegate {

    val passwordTextFieldState = TextFieldState()
    val confirmPasswordTextFieldState = TextFieldState()

    fun validatePasswords(minLength: Int = 1): Flow<PasswordState> =
        combine(passwordTextFieldState.textAsFlow(), confirmPasswordTextFieldState.textAsFlow()) {
            password,
            confirmPassword ->
            when {
                password.isEmpty() || confirmPassword.isEmpty() -> PasswordState.Empty
                password.toString() != confirmPassword.toString() -> PasswordState.Mismatch
                password.length < minLength -> PasswordState.TooShort(minLength)
                else -> PasswordState.Valid
            }
        }
}
