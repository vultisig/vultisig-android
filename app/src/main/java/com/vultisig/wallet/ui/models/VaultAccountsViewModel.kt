package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class VaultAccountsUiModel(
    val vaultName: String = "",
    val showBackupWarning: Boolean = false,
    val isRefreshing: Boolean = false,
    val totalFiatValue: String? = null,
    val isBalanceValueVisible: Boolean = true,
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
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
) : ViewModel() {
    private var vaultId: String? = null

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    private var loadVaultNameJob: Job? = null
    private var loadAccountsJob: Job? = null

    fun loadData(vaultId: String) {
        this.vaultId = vaultId
        loadVaultNameAndShowBackup(vaultId)
        loadAccounts(vaultId)
        loadBalanceVisibility(vaultId)
    }

    private fun loadBalanceVisibility(vaultId: String) {
        viewModelScope.launch {
            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
            uiState.update {
                it.copy(isBalanceValueVisible = isBalanceVisible)
            }
        }
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

    fun joinKeysign() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(vaultId = vaultId))
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

    private fun loadVaultNameAndShowBackup(vaultId: String) {
        loadVaultNameJob?.cancel()
        loadVaultNameJob = viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: return@launch
            uiState.update { it.copy(vaultName = vault.name) }
            val isVaultBackedUp = vaultDataStoreRepository.readBackupStatus(vaultId).first()
            uiState.update { it.copy(showBackupWarning = !isVaultBackedUp) }
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
                .map {
                    it.sortedBy {
                        it.accounts.calculateAccountsTotalFiatValue()?.value?.unaryMinus()
                    }
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

    fun toggleBalanceVisibility() {
        val isBalanceValueVisible = !uiState.value.isBalanceValueVisible
        viewModelScope.launch {
            uiState.update {
                it.copy(isBalanceValueVisible = isBalanceValueVisible)
            }
            balanceVisibilityRepository.setVisibility(vaultId!!, isBalanceValueVisible)
        }
    }

    fun backupVault() {
        viewModelScope.launch {
            navigator.navigate(Destination.BackupPassword(vaultId!!))
        }
    }

}