package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText.DynamicString
import com.vultisig.wallet.common.UiText.StringResource
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.on_board.db.VaultDB.Companion.FILE_POSTFIX
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.navigation.Screen.VaultDetail.VaultSettings.ARG_VAULT_ID
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.Backup
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.Delete
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.ErrorDownloadFile
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.SuccessBackup
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFile
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.NavigateToScreen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.ShowSnackBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
open class VaultSettingsViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(ARG_VAULT_ID)!!
    val vault: Vault? = vaultDB.select(vaultId)

    private val channel = Channel<VaultSettingsUiEvent>()
    val channelFlow = channel.receiveAsFlow()
    fun onEvent(event: VaultSettingsEvent) {
        when (event) {
            Backup -> backupVault()
            Delete -> deleteVault()
            ErrorDownloadFile -> errorBackUp()
            is SuccessBackup -> successBackup(event.fileName)
        }
    }

    private fun successBackup(fileName: String) {
        viewModelScope.launch {
            channel.send(ShowSnackBar(DynamicString(fileName + FILE_POSTFIX)))
        }
    }


    private fun errorBackUp() {
        viewModelScope.launch {
            channel.send(ShowSnackBar(StringResource(R.string.vault_settings_error_backup_file)))
        }
    }


    private fun backupVault() {
        viewModelScope.launch {
            vault?.let {
                channel.send(BackupFile(it.name))
            }
        }
    }

    private fun deleteVault() {
        viewModelScope.launch {
            vaultDB.delete(vaultId)
            channel.send(NavigateToScreen(Screen.Home.route))
        }
    }


}