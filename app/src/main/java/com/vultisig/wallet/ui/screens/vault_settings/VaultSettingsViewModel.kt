package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.on_board.db.VaultDB.Companion.FILE_POSTFIX
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.Backup
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.ChangeCheckCaution
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.Delete
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.DismissConfirmDeleteScreen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.ErrorDownloadFile
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.ShowConfirmDeleteScreen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsEvent.SuccessBackup
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFailed
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFile
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
internal open class VaultSettingsViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _uiModel = MutableStateFlow(
        VaultSettingsState(
            cautionsBeforeDelete = listOf(
                R.string.vault_settings_delete_vault_caution1,
                R.string.vault_settings_delete_vault_caution2,
                R.string.vault_settings_delete_vault_caution3,
            )
        )
    )
    val uiModel = _uiModel.asStateFlow()

    private val vaultId: String =
        savedStateHandle.get<String>(Destination.VaultSettings.ARG_VAULT_ID)!!
    val vault: Vault? = vaultDB.select(vaultId)

    private val channel = Channel<VaultSettingsUiEvent>()
    val channelFlow = channel.receiveAsFlow()
    fun onEvent(event: VaultSettingsEvent) {
        when (event) {
            Backup -> backupVault()
            ShowConfirmDeleteScreen -> showConfirmDeleteDialog()
            DismissConfirmDeleteScreen -> dismissConfirmDeleteDialog()
            Delete -> delete()
            ErrorDownloadFile -> errorBackUp()
            is SuccessBackup -> successBackup(event.fileName)
            is ChangeCheckCaution -> changeCheckCaution(event.index, event.isChecked)
        }
    }

    private fun dismissConfirmDeleteDialog() {
        _uiModel.update {
            it.copy(showDeleteConfirmScreen = false)
        }
    }

    private fun changeCheckCaution(index: Int, checked: Boolean) {
        _uiModel.update {
            val checkedCautionIndexes = it.checkedCautionIndexes.toMutableList()
            if (checked) checkedCautionIndexes.add(index)
            else checkedCautionIndexes.remove(index)
            it.copy(
                checkedCautionIndexes = checkedCautionIndexes,
                isDeleteButtonEnabled = checkedCautionIndexes.size == it.cautionsBeforeDelete.size
            )
        }
    }

    private fun successBackup(fileName: String) {
        viewModelScope.launch {
            channel.send(BackupSuccess(fileName + FILE_POSTFIX))
        }
    }


    private fun errorBackUp() {
        viewModelScope.launch {
            channel.send(BackupFailed)
        }
    }


    private fun backupVault() {
        viewModelScope.launch {
            vault?.let {
                val thresholds = Utils.getThreshold(it.signers.count())
                val date = Date()
                val format = SimpleDateFormat("yyyy-MM")
                val formattedDate = format.format(date)
                val fileName =
                    "vultisig-${it.name}-$formattedDate-${thresholds + 1}of${it.signers.count()}-${
                        it.pubKeyECDSA.takeLast(4)
                    }-${it.localPartyID}.dat"
                channel.send(BackupFile(it.name, fileName))
            }
        }
    }

    private fun showConfirmDeleteDialog() {
        _uiModel.update {
            it.copy(showDeleteConfirmScreen = true, checkedCautionIndexes = emptyList())
        }
    }

    private fun delete() {
        viewModelScope.launch {
            vaultDB.delete(vaultId)
            navigator.navigate(Destination.Home)
        }
    }


}