package com.vultisig.wallet.ui.models.keygen

import android.util.Patterns
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultEmailState(
    val errorMessage: UiText = UiText.Empty,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
)

@HiltViewModel
internal class FastVaultEmailViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultEmailState())
    val emailFieldState = TextFieldState()

    private val args = savedStateHandle.toRoute<Route.VaultInfo.Email>()

    private val vaultName = args.name
    private val action = args.action
    private val vaultId = args.vaultId

    init {
        collectEmailInput()
    }


    private fun collectEmailInput() {
        viewModelScope.launch {
            emailFieldState.textAsFlow().collect { typingEmail ->
                val isEmailValid = validateEmail(typingEmail)
                val errorMessage =
                    UiText.StringResource(R.string.keygen_email_error)
                        .takeIf { typingEmail.isNotEmpty() && !isEmailValid } ?: UiText.Empty
                val innerState = getInnerState(
                    email = typingEmail.toString(),
                    isEmailValid = isEmailValid
                )
                state.update { state ->
                    state.copy(
                        errorMessage = errorMessage,
                        innerState = innerState
                    )
                }
            }
        }
    }

    private fun validateEmail(typingEmail: CharSequence) =
        Patterns.EMAIL_ADDRESS.matcher(typingEmail).matches()

    private fun getInnerState(
        email: String,
        isEmailValid: Boolean,
    ) = if (email.isEmpty())
        VsTextInputFieldInnerState.Default
    else {
        if (isEmailValid)
            VsTextInputFieldInnerState.Success
        else VsTextInputFieldInnerState.Error
    }

    fun navigateToPassword() {
        viewModelScope.launch {
            if (!validateEmail(emailFieldState.text.toString()))
                return@launch
            val enteredEmail = emailFieldState.text.toString()

            if (!args.password.isNullOrBlank()) {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        vaultName = vaultName,
                        email = enteredEmail,
                        action = action,
                        vaultId = vaultId,
                        password = args.password,
                    )
                )
            } else {
                navigator.route(
                    Route.VaultInfo.Password(
                        name = vaultName,
                        email = enteredEmail,
                        tssAction = action,
                        vaultId = vaultId,
                    )
                )
            }
        }
    }

    fun clearInput() {
        emailFieldState.clearText()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}