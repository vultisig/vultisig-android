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
    val isFocused: Boolean = false,
    val errorMessage: UiText? = null,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    val textFieldState: TextFieldState = TextFieldState(),
)

@HiltViewModel
internal class FastVaultEmailViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultEmailState())

    init {
        viewModelScope.launch {
            collectEmailInput()
        }
    }


    private suspend fun collectEmailInput() {
        state.value.textFieldState.textAsFlow().collect { typingEmail ->
            val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(typingEmail).matches()
            val errorMessage =
                UiText.StringResource(R.string.keygen_email_caption)
                    .takeIf { typingEmail.isNotEmpty() && !isEmailValid }
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


    fun updateInputFocus(isFocused: Boolean) {
        viewModelScope.launch {
            state.update {
                it.copy(isFocused = isFocused)
            }
        }
    }

    fun navigateToPassword() {
        viewModelScope.launch {
            if (state.value.innerState != VsTextInputFieldInnerState.Success)
                return@launch
            val enteredEmail = state.value.textFieldState.text.toString()
            val vaultName = savedStateHandle.toRoute<Route.FastVaultInfo.Email>().name
            navigator.route(
                Route.FastVaultInfo.Password(
                    name = vaultName,
                    email = enteredEmail,
                )
            )
        }
    }

    fun clearInput() {
        state.value.textFieldState.clearText()
    }

    fun navigateToBack() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}