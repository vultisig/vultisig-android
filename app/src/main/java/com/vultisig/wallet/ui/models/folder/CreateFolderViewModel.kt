package com.vultisig.wallet.ui.models.folder

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CreateFolderState(
    val placeholder: String = "",
    val textField: TextFieldState = TextFieldState(),
    val errorText: UiText? = null,
    val folderNames: List<String> = emptyList(),
    val vaults: Map<Vault, Boolean> = emptyMap(),
){
    val isButtonEnabled: Boolean =
        errorText == null && vaults.any { it.value }
}

@HiltViewModel
internal class CreateFolderViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val folderRepository: FolderRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val uniqueName: GenerateUniqueName,
    private val isNameLengthValid: IsVaultNameValid,
    private val getOrderedVaults: GetOrderedVaults,
) : ViewModel() {
    val state = MutableStateFlow(CreateFolderState())

    init {
        getFolderNames()
        getVaults()
        validateEachTextChange()
    }

    private fun getFolderNames() = viewModelScope.launch {
        folderRepository.getAll().collectLatest { folders ->
            state.update { it.copy(folderNames = folders.map { folder -> folder.name }) }
        }
    }

    private fun validateEachTextChange() = viewModelScope.launch {
        state.value.textField.textAsFlow().collectLatest {
            validate()
        }
    }

    private fun getVaults() = viewModelScope.launch {
        getOrderedVaults(null, false).collectLatest { vaults ->
            state.update { it.copy(vaults = vaults.associateWith { false } ) }
        }
    }

    fun loadPlaceholder(placeHolder: String) {
        state.value = state.value.copy(placeholder = placeHolder)
    }

    private fun validate() = viewModelScope.launch {
        val name = state.value.textField.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        state.update { it.copy(errorText = errorMessage) }
    }

    fun checkVault(vault: Vault, checked: Boolean) {
        state.update {
            it.copy(
                vaults = mutableMapOf<Vault, Boolean>()
                    .apply {
                        putAll(it.vaults)
                        put(vault, checked)
                    }
                )
        }
    }

    fun createFolder() = viewModelScope.launch {
        if (state.value.errorText != null)
            return@launch
        val name = uniqueName(
            state.value.textField.text.toString().ifEmpty { state.value.placeholder },
            state.value.folderNames
        )
        val folderId = folderRepository.insertFolder(name)
        vaultOrderRepository.updateList(
            folderId.toString(),
            state.value.vaults.filterValues { it }.keys.map { it.id }
        )

        navigator.navigate(Destination.Back)
    }

}