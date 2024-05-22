package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class HomeUiModel(
    val showVaultList: Boolean = false,
    val vaultName: String = "",
    val selectedVaultId: String? = null,
)

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,

    private val vaultDb: VaultDB,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
) : ViewModel() {

    val uiState = MutableStateFlow(HomeUiModel())

    init {
        collectLastOpenedVault()
    }

    fun openSettings() {
        viewModelScope.launch {
            navigator.navigate(Destination.Settings)
        }
    }

    fun edit() {
        val selectedVaultId = uiState.value.selectedVaultId ?: return

        viewModelScope.launch {
            navigator.navigate(Destination.VaultSettings(selectedVaultId))
        }
    }

    fun toggleVaults() {
        uiState.update { it.copy(showVaultList = !it.showVaultList) }
    }

    fun selectVault(vaultId: String) {
        viewModelScope.launch {
            uiState.update {
                it.copy(showVaultList = false)
            }
            lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
        }
    }

    private fun collectLastOpenedVault() {
        viewModelScope.launch {
            lastOpenedVaultRepository.lastOpenedVaultId
                .map { lastOpenedVaultId ->
                    lastOpenedVaultId?.let {
                        vaultDb.select(lastOpenedVaultId)
                    } ?: vaultDb.selectAll().firstOrNull()
                }.collect { vault ->
                    if (vault != null) {
                        uiState.update {
                            it.copy(
                                vaultName = vault.name,
                                selectedVaultId = vault.id,
                            )
                        }
                    }
                }
        }
    }

}