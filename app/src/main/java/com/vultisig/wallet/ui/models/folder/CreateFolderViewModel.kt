package com.vultisig.wallet.ui.models.folder

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CreateFolderUiModel(
    val errorText: UiText? = null,
    val folderNames: List<String> = emptyList(),
    val checkedVaults: Map<Vault, Boolean> = emptyMap(),
){
    val isCreateButtonEnabled: Boolean =
        errorText == null && checkedVaults.any { it.value }
}

@HiltViewModel
internal class CreateFolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val folderRepository: FolderRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val generateUniqueName: GenerateUniqueName,
    private val isNameLengthValid: IsVaultNameValid,
    private val getOrderedVaults: GetOrderedVaults,
) : ViewModel() {
    val state = MutableStateFlow(CreateFolderUiModel())

    val textFieldState = TextFieldState()

    init {
        collectFolderNames()
        getVaults()
        validateEachTextChange()
    }

    private fun collectFolderNames() = viewModelScope.launch {
        folderRepository.getAll().collectLatest { folders ->
            state.update { it.copy(folderNames = folders.map { folder -> folder.name }) }
        }
    }

    private fun validateEachTextChange() = viewModelScope.launch {
        textFieldState.textAsFlow().collectLatest {
            validate()
        }
    }

    private fun getVaults() = viewModelScope.launch {
        getOrderedVaults(null).collectLatest { vaults ->
            state.update { it.copy(checkedVaults = vaults.associateWith { false } ) }
        }
    }

    private fun validate() = viewModelScope.launch {
        val name = textFieldState.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        state.update { it.copy(errorText = errorMessage) }
    }

    fun checkVault(vault: Vault, checked: Boolean) {
        state.update {
            it.copy(checkedVaults = it.checkedVaults + (vault to checked) )
        }
    }

    fun createFolder() = viewModelScope.launch {
        val currntState = state.value
        if (currntState.errorText != null)
            return@launch
        val name = generateUniqueName(
            textFieldState.text.toString()
                .ifEmpty { context.getString(R.string.create_folder_placeholder) },
            currntState.folderNames
        )
        val folderId = folderRepository.insertFolder(name)
        vaultOrderRepository.updateList(
            folderId.toString(),
            currntState.checkedVaults.filterValues { it }.keys.map { it.id }
        )

        navigator.navigate(Destination.Back)
    }
}