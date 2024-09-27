package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Vault
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
    private val vaultOrderRepository: VaultOrderRepository
) : ViewModel() {
    val vaults = MutableStateFlow<List<Vault>>(emptyList())
    private var reIndexJob: Job? = null

    init {
        viewModelScope.launch {
            vaultOrderRepository.loadOrders(null).map { orders ->
                val vaults = vaultRepository.getAll()
                val addressAndOrderMap = mutableMapOf<Vault, Float>()
                vaults.forEach { eachVault ->
                    addressAndOrderMap[eachVault] =
                        orders.find { it.value == eachVault.id }?.order
                            ?: vaultOrderRepository.insert(null,eachVault.id)
                }
                addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
            }.collect { orderedVaults ->
                vaults.value = orderedVaults
            }
        }
    }

    fun onMove(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList = vaults.value.toMutableList().apply {
            add(newOrder, removeAt(oldOrder))
        }
        vaults.value =  updatedPositionsList
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