package com.vultisig.wallet.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.common.UiEvent
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.navigation.Screen.VaultDetail.VaultSettings.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
open class VaultSettingViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(ARG_VAULT_ID)!!
    val vault: Vault? = vaultDB.select(vaultId)

    private val channel = Channel<UiEvent>()
    val channelFlow = channel.receiveAsFlow()
    fun deleteVault() {
        viewModelScope.launch {
            vaultDB.delete(vaultId)
            channel.send(UiEvent.NavigateToScreen(Screen.Home.route))
        }
    }
}