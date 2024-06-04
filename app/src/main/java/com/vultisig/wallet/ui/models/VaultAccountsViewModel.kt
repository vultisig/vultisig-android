package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.OrderRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class VaultAccountsUiModel(
    val vaultName: String = "",
    val isRefreshing: Boolean = false,
    val totalFiatValue: String? = null,
    val accounts: List<AccountUiModel> = emptyList(),
)

internal data class AccountUiModel(
    val model: Address,
    val chainName: String,
    @DrawableRes val logo: Int,
    val address: String,
    val nativeTokenAmount: String?,
    val fiatAmount: String?,
    val assetsSize: Int = 0,
)

@HiltViewModel
internal class VaultAccountsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,

    private val addressToUiModelMapper: AddressToUiModelMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,

    private val vaultRepository: VaultRepository,
    private val accountsRepository: AccountsRepository,
    private val chainsOrderRepository: OrderRepository<ChainOrderEntity>,
) : ViewModel() {
    private var vaultId: String? = null

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    private var loadVaultNameJob: Job? = null
    private var loadAccountsJob: Job? = null
    private var reIndexJob: Job? = null

    fun loadData(vaultId: String) {
        this.vaultId = vaultId
        loadVaultName(vaultId)
        loadAccounts(vaultId)
    }

    fun refreshData() {
        val vaultId = vaultId ?: return
        updateRefreshing(true)
        loadAccounts(vaultId)
    }

    fun send() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.navigate(Destination.Send(vaultId = vaultId))
        }
    }

    fun swap() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.navigate(Destination.Swap(vaultId = vaultId))
        }
    }

    fun openAccount(account: AccountUiModel) {
        val vaultId = vaultId ?: return
        val chainId = account.model.chain.id

        viewModelScope.launch {
            navigator.navigate(
                Destination.ChainTokens(
                    vaultId = vaultId,
                    chainId = chainId,
                )
            )
        }
    }

    private fun loadVaultName(vaultId: String) {
        loadVaultNameJob?.cancel()
        loadVaultNameJob = viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: return@launch
            uiState.update { it.copy(vaultName = vault.name) }
        }
    }

    fun closeLoadAccountJob() {
        loadAccountsJob?.cancel()
    }

    private fun loadAccounts(vaultId: String) {
        loadAccountsJob?.cancel()
        loadAccountsJob = viewModelScope.launch {
            accountsRepository
                .loadAddresses(vaultId)
                .combine(chainsOrderRepository.loadOrders(vaultId)) { addresses, chainOrders ->
                    val addressAndOrderMap = mutableMapOf<Address, Float>()
                    addresses.forEach { eachAddress ->
                        addressAndOrderMap[eachAddress] =
                            chainOrders.find { it.value == eachAddress.chain.raw }?.order
                                ?: chainsOrderRepository.insert(vaultId, eachAddress.chain.raw)
                    }
                    addressAndOrderMap.entries.sortedByDescending { it.value }.map { it.key }
                }
                .catch {
                    updateRefreshing(false)

                    // TODO handle error
                    Timber.e(it)
                }.collect { accounts ->
                    updateRefreshing(false)

                    val totalFiatValue = accounts.calculateAddressesTotalFiatValue()
                        ?.let(fiatValueToStringMapper::map)
                    val accountsUiModel = accounts.map(addressToUiModelMapper::map)

                    uiState.update {
                        it.copy(
                            totalFiatValue = totalFiatValue, accounts = accountsUiModel
                        )
                    }
                }
        }
    }

    private fun updateRefreshing(isRefreshing: Boolean) {
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }


    fun onMove(oldOrder: Int, newOrder: Int) {
        val updatedPositionsList = uiState.value.accounts.toMutableList().apply {
            add(newOrder, removeAt(oldOrder))
        }
        uiState.update {
            it.copy(
                accounts = updatedPositionsList
            )
        }
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch(IO) {
            delay(500)
            val midOrder = updatedPositionsList[newOrder].chainName
            val upperOrder = updatedPositionsList.getOrNull(newOrder + 1)?.chainName
            val lowerOrder = updatedPositionsList.getOrNull(newOrder - 1)?.chainName
            chainsOrderRepository.updateItemOrder(vaultId, upperOrder, midOrder, lowerOrder)
        }
    }

}