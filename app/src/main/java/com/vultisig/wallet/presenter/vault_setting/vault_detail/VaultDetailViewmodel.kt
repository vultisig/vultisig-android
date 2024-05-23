package com.vultisig.wallet.presenter.vault_setting.vault_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination.VaultSettings.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class VaultDetailViewmodel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val vaultId: String =
        savedStateHandle.get<String>(ARG_VAULT_ID)!!

    val uiModel = MutableStateFlow(VaultDetailUiModel())
    fun loadData() {
        viewModelScope.launch {
            vaultRepository.get(vaultId)?.let { vault ->
                uiModel.update {
                    it.copy(
                        name = vault.name,
                        pubKeyECDSA = vault.pubKeyECDSA,
                        pubKeyEDDSA = vault.pubKeyEDDSA,
                        deviceList = vault.signers
                    )
                }
            }
        }
    }
}