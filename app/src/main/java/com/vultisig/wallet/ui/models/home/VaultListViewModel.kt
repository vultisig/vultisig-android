package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.DepositDeeplinkData
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
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FolderAndVaultsCount(val folder: Folder, val vaultsCount: Int)

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
internal class VaultListViewModel
@Inject
constructor(
    private val vaultOrderRepository: VaultOrderRepository,
    private val folderRepository: FolderRepository,
    private val folderOrderRepository: FolderOrderRepository,
    private val getOrderedVaults: GetOrderedVaults,
    private val navigator: Navigator<Destination>,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultRepository: VaultRepository,
    private val vaultAndBalanceUseCase: VaultAndBalanceUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val snackbarFlow: SnackbarFlow,
) : ViewModel() {

    val state = MutableStateFlow(VaultListUiModel())

    private var openType: Route.VaultList.OpenType? = null
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
        collectVaultsJob =
            viewModelScope.launch {
                getOrderedVaults(null).collect { orderedVaults ->
                    val vaultAndBalances = orderedVaults.map { vaultAndBalanceUseCase(it) }
                    state.update { it.copy(vaults = vaultAndBalances) }
                }
            }
    }

    private fun collectTotalVaultAndBalance() {
        viewModelScope.launch {
            vaultRepository.getAllAsFlow().collect { vaults ->
                val fiatValues = vaults.mapNotNull { vaultAndBalanceUseCase(it).balanceFiatValue }

                val totalBalance = fiatValues.reduceOrNull { acc, value -> acc + value }

                state.update {
                    it.copy(
                        totalVaultsCount = vaults.size,
                        totalBalance = totalBalance?.let { fiatValueToStringMapper(totalBalance) },
                    )
                }
            }
        }
    }

    private fun collectFolders() {
        collectFoldersJob?.cancel()
        collectFoldersJob =
            viewModelScope.launch {
                combine(folderOrderRepository.loadOrders(null), folderRepository.getAll()) {
                        orders,
                        folders ->
                        val addressAndOrderMap = mutableMapOf<FolderAndVaultsCount, Float>()
                        if (folders.isEmpty()) return@combine emptyList()
                        folders.forEach { eachFolder ->
                            val vaultCounts =
                                vaultOrderRepository.getChildrenCountFor(eachFolder.id.toString())
                            addressAndOrderMap[FolderAndVaultsCount(eachFolder, vaultCounts)] =
                                orders.find { it.value == eachFolder.id.toString() }?.order
                                    ?: folderOrderRepository.insert(null, eachFolder.id.toString())
                        }
                        addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
                    }
                    .collect { orderedFolders ->
                        state.update { it.copy(folders = orderedFolders) }
                    }
            }
    }

    fun onMoveFolders(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList =
            state.value.folders.toMutableList().apply { add(newOrder, removeAt(oldOrder)) }
        state.update { it.copy(folders = updatedPositionsList) }
        reIndexJob?.cancel()
        reIndexJob =
            viewModelScope.launch {
                delay(500)
                val midOrder = updatedPositionsList[newOrder].folder.id.toString()
                val upperOrder =
                    updatedPositionsList.getOrNull(newOrder + 1)?.folder?.id?.toString()
                val lowerOrder =
                    updatedPositionsList.getOrNull(newOrder - 1)?.folder?.id?.toString()
                folderOrderRepository.updateItemOrder(null, upperOrder, midOrder, lowerOrder)
            }
    }

    fun selectVault(vaultId: String) {
        viewModelScope.launch {
            val openType = openType ?: return@launch
            when (openType) {
                is Route.VaultList.OpenType.DeepLink -> {
                    val sendDeepLinkData =
                        (openType as Route.VaultList.OpenType.DeepLink).sendDeepLinkData
                    navigator.route(
                        Route.Send(
                            vaultId = vaultId,
                            chainId = sendDeepLinkData.assetChain,
                            tokenId =
                                "${sendDeepLinkData.assetTicker}-${sendDeepLinkData.assetChain}",
                            address = sendDeepLinkData.toAddress,
                            amount = sendDeepLinkData.amount,
                            memo = sendDeepLinkData.memo,
                        )
                    )
                }
                is Route.VaultList.OpenType.DepositDeepLink ->
                    openDepositDeepLink(vaultId, openType.depositDeepLinkData)
                is Route.VaultList.OpenType.Home -> {
                    lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
                    navigator.back()
                }
            }
        }
    }

    private suspend fun openDepositDeepLink(vaultId: String, data: DepositDeeplinkData) {
        val ticker = data.assetTicker
        val matches =
            vaultRepository.get(vaultId)?.coins.orEmpty().count { coin ->
                coin.chain.raw == data.chainId &&
                    if (ticker == null) coin.isNativeToken
                    else coin.ticker.equals(ticker, ignoreCase = true)
            }
        // A ticker two of the vault's coins share cannot say which one the link means.
        val error =
            when {
                matches == 0 ->
                    UiText.FormattedText(
                        R.string.must_be_enabled_before_proceeding,
                        listOf(ticker ?: data.chainId),
                    )
                matches > 1 -> UiText.StringResource(R.string.deeplink_not_supported)
                else -> null
            }
        if (error != null) {
            snackbarFlow.showMessage(error, SnackbarType.Error)
            return
        }
        navigator.route(
            Route.Deposit(
                vaultId = vaultId,
                chainId = data.chainId,
                depositType = DepositOption.Custom.name,
                prefillTicker = ticker,
                prefillAmount = data.amount,
                prefillMemo = data.memo,
            )
        )
    }

    fun addVault() {
        viewModelScope.launch { navigator.route(Route.AddVault) }
    }

    fun onMoveVaults(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList =
            state.value.vaults.toMutableList().apply { add(newOrder, removeAt(oldOrder)) }
        state.update { it.copy(vaults = updatedPositionsList) }
        reIndexJob?.cancel()
        reIndexJob =
            viewModelScope.launch {
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
            val vault = vaultRepository.get(vaultId) ?: return@launch
            state.update {
                it.copy(
                    currentVaultId = vaultId,
                    currentFolderId = vaultOrder?.parentId,
                    currentVaultName = vault.name,
                )
            }
        }
    }

    fun toggleRearrangeMode() {
        state.update { it.copy(isRearrangeMode = it.isRearrangeMode.not()) }
    }
}
