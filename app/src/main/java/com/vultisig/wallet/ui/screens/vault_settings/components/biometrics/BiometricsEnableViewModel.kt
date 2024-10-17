package com.vultisig.wallet.ui.screens.vault_settings.components.biometrics

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BiometricsEnableUiModel(
    val isSwitchEnabled: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val passwordErrorMessage: UiText? = null,
)

@HiltViewModel
internal class BiometricsEnableViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultRepository: VaultRepository,
    private val vultiSignerRepository: VultiSignerRepository,
    private val snackbarFlow: SnackbarFlow,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val uiModel = MutableStateFlow(BiometricsEnableUiModel())

    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.VaultSettings.ARG_VAULT_ID))

    val passwordTextFieldState = TextFieldState()

    init {
        initSwitchState()
        validateEachTextChange()
    }

    private fun initSwitchState() = viewModelScope.launch {
        vaultPasswordRepository.getPassword(vaultId).let { password ->
            if (password != null) {
                uiModel.update { it.copy(isSwitchEnabled = true) }
            }
        }
    }

    private fun validateEachTextChange() = viewModelScope.launch {
        passwordTextFieldState.textAsFlow().collectLatest { text ->
            uiModel.update { it.copy(isSaveEnabled = text.isNotEmpty()) }
        }
    }

    private suspend fun showSnackbarMessage(isEnabled: Boolean) {
        val messageRes = if (isEnabled) {
            R.string.vault_settings_biometrics_screen_snackbar_enabled
        } else {
            R.string.vault_settings_biometrics_screen_snackbar_disabled
        }
        snackbarFlow.showMessage(context.getString(messageRes))
    }

    fun togglePasswordVisibility() = viewModelScope.launch {
        uiModel.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onCheckChange(isChecked: Boolean) = viewModelScope.launch {
        uiModel.update { it.copy(isSwitchEnabled = isChecked) }
    }

    fun onSaveClick() = viewModelScope.launch {
        val vault = vaultRepository.get(vaultId)
            ?: error("No vault with id $vaultId exists")
        val isPasswordValid = vultiSignerRepository.isPasswordValid(
            publicKeyEcdsa = vault.pubKeyECDSA,
            password = passwordTextFieldState.text.toString(),
        )

        if (!isPasswordValid) {
            uiModel.update { it.copy(
                passwordErrorMessage = UiText.StringResource(
                    R.string.keysign_password_incorrect_password
                ),
                isSaveEnabled = false,
            ) }
            return@launch
        }
        if (uiModel.value.isSwitchEnabled) {
            vaultPasswordRepository.savePassword(vaultId, passwordTextFieldState.text.toString())
        } else {
            vaultPasswordRepository.clearPassword(vaultId)
        }
        showSnackbarMessage(uiModel.value.isSwitchEnabled)
        navigator.navigate(Destination.Back)
    }

}