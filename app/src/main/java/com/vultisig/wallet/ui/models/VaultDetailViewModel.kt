package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.ChainAccountsRepository
import com.vultisig.wallet.ui.models.mappers.ChainAccountToChainAccountUiModelMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultDetailUiModel(
    val vaultName: String = "",
    val accounts: List<ChainAccountUiModel> = emptyList(),
)

internal data class ChainAccountUiModel(
    val chainName: String,
    @DrawableRes val logo: Int,
    val address: String,
    val nativeTokenAmount: String?,
    val fiatAmount: String?,
)

@HiltViewModel
internal class VaultDetailViewModel @Inject constructor(
    private val vaultDb: VaultDB,
    private val chainAccountsRepository: ChainAccountsRepository,
    private val chainAccountToUiModelMapper: ChainAccountToChainAccountUiModelMapper,
) : ViewModel() {

    val uiState = MutableStateFlow(VaultDetailUiModel())

    fun loadData(vaultId: String) {
        loadVaultName(vaultId)
        loadAccounts(vaultId)
    }

    private fun loadVaultName(vaultId: String) {
        viewModelScope.launch {
            val vault = requireNotNull(vaultDb.select(vaultId))
            uiState.update { it.copy(vaultName = vault.name) }
        }
    }

    private fun loadAccounts(vaultId: String) {
        viewModelScope.launch {
            chainAccountsRepository.loadChainAccounts(vaultId)
                .map { it.map(chainAccountToUiModelMapper::map) }
                .collect { accounts ->
                    uiState.update { it.copy(accounts = accounts) }
                }
        }
    }

}