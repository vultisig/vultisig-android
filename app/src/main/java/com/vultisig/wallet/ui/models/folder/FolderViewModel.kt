package com.vultisig.wallet.ui.models.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Folder
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.data.usecases.VaultAndBalance
import com.vultisig.wallet.data.usecases.VaultAndBalanceUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FolderUiModel(
    val folder: Folder? = null,
    val vaults: List<VaultAndBalance> = emptyList(),
    val totalBalance: String? = null,
)

@HiltViewModel
internal class FolderViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val folderRepository: FolderRepository,
    private val vaultOrderRepository: VaultOrderRepository,
    private val getOrderedVaults: GetOrderedVaults,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultAndBalanceUseCase: VaultAndBalanceUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : ViewModel() {
    private lateinit var folderId: String

    val state = MutableStateFlow(FolderUiModel())

    private var reIndexJob: Job? = null

    fun init(folderId: String) {
        this.folderId = folderId
        getFolder()
        collectVaults()
    }

    private fun collectVaults() = viewModelScope.launch {
        getOrderedVaults(folderId).collect { orderedVaults ->
            val vaultAndBalances = orderedVaults.map {
                vaultAndBalanceUseCase(it)
            }

            val totalBalance = vaultAndBalances
                .mapNotNull { it.balanceFiatValue }
                .reduceOrNull { acc, balance -> acc + balance }
            state.update {
                it.copy(
                    vaults = vaultAndBalances,
                    totalBalance = totalBalance?.let { fiatValueToStringMapper(totalBalance) }
                )
            }
        }
    }

    private fun getFolder() = viewModelScope.launch {
        val folder = folderRepository.getFolder(folderId)
        state.update { it.copy(folder = folder) }
    }


    fun selectVault(vaultId: String) = viewModelScope.launch {
        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
        navigator.navigate(
            Destination.Home(vaultId),
            opts = NavigationOptions(clearBackStack = true)
        )
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

}