package com.vultisig.wallet.ui.models.folder

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Folder
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.data.usecases.VaultAndBalance
import com.vultisig.wallet.data.usecases.VaultAndBalanceUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CreateFolderUiModel(
    val errorText: UiText? = null,
    val folderNames: List<String> = emptyList(),
    val checkedVaults: Map<VaultAndBalance, Boolean> = emptyMap(),

    val folder: Folder? = null,
    val vaults: List<VaultAndBalance> = emptyList(),
    val availableVaults: List<VaultAndBalance> = emptyList(),
    val error: UiText? = null,
) {
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
    private val vaultAndBalanceUseCase: VaultAndBalanceUseCase,
) : ViewModel() {
    val state = MutableStateFlow(CreateFolderUiModel())
    var folderId: String? = null
    var reIndexJob: Job? = null
    val textFieldState = TextFieldState()

    fun init(folderId: String?) {
        this.folderId = folderId
        textFieldState.clearText()
        collectFolderNames()
        getVaults()
        validateEachTextChange()

        if (folderId != null) {
            collectVaults(folderId)
            collectAvailableVaults()
            getFolder(folderId)
        }
    }

    private fun collectFolderNames() = viewModelScope.launch {
        folderRepository.getAll().collectLatest { folders ->
            state.update { it.copy(folderNames = folders.map { folder -> folder.name }) }
        }
    }

    private fun validateEachTextChange() = viewModelScope.launch {
        textFieldState.textAsFlow().collectLatest {
            if (it.isNotEmpty()) {
                validate()
            }
        }
    }

    private fun getVaults() = viewModelScope.launch {
        getOrderedVaults(null).map { vaults ->
            vaults.map { vault ->
                vaultAndBalanceUseCase(vault)
            }
        }.collectLatest { vaults ->
            state.update { it.copy(checkedVaults = vaults.associateWith { false }) }
        }
    }

    private fun validate() = viewModelScope.launch {
        val name = textFieldState.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        state.update { it.copy(errorText = errorMessage) }
    }

    fun checkVault(vault: VaultAndBalance, checked: Boolean) {
        state.update {
            it.copy(checkedVaults = it.checkedVaults + (vault to checked))
        }
    }


    fun commitChanges(completed: () -> Unit) {
        folderId?.let { changeFolderName(folderId = it, completed = completed) } ?: createFolder()
    }


    private fun changeFolderName(folderId: String, completed: () -> Unit = {}) {
        viewModelScope.launch {
            val name = textFieldState.text.toString()

            if (!isNameValid(name))
                return@launch

            if (name == state.value.folder?.name) {
                completed()
                return@launch
            }

            val uniqueName = generateUniqueName(
                name,
                state.value.folderNames
            )
            folderRepository.updateFolderName(folderId, uniqueName)
            completed()
        }

    }

    private fun isNameValid(name: String): Boolean {
        return isNameLengthValid(name)
    }


    private fun createFolder() = viewModelScope.launch {
        val currentState = state.value
        if (currentState.errorText != null)
            return@launch

        if(currentState.checkedVaults.filter { it.value }.isEmpty()){
            return@launch
        }

        val targetName = textFieldState.text.toString()
            .ifEmpty { context.getString(R.string.create_folder_placeholder) }
        val name = generateUniqueName(targetName, currentState.folderNames)
        val folderId = folderRepository.insertFolder(name)

        vaultOrderRepository.updateList(
            folderId.toString(),
            currentState.checkedVaults.filterValues { it }.keys.map { it.vault.id }
        )

        navigator.navigate(Destination.Back)
    }


    private fun collectVaults(folderId: String) = viewModelScope.launch {
        getOrderedVaults(folderId).collect { orderedVaults ->
            val vaultAndBalances = orderedVaults.map {
                vaultAndBalanceUseCase(it)
            }
            state.update {
                it.copy(
                    vaults = vaultAndBalances,
                )
            }
        }
    }

    private fun collectAvailableVaults() = viewModelScope.launch {
        getOrderedVaults(null).collect { availableVaults ->
            val availableVaults = availableVaults.map {
                vaultAndBalanceUseCase(it)
            }
            state.update { it.copy(availableVaults = availableVaults) }
        }
    }

    private fun getFolder(folderId: String) = viewModelScope.launch {
        val folder = folderRepository.getFolder(folderId)
        state.update { it.copy(folder = folder) }
        textFieldState.setTextAndPlaceCursorAtEnd(folder.name)
    }

    fun onMoveVaults(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList = state.value.vaults.toMutableList().apply {
            add(newOrder, removeAt(oldOrder))
        }
        state.update { it.copy(vaults = updatedPositionsList) }
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val midOrder = updatedPositionsList[newOrder].vault.id
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.vault?.id
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.vault?.id
            vaultOrderRepository.updateItemOrder(folderId, upperOrder, midOrder, lowerOrder)
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

    private fun canRemoveVaultFromFolder(check: Boolean): Boolean {
        return when {
            check -> true
            state.value.vaults.size > 1 -> true
            else -> false
        }
    }

    private fun writeCheckVaultChanges(check: Boolean, vaultId: String) = viewModelScope.launch {
        val folderId = if (check) folderId else null
        vaultOrderRepository.insert(folderId, vaultId)
    }
    private fun showEmptyFolderError() = viewModelScope.launch {
        state.update {
            it.copy(
                error = StringResource(R.string.error_folder_must_have_at_least_one_vault)
            )
        }
    }


    fun deleteFolder(onComplete: ()-> Unit) = viewModelScope.launch {
        vaultOrderRepository.removeParentId(folderId)
        folderRepository.deleteFolder(requireNotNull(folderId))
        onComplete()
    }

}