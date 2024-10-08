package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.db.models.FolderEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.repositories.order.FolderOrderRepository
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultListUiModel(
    val vaults: List<Vault> = emptyList(),
    val folders: List<FolderEntity> = emptyList()
)

@HiltViewModel
internal class VaultListViewModel @Inject constructor(
    private val vaultOrderRepository: VaultOrderRepository,
    private val folderRepository: FolderRepository,
    private val folderOrderRepository: FolderOrderRepository,
    private val getOrderedVaults: GetOrderedVaults,
) : ViewModel() {
    val state = MutableStateFlow(VaultListUiModel())
    private var reIndexJob: Job? = null

    init {
        collectFolders()
        collectVaults()
    }

    private fun collectVaults() = viewModelScope.launch {
        getOrderedVaults(null).collect { orderedVaults ->
            state.update { it.copy(vaults = orderedVaults) }
        }
    }

    private fun collectFolders() = viewModelScope.launch {
        combine(
            folderOrderRepository.loadOrders(null),
            folderRepository.getAll()
        ) { orders, folders ->
            val addressAndOrderMap = mutableMapOf<FolderEntity, Float>()
            folders.forEach { eachFolder ->
                addressAndOrderMap[eachFolder] =
                    orders.find { it.value == eachFolder.id.toString() }?.order
                        ?: folderOrderRepository.insert(null,eachFolder.id.toString())
            }
            addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
        }.collect { orderedFolders ->
            state.update { it.copy(folders = orderedFolders) }
        }
    }

    fun onMoveFolders(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList = state.value.folders.toMutableList().apply {
            add(newOrder, removeAt(oldOrder))
        }
        state.update { it.copy(folders = updatedPositionsList) }
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val midOrder = updatedPositionsList[newOrder].id.toString()
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.id.toString()
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.id.toString()
            folderOrderRepository.updateItemOrder(null,upperOrder, midOrder, lowerOrder)
        }
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
            vaultOrderRepository.updateItemOrder(null,upperOrder, midOrder, lowerOrder)
        }
    }
}