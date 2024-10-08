package com.vultisig.wallet.ui.models.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.db.models.FolderEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FolderState(
    val folder: FolderEntity? = null,
    val isEditMode: Boolean = false,
    val vaults: List<Vault> = emptyList(),
    val availableVaults: List<Vault> = emptyList(),
)

@HiltViewModel
internal class  FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val folderRepository: FolderRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val getOrderedVaults: GetOrderedVaults,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
): ViewModel() {
    private val folderId: String =
        requireNotNull(savedStateHandle[Destination.Folder.ARG_FOLDER_ID])
    val state = MutableStateFlow(FolderState())

    private var reIndexJob: Job? = null

    init {
        getFolder()
        collectVaults()
        collectAvailableVaults()
    }

    private fun collectVaults() = viewModelScope.launch {
        getOrderedVaults(folderId).collect { orderedVaults ->
            state.update { it.copy(vaults = orderedVaults) }
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
    }

    fun selectVault(vaultId: String) = viewModelScope.launch {
        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
        navigator.navigate(Destination.Back)
    }

    fun deleteFolder() = viewModelScope.launch {
        vaultOrderRepository.updateList(folderId)
        folderRepository.deleteFolder(folderId)
        navigator.navigate(Destination.Back)
    }

    fun onBack() = viewModelScope.launch {
        navigator.navigate(Destination.Back)
    }

    fun onEdit() = viewModelScope.launch {
        state.update { it.copy(isEditMode = !it.isEditMode) }
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

    fun checkVault(check: Boolean, vaultId: String) = viewModelScope.launch {
        val folderId = if (check) folderId else null
        vaultOrderRepository.insert(folderId, vaultId)
    }
}