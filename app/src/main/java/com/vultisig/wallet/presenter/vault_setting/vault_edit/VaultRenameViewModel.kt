package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText.StringResource
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.data.repositories.VaultRepository
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

internal data class VaultEditUiModel(
    val name: String = ""
)

@HiltViewModel
internal class VaultRenameViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val orderDB: OrderDB,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(ARG_VAULT_ID)!!

    private val vault = MutableStateFlow<Vault?>(null)

    val uiModel = MutableStateFlow(VaultEditUiModel())

    private val channel = Channel<VaultEditUiEvent>()
    val channelFlow = channel.receiveAsFlow()
    fun loadData() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: error("No vault with $vaultId id")
            this@VaultRenameViewModel.vault.value = vault
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
            vault.value?.let { vault ->
                val newName = uiModel.value.name
                val isNameAlreadyExist =
                    vaultRepository.getAll().any { it.name == newName }
                if (isNameAlreadyExist) {
                    channel.send(ShowSnackBar(StringResource(R.string.vault_edit_this_name_already_exist)))
                    return@launch
                }
                vaultRepository.setVaultName(vault.id, newName)
                orderDB.updateItemKey(vault.name, newName)
                navigator.navigate(Destination.Home)
            }
        }
    }
}