package com.vultisig.wallet.ui.models

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.forEachTextValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class NamingVaultUiModel(
    val placeholder: String = ""
)

@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class NamingVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val uniqueName: GenerateUniqueName,
    private val isNameLengthValid: IsVaultNameValid,
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
        val placeholder =
            context.getString(
                when (vaultSetupType) {
                    VaultSetupType.FAST -> R.string.naming_vault_placeholder_fast_vault
                    VaultSetupType.ACTIVE -> R.string.naming_vault_placeholder_active_vault
                    else -> R.string.naming_vault_placeholder_secure_vault
                }
            )
        state.update { it.copy(placeholder = placeholder) }

        viewModelScope.launch {
            vaultNamesList.update { vaultRepository.getAll().map { it.name } }
            namingTextFieldState.forEachTextValue {
                validate()
            }
        }
    }

    fun navigateToKeygen() {
        if (errorMessageState.value != null)
            return
        val name = Uri.encode(
            uniqueName(
                namingTextFieldState.text.toString()
                    .ifEmpty { state.value.placeholder },
                vaultNamesList.value
            )
        )
        viewModelScope.launch {
            when (vaultSetupType) {
                VaultSetupType.ACTIVE, VaultSetupType.FAST -> {
                    navigator.navigate(
                        Destination.KeygenEmail(
                            vaultId = null,
                            name = name,
                            setupType = vaultSetupType
                        )
                    )
                }
                else -> {
                    navigator.navigate(
                        Destination.KeygenFlow.generateNewVault(
                            name = name,
                            setupType = vaultSetupType,
                        )
                    )
                }
            }
        }
    }

    private fun validate() = viewModelScope.launch {
        val name = namingTextFieldState.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        errorMessageState.update { errorMessage }
    }
}