package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
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
    val isChainRearrangeMode: Boolean = false,
    val isVaultRearrangeMode: Boolean = false
)

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,

    private val navigator: Navigator<Destination>,

    private val vaultRepository: VaultRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
) : ViewModel() {

    private var requestedVaultId: String? = savedStateHandle.remove(Destination.ARG_VAULT_ID)
    private var showVaultList: Boolean =
        savedStateHandle.remove(Destination.Home.ARG_SHOW_VAULT_LIST)!!
    val uiState = MutableStateFlow(HomeUiModel(showVaultList = showVaultList))
    init {
        collectLastOpenedVault()
    }

    fun openSettings() {
        val selectedVaultId = uiState.value.selectedVaultId
        selectedVaultId?.let { vaultId ->
            viewModelScope.launch {
                navigator.navigate(Destination.Settings(vaultId = vaultId))
            }
        }
    }

    fun edit() {
        if (uiState.value.showVaultList) {
            toggleVaultRearrangeMode()
        } else toggleChainRearrangeMode()
    }


    private fun toggleChainRearrangeMode() {
        uiState.update { it.copy(isChainRearrangeMode = !it.isChainRearrangeMode) }
    }

    private fun toggleVaultRearrangeMode() {
        uiState.update { it.copy(isVaultRearrangeMode = !it.isVaultRearrangeMode) }
    }

    fun toggleVaults() {
        uiState.update { it.copy(showVaultList = !it.showVaultList) }
    }

    fun selectVault(vaultId: String) {
        viewModelScope.launch {
            hideVaultList()
            lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)
        }
    }

    fun addVault() {
        viewModelScope.launch {
            hideVaultList()
            navigator.navigate(Destination.SelectVaultType)
        }
    }

    fun importVault() {
        viewModelScope.launch {
            hideVaultList()
            navigator.navigate(Destination.ImportVault)
        }
    }

    fun shareVaultQr(){
        viewModelScope.launch {
            uiState.value.selectedVaultId?.let { vaultId ->
                navigator.navigate(Destination.ShareVaultQr(vaultId))
            }
        }
    }

    val isEditMode: Boolean
        get() {
            val state = uiState.value
            return (!state.showVaultList && state.isChainRearrangeMode)
                    || (state.showVaultList && state.isVaultRearrangeMode)
        }

    private fun hideVaultList() {
        uiState.update { it.copy(showVaultList = false) }
    }

    private fun collectLastOpenedVault() {
        viewModelScope.launch {
            val requestedVaultId = requestedVaultId
            if (requestedVaultId != null) {
                lastOpenedVaultRepository.setLastOpenedVaultId(requestedVaultId)
                this@HomeViewModel.requestedVaultId = null
            }

            lastOpenedVaultRepository.lastOpenedVaultId
                .map { lastOpenedVaultId ->
                    lastOpenedVaultId?.let {
                        vaultRepository.get(it)
                    } ?: vaultRepository.getAll().firstOrNull()
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