package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.VaultListEntity
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class VaultListViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {
    val listItems = MutableStateFlow<List<VaultListEntity>>(emptyList())
    private var reIndexJob: Job? = null

    init {
        viewModelScope.launch {
            vaultOrderRepository.loadOrders(null).map { orders ->
                val addressAndOrderMap = mutableMapOf<VaultListEntity, Float>()
                vaultRepository.getAll().forEach { eachVault ->
                    addressAndOrderMap[VaultListEntity.VaultListItem(eachVault)] =
                        orders.find { it.value == eachVault.id }?.order
                            ?: vaultOrderRepository.insert(null,eachVault.id)
                }
                folderRepository.getAll().forEach { eachFolder ->
                    val folderListItem = VaultListEntity.FolderListItem(eachFolder)
                    addressAndOrderMap[VaultListEntity.FolderListItem(eachFolder)] =
                        orders.find { it.value == folderListItem.id }?.order
                            ?: vaultOrderRepository.insert(null, folderListItem.id)
                }
                addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
            }.collect { orderedVaults ->
                listItems.value = orderedVaults
            }
        }
    }

    fun onMove(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList = listItems.value.toMutableList().apply {
            add(newOrder, removeAt(oldOrder))
        }
        listItems.value =  updatedPositionsList
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val midOrder = updatedPositionsList[newOrder].id
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.id
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.id
            vaultOrderRepository.updateItemOrder(null,upperOrder, midOrder, lowerOrder)
        }
    }

    fun onCreateNewFolder() = viewModelScope.launch {
        folderRepository.insertFolder("New Folder")
    }
}