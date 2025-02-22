package com.vultisig.wallet.ui.models

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateRandomUniqueName
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class NamingVaultUiModel(
    val placeholder: String = "",
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null
)

@HiltViewModel
internal class NamingVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val uniqueName: GenerateRandomUniqueName,
    private val isNameLengthValid: IsVaultNameValid,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val uiState = MutableStateFlow(NamingVaultUiModel())

    val namingTextFieldState = TextFieldState()
    private val vaultNamesList = MutableStateFlow<List<String>>(emptyList())

    private val vaultSetupType =
        VaultSetupType.fromInt(
            (savedStateHandle.get<String>(Destination.NamingVault.ARG_VAULT_SETUP_TYPE)
                ?: "0").toInt()
        )

    private var isLoading = false
        set(value) {
            uiState.update {
                it.copy(isLoading = value)
            }
        }

    init {
        val placeholder =
            context.getString(
                when (vaultSetupType) {
                    VaultSetupType.FAST -> R.string.naming_vault_placeholder_fast_vault
                    VaultSetupType.ACTIVE -> R.string.naming_vault_placeholder_active_vault
                    else -> R.string.naming_vault_placeholder_secure_vault
                }
            )
        uiState.update { it.copy(placeholder = placeholder) }

        viewModelScope.launch {
            vaultNamesList.update { vaultRepository.getAll().map { it.name } }
            namingTextFieldState.textAsFlow().collectLatest {
                validate()
            }
        }
    }

    fun navigateToKeygen() {
        if (uiState.value.errorMessage != null)
            return
        isLoading = true
        val name = Uri.encode(
            uniqueName(
                namingTextFieldState.text.toString()
                    .ifEmpty { uiState.value.placeholder },
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
                    navigator.route(
                        Route.Keygen.PeerDiscovery(
                            action = TssAction.KEYGEN,
                            vaultName = name,
                        )
                    )
                }
            }
        }
        isLoading = false
    }


    private fun validate() = viewModelScope.launch {
        val name = namingTextFieldState.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        uiState.update {
            it.copy(
                errorMessage = errorMessage
            )
        }
    }
}