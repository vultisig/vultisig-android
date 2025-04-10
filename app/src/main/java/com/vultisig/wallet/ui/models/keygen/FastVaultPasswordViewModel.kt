package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.backup.PasswordState
import com.vultisig.wallet.ui.screens.backup.PasswordViewModelDelegate
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultPasswordUiModel(
    val isMoreInfoVisible: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isNextButtonEnabled: Boolean = false,
    val errorMessage: UiText? = null,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
)

@HiltViewModel
internal class FastVaultPasswordViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VaultInfo.Password>()

    val state = MutableStateFlow(FastVaultPasswordUiModel())

    private val passwordDelegate = PasswordViewModelDelegate()

    val passwordTextFieldState = passwordDelegate.passwordTextFieldState
    val confirmPasswordTextFieldState = passwordDelegate.confirmPasswordTextFieldState

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
            passwordDelegate.validatePasswords()
                .collect { passwordState ->
                    val errorMessage = if (passwordState is PasswordState.Mismatch) {
                        UiText.StringResource(R.string.fast_vault_password_screen_error)
                    } else {
                        null
                    }

                    state.update {
                        it.copy(
                            isNextButtonEnabled = passwordState is PasswordState.Valid,
                            errorMessage = errorMessage,
                            innerState = if (errorMessage != null) {
                                VsTextInputFieldInnerState.Error
                            } else {
                                VsTextInputFieldInnerState.Default
                            }
                        )
                    }
                }
        }
    }

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
                    name = args.name,
                    email = args.email,
                    password = enteredPassword,
                    tssAction = args.tssAction,
                    vaultId = args.vaultId
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