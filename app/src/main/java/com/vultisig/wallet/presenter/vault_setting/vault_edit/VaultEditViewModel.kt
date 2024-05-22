package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText.StringResource
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditUiEvent.ShowSnackBar
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.VaultSettings.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class VaultEditViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    private val orderDB: OrderDB,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(ARG_VAULT_ID)!!
    val vault: Vault? = vaultDB.select(vaultId)

    val uiModel = MutableStateFlow(VaultEditUiModel())

    private val channel = Channel<VaultEditUiEvent>()
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
            vault?.let { vault ->
                val newName = uiModel.value.name
                val isNameAlreadyExist =
                    vaultDB.selectAll().map { v -> v.name }.any { it == newName }
                if (isNameAlreadyExist) {
                    channel.send(ShowSnackBar(StringResource(R.string.vault_edit_this_name_already_exist)))
                    return@launch
                }
                vaultDB.updateVaultName(vault.name, vault.copy(name = newName))
                orderDB.updateItemKey(vault.name, newName)
                navigator.navigate(Destination.Home)
            }
        }
    }
}