package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultPasswordState(
    val isMoreInfoVisible: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isNextButtonEnabled: Boolean = false,
)

@HiltViewModel
internal class FastVaultPasswordViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultPasswordState())
    val passwordTextFieldState: TextFieldState = TextFieldState()
    val confirmPasswordTextFieldState: TextFieldState = TextFieldState()

    private var isMoreInfoVisible: Boolean
        get() = state.value.isMoreInfoVisible
        set(value) = state.update {
            it.copy(isMoreInfoVisible = value)
        }

    private var isPasswordVisible: Boolean
        get() = state.value.isPasswordVisible
        set(value) = state.update {
            it.copy(isPasswordVisible = value)
        }

    private var isConfirmPasswordVisible: Boolean
        get() = state.value.isConfirmPasswordVisible
        set(value) = state.update {
            it.copy(isConfirmPasswordVisible = value)
        }


    init {
        viewModelScope.launch {
            passwordTextFieldState.textAsFlow()
                .combine(
                    confirmPasswordTextFieldState.textAsFlow()
                ) { password, confirmPassword ->
                    val isValidPassword = validatePassword(password, confirmPassword)
                    state.update {
                        it.copy(isNextButtonEnabled = isValidPassword)
                    }

                }.launchIn(this)
        }
    }

    private fun validatePassword(password: CharSequence, confirmPassword: CharSequence) =
        password.isNotEmpty() && password.toString() == confirmPassword.toString()

    fun showMoreInfo() {
        isMoreInfoVisible = true
    }

    fun hideMoreInfo() {
        isMoreInfoVisible = false
    }


    fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
    }


    fun toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible
    }


    fun navigateToHint() {
        viewModelScope.launch {
            val enteredPassword = passwordTextFieldState.text.toString()
            val args = savedStateHandle.toRoute<Route.FastVaultInfo.Password>()
            val vaultName = args.name
            val email = args.email
            navigator.route(
                Route.FastVaultInfo.PasswordHint(
                    name = vaultName,
                    email = email,
                    password = enteredPassword,
                )
            )
        }
    }


    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}