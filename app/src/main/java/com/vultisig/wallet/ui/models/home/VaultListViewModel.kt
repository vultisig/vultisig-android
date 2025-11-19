package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Folder
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.FolderRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.order.FolderOrderRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.data.usecases.VaultAndBalance
import com.vultisig.wallet.data.usecases.VaultAndBalanceUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FolderAndVaultsCount(
    val folder: Folder,
    val vaultsCount: Int,
)


internal data class VaultListUiModel(
    val vaults: List<VaultAndBalance> = emptyList(),
    val folders: List<FolderAndVaultsCount> = emptyList(),
    val isRearrangeMode: Boolean = false,
    val currentVaultId: VaultId = "",
    val currentFolderId: String? = null,
    val currentVaultName: String? = null,
    val totalVaultsCount: Int = 0,
    val totalBalance: String? = null,
)

@HiltViewModel
internal class VaultListViewModel @Inject constructor(
    private val vaultOrderRepository: VaultOrderRepository,
    private val folderRepository: FolderRepository,
    private val folderOrderRepository: FolderOrderRepository,
    private val getOrderedVaults: GetOrderedVaults,
    private val navigator: Navigator<Destination>,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultRepository: VaultRepository,
    private val vaultAndBalanceUseCase: VaultAndBalanceUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : ViewModel() {

    val state = MutableStateFlow(VaultListUiModel())

    lateinit var openType: Route.VaultList.OpenType
    private var vaultId: VaultId? = null
    private var reIndexJob: Job? = null
    private var collectVaultsJob: Job? = null
    private var collectFoldersJob: Job? = null

    fun init(type: Route.VaultList.OpenType) {
        this.openType = type
        collectEachVaultAndBalance()
        collectTotalVaultAndBalance()
        if (type is Route.VaultList.OpenType.Home) {
            this.vaultId = type.vaultId
            collectFolders()
            collectCurrentVaultAndFolder(type.vaultId)
        }
    }

    private fun collectEachVaultAndBalance() {
        collectVaultsJob?.cancel()
        collectVaultsJob = viewModelScope.launch {
            getOrderedVaults(null)
                .collect { orderedVaults ->
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
    }

    private fun collectTotalVaultAndBalance() {

        viewModelScope.launch {
           val vaults = vaultRepository.getAll()
            val fiatValues = vaults.mapNotNull {
                vaultAndBalanceUseCase(it).balanceFiatValue
            }

            val totalBalance = fiatValues.reduceOrNull { acc, value -> acc + value }

            state.update {
                it.copy(
                    totalVaultsCount = vaults.size,
                    totalBalance = totalBalance?.let { fiatValueToStringMapper(totalBalance) }
                )
            }
        }
    }

    private fun collectFolders() {
        collectFoldersJob?.cancel()
        collectFoldersJob = viewModelScope.launch {
            combine(
                folderOrderRepository.loadOrders(null),
                folderRepository.getAll()
            ) { orders, folders ->
                val addressAndOrderMap = mutableMapOf<FolderAndVaultsCount, Float>()
                if(folders.isEmpty())
                    return@combine emptyList()
                folders.forEach { eachFolder ->
                    val vaultCounts = vaultOrderRepository.getChildrenCountFor(eachFolder.id.toString())
                    addressAndOrderMap[FolderAndVaultsCount(eachFolder, vaultCounts)] =
                        orders.find { it.value == eachFolder.id.toString() }?.order
                            ?: folderOrderRepository.insert(null, eachFolder.id.toString())
                }
                addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
            }.collect { orderedFolders ->
                state.update { it.copy(folders = orderedFolders) }
            }
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
            val midOrder = updatedPositionsList[newOrder].folder.id.toString()
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.folder?.id?.toString()
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.folder?.id?.toString()
            folderOrderRepository.updateItemOrder(null, upperOrder, midOrder, lowerOrder)
        }
    }


    fun selectVault(vaultId: String) {
        viewModelScope.launch {
            when (openType) {
                is Route.VaultList.OpenType.DeepLink -> {
                    val sendDeepLinkData = (openType as Route.VaultList.OpenType.DeepLink).sendDeepLinkData
                    navigator.route(
                        Route.Send(
                            vaultId = vaultId,
                            chainId = sendDeepLinkData.assetChain,
                            tokenId = sendDeepLinkData.assetTicker,
                            address = sendDeepLinkData.toAddress,
                            amount = sendDeepLinkData.amount,
                            memo = sendDeepLinkData.memo,
                        )
                    )
                }
                is Route.VaultList.OpenType.Home -> {
                    lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
                    navigator.back()
                }
            }
        }
    }

    fun addVault() {
        viewModelScope.launch {
            navigator.route(Route.AddVault)
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
            val midOrder = updatedPositionsList[newOrder].vault.id
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.vault?.id
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.vault?.id
            vaultOrderRepository.updateItemOrder(null, upperOrder, midOrder, lowerOrder)
        }
    }


    private fun collectCurrentVaultAndFolder(vaultId: VaultId) {
        viewModelScope.launch {
            val vaultOrder = vaultOrderRepository.find(name = vaultId)
            val vault = requireNotNull(vaultRepository.get(vaultId))
            state.update {
                it.copy(
                    currentVaultId = vaultId,
                    currentFolderId = vaultOrder?.parentId,
                    currentVaultName = vault.name
                )
            }
        }
    }

    fun toggleRearrangeMode() {
        state.update {
            it.copy(
                isRearrangeMode = it.isRearrangeMode.not()
            )
        }
    }

}