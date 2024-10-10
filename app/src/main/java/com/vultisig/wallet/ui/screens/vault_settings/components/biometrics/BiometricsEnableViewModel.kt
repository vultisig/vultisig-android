package com.vultisig.wallet.ui.screens.vault_settings.components.biometrics

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BiometricsEnableUiModel(
    val isEnabled: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val passwordErrorMessage: UiText? = null,
)

@HiltViewModel
internal class BiometricsEnableViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val uiModel = MutableStateFlow(BiometricsEnableUiModel())

    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.VaultSettings.ARG_VAULT_ID))

    val passwordTextFieldState = TextFieldState()

    fun togglePasswordVisibility() = viewModelScope.launch {
        uiModel.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onCheckChange(isChecked: Boolean) = viewModelScope.launch {
        uiModel.update { it.copy(isEnabled = isChecked) }
    }

    fun onSaveClick() = viewModelScope.launch {
        navigator.navigate(Destination.Back)
    }

}