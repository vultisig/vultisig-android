package com.vultisig.wallet.ui.screens.backup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
            password, confirmPassword ->
            validate(password.toString(), confirmPassword.toString(), minLength)
        }

    internal fun confirmedPassword(minLength: Int): String? {
        val password = passwordTextFieldState.text.toString()
        return password.takeIf {
            validate(password, confirmPasswordTextFieldState.text.toString(), minLength) ==
                PasswordState.Valid
        }
    }

    internal fun clearPasswords() {
        passwordTextFieldState.setTextAndPlaceCursorAtEnd("")
        confirmPasswordTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    private fun validate(password: String, confirmPassword: String, minLength: Int): PasswordState =
        when {
            password.isBlank() || confirmPassword.isBlank() -> PasswordState.Empty
            password != confirmPassword -> PasswordState.Mismatch
            password.length < minLength -> PasswordState.TooShort(minLength)
            else -> PasswordState.Valid
        }
}
