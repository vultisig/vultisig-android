package com.vultisig.wallet.ui.models

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.ChainsOrderRepository
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class VaultAccountsUiModel(
    val vaultName: String = "",
    val isRefreshing: Boolean = false,
    val totalFiatValue: String? = null,
    val accounts: List<AccountUiModel> = emptyList(),
)

@Parcelize
internal data class AccountUiModel(
    val model: @RawValue Address,
    val chainName: String,
    @DrawableRes val logo: Int,
    val address: String,
    val nativeTokenAmount: String?,
    val fiatAmount: String?,
    val assetsSize: Int = 0,
):Parcelable

@HiltViewModel
internal class VaultAccountsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,

    private val addressToUiModelMapper: AddressToUiModelMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,

    private val vaultRepository: VaultRepository,
    private val accountsRepository: AccountsRepository,
    private val chainsOrderRepository: ChainsOrderRepository,
    ) : ViewModel() {
    private var vaultId: String? = null

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    private var loadVaultNameJob: Job? = null
    private var loadAccountsJob: Job? = null
    private var reIndexJob: Job? = null

    fun loadData(vaultId: String) {
        this.vaultId = vaultId
        loadVaultName(vaultId)
        loadAccounts(
            vaultId = vaultId,
            showRefreshing = false,
        )
    }

    fun refreshData() {
        loadAccounts(
            vaultId = requireNotNull(vaultId),
            showRefreshing = true,
        )
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
            val vault = requireNotNull(vaultRepository.get(vaultId))
            uiState.update { it.copy(vaultName = vault.name) }
        }
    }

    private fun loadAccounts(
        vaultId: String,
        showRefreshing: Boolean,
    ) {
        loadAccountsJob?.cancel()
        loadAccountsJob = viewModelScope.launch {
            if (showRefreshing) {
                uiState.update { it.copy(isRefreshing = true) }
            }
            accountsRepository
                .loadAddresses(vaultId)
                .onEach { addresses->
                    addresses.forEach{
                        val addressOrder = chainsOrderRepository.find(it.chain.raw)
                        if (addressOrder == null)
                            chainsOrderRepository.insert(it.chain.raw)
                    }
                }
                .zip(chainsOrderRepository.loadByOrders()) { addresses, chainOrders ->
                    chainOrders.map {orderEntity->
                         addresses.find { address -> address.chain.raw == orderEntity.value }!!
                    }
                }
                .flowOn(IO)
                .catch {
                    // TODO handle error
                    Timber.e(it)
                }.collect { accounts ->
                    if (showRefreshing) {
                        uiState.update { it.copy(isRefreshing = false) }
                    }

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
            chainsOrderRepository.updateItemOrder(upperOrder, midOrder, lowerOrder)
        }
    }

}