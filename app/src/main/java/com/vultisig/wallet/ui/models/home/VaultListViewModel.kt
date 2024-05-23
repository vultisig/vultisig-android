package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.VaultLocationsRepository
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.components.reorderable.utils.ItemPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class VaultListViewModel @Inject constructor(
    private val vaultLocationsRepository: VaultLocationsRepository,
) : ViewModel() {
    val vaults = MutableStateFlow<List<Vault>>(emptyList())
    private var reIndexJob: Job? = null

    init {
        viewModelScope.launch {
            vaults.value = vaultLocationsRepository.getVaultsForHomeLocation()
        }
    }

    fun onMove(from: ItemPosition, to: ItemPosition) {
        val updatedPositionsList = vaults.value.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vaults.value =  updatedPositionsList
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val indexedVaultList =
                vaultLocationsRepository.updateVaultOrderInHomeLocation(updatedPositionsList)
            vaults.value = indexedVaultList
        }
    }
}