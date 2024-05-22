package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
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
    val chainName: String,
    @DrawableRes val logo: Int,
    val address: String,
    val nativeTokenAmount: String?,
    val fiatAmount: String?,
    val assetsSize: Int = 0,
)

@HiltViewModel
internal class VaultAccountsViewModel @Inject constructor(
    private val vaultDb: VaultDB,
    private val accountsRepository: AccountsRepository,
    private val addressToUiModelMapper: AddressToUiModelMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : ViewModel() {
    var vault: Vault? by mutableStateOf(null)

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    fun loadData(vaultId: String) {
        loadVaultName(vaultId)
        loadAccounts(vaultId)
    }

    fun refreshData() {
        viewModelScope.launch {
            uiState.update { it.copy(isRefreshing = true) }
            // TODO: add refresh logic here
            delay(3000)
            uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadVaultName(vaultId: String) {
        viewModelScope.launch {
            val vault = requireNotNull(vaultDb.select(vaultId))
            this@VaultAccountsViewModel.vault = vault
            uiState.update { it.copy(vaultName = vault.name) }
        }
    }

    private fun loadAccounts(vaultId: String) {
        viewModelScope.launch {
            accountsRepository
                .loadAddresses(vaultId)
                .catch {
                    // TODO handle error
                    Timber.e(it)
                }.collect { accounts ->
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

}