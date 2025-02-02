package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultPasswordHintState(
    val isFocused: Boolean = false,
    val textFieldState: TextFieldState = TextFieldState(),
)

@HiltViewModel
internal class FastVaultPasswordHintViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultPasswordHintState())

    fun updateInputFocus(isFocused: Boolean) {
        viewModelScope.launch {
            state.update {
                it.copy(isFocused = isFocused)
            }
        }
    }

    fun navigateToBack() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}