package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.on_board.db.VaultDB.Companion.FILE_POSTFIX
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
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
    private val orderDB: OrderDB,
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


    fun dismissConfirmDeleteDialog() {
        _uiModel.update {
            it.copy(showDeleteConfirmScreen = false)
        }
    }

    fun changeCheckCaution(index: Int, checked: Boolean) {
        val checkedCautionIndexes = _uiModel.value.checkedCautionIndexes.toMutableList()
        if (checked) checkedCautionIndexes.add(index)
        else checkedCautionIndexes.remove(index)
        _uiModel.update {
            it.copy(
                checkedCautionIndexes = checkedCautionIndexes,
                isDeleteButtonEnabled = checkedCautionIndexes.size == it.cautionsBeforeDelete.size
            )
        }
    }

    fun successBackup(fileName: String) {
        viewModelScope.launch {
            channel.send(BackupSuccess(fileName + FILE_POSTFIX))
        }
    }


    fun errorBackUp() {
        viewModelScope.launch {
            channel.send(BackupFailed)
        }
    }


    fun backupVault() {
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

    fun showConfirmDeleteDialog() {
        _uiModel.update {
            it.copy(showDeleteConfirmScreen = true, checkedCautionIndexes = emptyList())
        }
    }

    fun delete() {
        viewModelScope.launch {
            vaultDB.delete(vaultId)
            orderDB.removeOrder(vault?.name?:"")
            navigator.navigate(Destination.Home)
        }
    }


}