package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.common.UiEvent
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultEditViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(Screen.VaultDetail.VaultSettings.ARG_VAULT_ID)!!
    val vault: Vault? = vaultDB.select(vaultId)

    val uiModel = MutableStateFlow(VaultEditUiModel())

    private val channel = Channel<UiEvent>()
    val channelFlow = channel.receiveAsFlow()
    fun loadData() {
        vault?.let {
            uiModel.update {
                it.copy(name = vault.name)
            }
        }
    }

    fun onEvent(event: VaultEditEvent) {
        when (event) {
            is VaultEditEvent.OnNameChange -> uiModel.update { it.copy(name = event.name) }
            VaultEditEvent.OnSave -> saveName()
        }
    }

    private fun saveName() {
        viewModelScope.launch {
            vault?.let {
                vaultDB.updateVaultName(it.name, it.copy(name = uiModel.value.name))
                channel.send(UiEvent.NavigateUp)
            }
        }
    }
}