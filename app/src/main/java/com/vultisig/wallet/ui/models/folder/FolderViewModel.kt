package com.vultisig.wallet.ui.models.folder

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Folder
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FolderUiModel(
    val folder: Folder? = null,
    val isEditMode: Boolean = false,
    val vaults: List<Vault> = emptyList(),
    val availableVaults: List<Vault> = emptyList(),
    val folderNames: List<String> = emptyList(),
    val error: UiText? = null,
    val nameError: UiText? = null,
)

@HiltViewModel
internal class  FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val folderRepository: FolderRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val getOrderedVaults: GetOrderedVaults,
    private val isNameLengthValid: IsVaultNameValid,
    private val generateUniqueName: GenerateUniqueName,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
): ViewModel() {
    private val folderId: String =
        requireNotNull(savedStateHandle[Destination.Folder.ARG_FOLDER_ID])
    val state = MutableStateFlow(FolderUiModel())
    val nameFieldState = TextFieldState()

    private var reIndexJob: Job? = null

    init {
        getFolder()
        collectVaults()
        collectAvailableVaults()
        validateEachTextChange()
        collectFolderNames()
    }

    private fun collectVaults() = viewModelScope.launch {
        getOrderedVaults(folderId).collect { orderedVaults ->
            state.update { it.copy(vaults = orderedVaults) }
        }
    }

    private fun collectFolderNames() = viewModelScope.launch {
        folderRepository.getAll().collectLatest { folders ->
            state.update { it.copy(folderNames = folders.map { folder -> folder.name }) }
        }
    }

    private fun collectAvailableVaults() = viewModelScope.launch {
        getOrderedVaults(null).collect { availableVaults ->
            state.update { it.copy(availableVaults = availableVaults) }
        }
    }

    private fun getFolder() = viewModelScope.launch {
        val folder = folderRepository.getFolder(folderId)
        state.update { it.copy(folder = folder) }
        nameFieldState.edit { insert(0, folder.name) }
    }

    private fun validateEachTextChange() = viewModelScope.launch {
        nameFieldState.textAsFlow().collectLatest {
            validate()
        }
    }

    private fun validate() = viewModelScope.launch {
        val name = nameFieldState.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        state.update { it.copy(nameError = errorMessage) }
    }

    fun selectVault(vaultId: String) = viewModelScope.launch {
        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
        navigator.navigate(Destination.Home(vaultId),
            opts = NavigationOptions(clearBackStack = true)
        )
    }

    fun deleteFolder() = viewModelScope.launch {
        vaultOrderRepository.removeParentId(folderId)
        folderRepository.deleteFolder(folderId)
        navigator.navigate(Destination.Back)
    }

    fun back() = viewModelScope.launch {
        navigator.navigate(Destination.Back)
    }

    fun edit() = viewModelScope.launch {
        if (state.value.isEditMode) {
            changeFolderName()
        } else {
            state.update { it.copy(isEditMode = !it.isEditMode) }
        }
    }

    private suspend fun changeFolderName() {
        if (state.value.nameError != null)
            return

        val name = nameFieldState.text.toString()

        if (name.isEmpty() || name == state.value.folder?.name) {
            state.update { it.copy(isEditMode = false) }
            return
        }
        val uniqueName = generateUniqueName(
            name,
            state.value.folderNames
        )
        folderRepository.updateFolderName(folderId, uniqueName)
        state.update { it.copy(
            isEditMode = false,
            folder = it.folder?.copy(name = uniqueName),
        ) }
    }

    fun onMoveVaults(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList = state.value.vaults.toMutableList().apply {
            add(newOrder, removeAt(oldOrder))
        }
        state.update { it.copy(vaults = updatedPositionsList) }
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val midOrder = updatedPositionsList[newOrder].id
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.id
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.id
            vaultOrderRepository.updateItemOrder(folderId,upperOrder, midOrder, lowerOrder)
        }
    }

    fun tryToCheckVault(check: Boolean, vaultId: String): Boolean {
        if (canRemoveVaultFromFolder(check)) {
            writeCheckVaultChanges(check, vaultId)
            return check
        } else {
            showEmptyFolderError()
            return !check
        }
    }

    private fun writeCheckVaultChanges(check: Boolean, vaultId: String) = viewModelScope.launch {
        val folderId = if (check) folderId else null
        vaultOrderRepository.insert(folderId, vaultId)
    }

    private fun canRemoveVaultFromFolder(check: Boolean): Boolean {
        return when {
            check -> true
            state.value.vaults.size > 1 -> true
            else -> false
        }
    }

    private fun showEmptyFolderError() = viewModelScope.launch {
        state.update {
            it.copy(
                error = UiText.StringResource(R.string.error_folder_must_have_at_least_one_vault)
            )
        }
    }

    fun hideError() = viewModelScope.launch {
        state.update { it.copy(error = null) }
    }
}