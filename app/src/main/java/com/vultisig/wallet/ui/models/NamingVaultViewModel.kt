package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.UiText.StringResource
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.TextFieldUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class NamingVaultUiModel(
    val placeholder: UiText = StringResource(R.string.naming_vault_screen_vault_placeholder)
)

@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class NamingVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state = MutableStateFlow(NamingVaultUiModel())

    val namingTextFieldState = TextFieldState()
    val errorMessageState = MutableStateFlow<UiText?>(null)
    private val vaultNamesList = MutableStateFlow<List<String>>(emptyList())

    private val vaultSetupType =
        VaultSetupType.fromInt(
            (savedStateHandle.get<String>(Destination.NamingVault.ARG_VAULT_SETUP_TYPE)
                ?: "0").toInt()
        )

    init {
        val placeholder = when (vaultSetupType) {
            VaultSetupType.FAST -> StringResource(R.string.naming_vault_placeholder_hot_vault)
            VaultSetupType.ACTIVE -> StringResource(R.string.naming_vault_screen_vault_placeholder)
            else -> StringResource(R.string.naming_vault_placeholder_cold_vault)
        }
        state.update { it.copy(placeholder = placeholder) }

        viewModelScope.launch {
            vaultNamesList.update { vaultRepository.getAll().map { it.name } }
        }
    }

    private fun validateVaultName(s: String): UiText? {
        if (isNameNotValid(s))
            return StringResource(R.string.naming_vault_screen_invalid_name)
        val isNameAlreadyExist = vaultNamesList.value.any { it == s }
        if (isNameAlreadyExist) {
            return StringResource(R.string.vault_edit_this_name_already_exist)
        }
        return null
    }

    fun navigateToKeygen(placeholder: String) {
        val name = namingTextFieldState.text.toString().ifEmpty { placeholder }
        if (errorMessageState.value != null)
            return
        viewModelScope.launch {
            when (vaultSetupType) {
                VaultSetupType.ACTIVE, VaultSetupType.FAST -> {
                    navigator.navigate(
                        Destination.KeygenEmail(
                            name = name,
                            setupType = vaultSetupType
                        )
                    )
                }
                else -> {
                    navigator.navigate(
                        Destination.KeygenFlow(
                            vaultName = name,
                            vaultSetupType = vaultSetupType,
                            isReshare = false,
                            email = null,
                            password = null,
                        )
                    )
                }
            }
        }
    }

    fun validate() = viewModelScope.launch {
        val placeholder = context.getString(R.string.naming_vault_screen_vault_placeholder)
        val name = namingTextFieldState.text.toString().ifEmpty { placeholder }
        val errorMessage = validateVaultName(name)
        errorMessageState.update { errorMessage }
    }

    private fun isNameNotValid(s: String) =
        s.isEmpty() || s.length > TextFieldUtils.VAULT_NAME_MAX_LENGTH
}