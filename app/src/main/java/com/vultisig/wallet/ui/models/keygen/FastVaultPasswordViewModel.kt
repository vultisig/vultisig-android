package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultPasswordUiModel(
    val isMoreInfoVisible: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isNextButtonEnabled: Boolean = false,
    val errorMessage: UiText = UiText.Empty,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
)

@HiltViewModel
internal class FastVaultPasswordViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val vaultName = savedStateHandle.toRoute<Route.VaultInfo.Password>().name
    val email = savedStateHandle.toRoute<Route.VaultInfo.Password>().email
    val tssAction = savedStateHandle.toRoute<Route.VaultInfo.Password>().tssAction
    val vaultId = savedStateHandle.toRoute<Route.VaultInfo.Password>().vaultId

    val state = MutableStateFlow(FastVaultPasswordUiModel())
    val passwordTextFieldState = TextFieldState()
    val confirmPasswordTextFieldState = TextFieldState()

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
                    val isShowError = isShowError(password, confirmPassword)
                    val errorMessage = if (isShowError) {
                        UiText.StringResource(R.string.fast_vault_password_screen_error)
                    } else {
                        UiText.Empty
                    }
                    state.update {
                        it.copy(
                            isNextButtonEnabled = isValidPassword,
                            errorMessage = errorMessage,
                            innerState = if (isShowError) {
                                VsTextInputFieldInnerState.Error
                            } else {
                                VsTextInputFieldInnerState.Default
                            }
                        )
                    }

                }.launchIn(this)
        }
    }

    private fun validatePassword(password: CharSequence, confirmPassword: CharSequence) =
        password.isNotEmpty() && password.toString() == confirmPassword.toString()

    private fun isShowError(password: CharSequence, confirmPassword: CharSequence) =
        password.isNotEmpty() && confirmPassword.isNotEmpty()
                && password.toString() != confirmPassword.toString()

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
            navigator.route(
                Route.VaultInfo.PasswordHint(
                    name = vaultName,
                    email = email,
                    password = enteredPassword,
                    tssAction = tssAction,
                    vaultId = vaultId
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