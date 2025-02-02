package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultNameState(
    val isFocused: Boolean = false,
    val textFieldState: TextFieldState = TextFieldState(),
)

@HiltViewModel
internal class FastVaultNameViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultNameState())

    fun updateInputFocus(isFocused: Boolean) {
        viewModelScope.launch {
            state.update {
                it.copy(isFocused = isFocused)
            }
        }
    }

    fun navigateToEmail() {
        viewModelScope.launch {
            val enteredName = state.value.textFieldState.text.toString()
            navigator.route(
                Route.FastVaultInfo.Email(enteredName)
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